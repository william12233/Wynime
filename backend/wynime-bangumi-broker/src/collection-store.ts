import type {
  CollectionSyncMutation,
  CollectionSyncRecord,
  CollectionSyncRequest,
  CollectionSyncResponse,
  CollectionStoreMarkSyncedResponse,
  CollectionStoreSyncResponse,
  CollectionStorageProbeResponse,
} from "./protocol";

interface StoredField {
  value: unknown;
  modifiedAt: number;
  serverReceivedAt: number;
  deviceId: string;
  mutationId: string;
}

interface StoredRecord {
  kind: "subject" | "episode";
  subjectId: number;
  episodeId?: number;
  fields: Record<string, StoredField>;
  revision: number;
}

interface StoredMutationResult {
  revision: number;
  changed: boolean;
  upstreamSynced: boolean;
}

interface StoredMeta {
  cursor: number;
  pendingCount: number;
}

interface StoredLogEntry {
  recordKey: string;
  revision: number;
}

type CollectionStoreCommand =
  | { op: "sync"; request: CollectionSyncRequest }
  | { op: "markUpstreamSynced"; mutationIds: string[] }
  | { op: "storageProbe" };

const META_KEY = "meta";
const RECORD_PREFIX = "record:";
const LOG_PREFIX = "log:";
const MUTATION_PREFIX = "mutation:";
const PENDING_PREFIX = "pending:";
const PENDING_ORDER_PREFIX = "pending-order:";
const STORAGE_BATCH_SIZE = 128;
// A web-confirmed removal can use two Bangumi fetches. Leave headroom under the free Worker subrequest limit.
const MAX_UPSTREAM_BATCH = 20;
const VALID_FIELDS = new Set([
  "collectionType",
  "rate",
  "comment",
  "tags",
  "private",
  "episodeStatus",
  "volumeStatus",
]);

export class CollectionStore {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    if (request.method !== "POST") return json({ ok: false, error: "method_not_allowed" }, 405);

    try {
      const command = (await request.json()) as CollectionStoreCommand;
      const result = command.op === "sync"
        ? await this.sync(command.request)
        : command.op === "markUpstreamSynced"
          ? await this.markUpstreamSynced(command.mutationIds)
          : await this.storageProbe();
      return json({ ok: true, result });
    } catch {
      return json({ ok: false, error: "collection_store_request_failed" }, 400);
    }
  }

  private async sync(request: CollectionSyncRequest): Promise<CollectionStoreSyncResponse> {
    const now = Date.now();
    return this.state.storage.transaction(async (storage) => {
      const meta = await this.readMeta(storage);
      const initialMeta = { ...meta };
      const accepted: CollectionSyncResponse["accepted"] = [];
      const authoritativeKeys = new Set<string>();
      const updatedRecords = new Map<string, StoredRecord>();

      for (const mutation of request.changes) {
        const key = recordKey(mutation.kind, mutation.subjectId, mutation.episodeId);
        const mutationKey = MUTATION_PREFIX + mutation.mutationId;
        let result = await storage.get<StoredMutationResult>(mutationKey);
        if (!result) {
          const normalized = normalizeMutationClock(mutation, now);
          result = await this.applyMutation(storage, normalized, now, meta, updatedRecords);
          await storage.put(mutationKey, result);
        }
        accepted.push({ mutationId: mutation.mutationId, revision: result.revision, changed: result.changed });
        if (!result.changed) authoritativeKeys.add(key);
      }

      if (meta.cursor !== initialMeta.cursor || meta.pendingCount !== initialMeta.pendingCount) {
        await storage.put(META_KEY, meta);
      }

      const records = await this.readRecords(storage, request, initialMeta.cursor, updatedRecords, authoritativeKeys);
      const pendingChanges = request.mode === "reconcile" && meta.pendingCount > 0
        ? await this.readPendingBatch(storage)
        : [];
      return {
        cursor: meta.cursor,
        records,
        accepted,
        upstreamStatus: meta.pendingCount > 0 ? "pending" : "not_requested",
        pendingCount: meta.pendingCount,
        pendingChanges,
      };
    });
  }

  private async applyMutation(
    storage: DurableObjectTransaction,
    mutation: CollectionSyncMutation,
    now: number,
    meta: StoredMeta,
    updatedRecords: Map<string, StoredRecord>,
  ): Promise<StoredMutationResult> {
    validateMutation(mutation);
    const key = recordKey(mutation.kind, mutation.subjectId, mutation.episodeId);
    const current = updatedRecords.get(key) ?? await storage.get<StoredRecord>(key) ?? {
      kind: mutation.kind,
      subjectId: mutation.subjectId,
      ...(mutation.episodeId == null ? {} : { episodeId: mutation.episodeId }),
      fields: {},
      revision: 0,
    } satisfies StoredRecord;
    const fields = { ...current.fields };
    const replacedMutationIds = new Set<string>();
    const changedFields: string[] = [];

    for (const field of mutation.changedFields) {
      const next: StoredField = {
        value: mutationFieldValue(mutation, field),
        modifiedAt: mutation.fieldModifiedAt?.[field] ?? mutation.modifiedAt,
        serverReceivedAt: now,
        deviceId: mutation.deviceId,
        mutationId: mutation.mutationId,
      };
      const previous = fields[field];
      if (!previous || isNewer(next, previous)) {
        if (previous) replacedMutationIds.add(previous.mutationId);
        fields[field] = next;
        changedFields.push(field);
      }
    }

    if (changedFields.length === 0) {
      return { revision: current.revision, changed: false, upstreamSynced: true };
    }

    meta.cursor += 1;
    const nextRecord: StoredRecord = { ...current, fields, revision: meta.cursor };
    updatedRecords.set(key, nextRecord);
    await storage.put(key, nextRecord);
    await storage.put(logKey(meta.cursor), { recordKey: key, revision: meta.cursor } satisfies StoredLogEntry);

    for (const mutationId of replacedMutationIds) {
      const pending = await storage.get<CollectionSyncMutation>(PENDING_PREFIX + mutationId);
      if (!pending) continue;
      const remaining = activePendingFields(pending, nextRecord);
      if (remaining.length === 0) {
        await storage.delete([PENDING_PREFIX + mutationId, pendingOrderKey(pending)]);
        meta.pendingCount -= 1;
      } else if (remaining.length !== pending.changedFields.length) {
        await storage.put(PENDING_PREFIX + mutationId, withPendingFields(pending, remaining));
      }
    }

    const pending = withPendingFields(mutation, changedFields);
    await storage.put(PENDING_PREFIX + mutation.mutationId, pending);
    await storage.put(pendingOrderKey(pending), mutation.mutationId);
    meta.pendingCount += 1;
    return { revision: meta.cursor, changed: true, upstreamSynced: false };
  }

  private async markUpstreamSynced(mutationIds: string[]): Promise<CollectionStoreMarkSyncedResponse> {
    return this.state.storage.transaction(async (storage) => {
      const meta = await this.readMeta(storage);
      const initialCount = meta.pendingCount;
      for (const mutationId of new Set(mutationIds)) {
        if (!mutationId) continue;
        const mutationKey = MUTATION_PREFIX + mutationId;
        const result = await storage.get<StoredMutationResult>(mutationKey);
        if (result && !result.upstreamSynced) {
          await storage.put(mutationKey, { ...result, upstreamSynced: true });
        }
        const pending = await storage.get<CollectionSyncMutation>(PENDING_PREFIX + mutationId);
        if (pending) {
          await storage.delete([PENDING_PREFIX + mutationId, pendingOrderKey(pending)]);
          meta.pendingCount -= 1;
        }
      }
      if (meta.pendingCount !== initialCount) await storage.put(META_KEY, meta);
      return { pendingCount: meta.pendingCount };
    });
  }

  private async readPendingBatch(storage: DurableObjectTransaction): Promise<CollectionSyncMutation[]> {
    const index = await storage.list<string>({ prefix: PENDING_ORDER_PREFIX, limit: MAX_UPSTREAM_BATCH });
    const keys = [...index.values()].map((id) => PENDING_PREFIX + id);
    const pending = await getBatch<CollectionSyncMutation>(storage, keys);
    return keys.flatMap((key) => {
      const mutation = pending.get(key);
      return mutation ? [mutation] : [];
    });
  }

  private async readRecords(
    storage: DurableObjectTransaction,
    request: CollectionSyncRequest,
    previousCursor: number,
    updatedRecords: Map<string, StoredRecord>,
    authoritativeKeys: Set<string>,
  ): Promise<CollectionSyncRecord[]> {
    const subjectIds = request.subjectIds ? new Set(request.subjectIds) : null;
    const shouldInclude = (record: StoredRecord) =>
      subjectIds ? subjectIds.has(record.subjectId) : record.kind === "subject" || request.includeEpisodes;

    const records = new Map<string, StoredRecord>();
    if (request.fullSnapshot || request.cursor <= 0) {
      if (subjectIds) {
        const subjectKeys = [...subjectIds].map((id) => recordKey("subject", id));
        for (const [key, record] of await getBatch<StoredRecord>(storage, subjectKeys)) {
          records.set(key, record);
        }
        for (const id of subjectIds) {
          const prefixes = [recordKey("subject", id) + ":", recordKey("episode", id) + ":"];
          for (const prefix of prefixes) {
            for (const [key, record] of await storage.list<StoredRecord>({ prefix })) {
              records.set(key, record);
            }
          }
        }
      } else {
        const prefix = request.includeEpisodes ? RECORD_PREFIX : RECORD_PREFIX + "subject:";
        for (const [key, record] of await storage.list<StoredRecord>({ prefix })) {
          records.set(key, record);
        }
      }
    } else if (!subjectIds || subjectIds.size > 0) {
      if (request.cursor < previousCursor) {
        const logs = await storage.list<StoredLogEntry>({
          prefix: LOG_PREFIX,
          startAfter: logKey(request.cursor),
          end: logKey(previousCursor + 1),
        });
        for (const entry of logs.values()) authoritativeKeys.add(entry.recordKey);
      }
      for (const [key, record] of updatedRecords) {
        if (record.revision > request.cursor) records.set(key, record);
      }

      const missingKeys = [...authoritativeKeys].filter((key) => !records.has(key) && !updatedRecords.has(key));
      for (const [key, record] of await getBatch<StoredRecord>(storage, missingKeys)) records.set(key, record);
      for (const key of authoritativeKeys) {
        const updated = updatedRecords.get(key);
        if (updated) records.set(key, updated);
      }
    }

    return [...records.values()].filter(shouldInclude).map(toPublicRecord).sort((left, right) => {
      if (left.revision !== right.revision) return left.revision - right.revision;
      if (left.subjectId !== right.subjectId) return left.subjectId - right.subjectId;
      return (left.episodeId ?? 0) - (right.episodeId ?? 0);
    });
  }

  private async readMeta(storage: DurableObjectTransaction): Promise<StoredMeta> {
    const stored = await storage.get<{ cursor: number; pendingCount?: number }>(META_KEY);
    if (stored && stored.pendingCount !== undefined) {
      return { cursor: stored.cursor, pendingCount: stored.pendingCount };
    }

    // Existing objects have pending payloads keyed by mutation ID.
    const meta: StoredMeta = { cursor: stored?.cursor ?? 0, pendingCount: 0 };
    let startAfter: string | undefined;
    for (;;) {
      const page = await storage.list<CollectionSyncMutation>({
        prefix: PENDING_PREFIX,
        startAfter,
        limit: STORAGE_BATCH_SIZE,
      });
      const recordKeys = [...new Set([...page.values()].map((mutation) =>
        recordKey(mutation.kind, mutation.subjectId, mutation.episodeId),
      ))];
      const records = await getBatch<StoredRecord>(storage, recordKeys);
      for (const [key, mutation] of page) {
        startAfter = key;
        const record = records.get(recordKey(mutation.kind, mutation.subjectId, mutation.episodeId));
        const remaining = activePendingFields(mutation, record);
        if (remaining.length === 0) {
          await storage.delete(key);
          continue;
        }
        if (remaining.length !== mutation.changedFields.length) {
          await storage.put(key, withPendingFields(mutation, remaining));
        }
        await storage.put(pendingOrderKey(mutation), mutation.mutationId);
        meta.pendingCount += 1;
      }
      if (page.size < STORAGE_BATCH_SIZE) break;
    }
    await storage.put(META_KEY, meta);
    return meta;
  }

  private async storageProbe(): Promise<CollectionStorageProbeResponse> {
    const startedAt = Date.now();
    const key = "probe:" + crypto.randomUUID();
    const value = { createdAt: startedAt };
    await this.state.storage.put(key, value);
    const stored = await this.state.storage.get<typeof value>(key);
    if (!stored || stored.createdAt !== startedAt) throw new Error("storage_probe_mismatch");
    await this.state.storage.delete(key);
    return { ok: true, storageLatencyMs: Math.max(0, Date.now() - startedAt) };
  }
}

async function getBatch<T>(
  storage: DurableObjectTransaction,
  keys: string[],
): Promise<Map<string, T>> {
  const values = new Map<string, T>();
  for (let offset = 0; offset < keys.length; offset += STORAGE_BATCH_SIZE) {
    const batch = await storage.get<T>(keys.slice(offset, offset + STORAGE_BATCH_SIZE));
    for (const [key, value] of batch) values.set(key, value);
  }
  return values;
}

function activePendingFields(mutation: CollectionSyncMutation, record?: StoredRecord): string[] {
  return mutation.changedFields.filter((field) => record?.fields[field]?.mutationId === mutation.mutationId);
}

function withPendingFields(mutation: CollectionSyncMutation, changedFields: string[]): CollectionSyncMutation {
  if (changedFields.length === mutation.changedFields.length) return mutation;
  return {
    ...mutation,
    changedFields,
    fieldModifiedAt: mutation.fieldModifiedAt
      ? Object.fromEntries(Object.entries(mutation.fieldModifiedAt).filter(([field]) => changedFields.includes(field)))
      : undefined,
  };
}

function pendingOrderKey(mutation: CollectionSyncMutation): string {
  return PENDING_ORDER_PREFIX + String(mutation.modifiedAt).padStart(16, "0") + ":" + mutation.mutationId;
}

function validateMutation(mutation: CollectionSyncMutation) {
  if (!mutation.mutationId || mutation.mutationId.length > 256) throw new Error("invalid_mutation_id");
  if (!mutation.deviceId || mutation.deviceId.length > 256) throw new Error("invalid_device_id");
  if (!Number.isSafeInteger(mutation.subjectId) || mutation.subjectId <= 0) throw new Error("invalid_subject_id");
  if (mutation.kind === "episode" && (!Number.isSafeInteger(mutation.episodeId) || mutation.episodeId! <= 0)) {
    throw new Error("invalid_episode_id");
  }
  if (!Number.isSafeInteger(mutation.modifiedAt) || mutation.modifiedAt < 0) throw new Error("invalid_modified_at");
  if (mutation.fieldModifiedAt && Object.entries(mutation.fieldModifiedAt).some(([field, modifiedAt]) =>
    !VALID_FIELDS.has(field) || !mutation.changedFields.includes(field) ||
    !Number.isSafeInteger(modifiedAt) || modifiedAt < 0,
  )) throw new Error("invalid_field_modified_at");
  if (mutation.changedFields.length === 0 || mutation.changedFields.some((field) => !VALID_FIELDS.has(field))) {
    throw new Error("invalid_changed_fields");
  }
  if (mutation.kind === "episode" && mutation.changedFields.some((field) => field !== "collectionType")) {
    throw new Error("invalid_episode_fields");
  }
}

function normalizeMutationClock(mutation: CollectionSyncMutation, now: number): CollectionSyncMutation {
  const maximumAcceptedTime = now + MAX_FUTURE_CLOCK_SKEW_MS;
  return {
    ...mutation,
    modifiedAt: Math.min(mutation.modifiedAt, maximumAcceptedTime),
    fieldModifiedAt: mutation.fieldModifiedAt
      ? Object.fromEntries(
        Object.entries(mutation.fieldModifiedAt).map(([field, modifiedAt]) => [
          field,
          Math.min(modifiedAt, maximumAcceptedTime),
        ]),
      )
      : mutation.fieldModifiedAt,
  };
}

function mutationFieldValue(mutation: CollectionSyncMutation, field: string): unknown {
  switch (field) {
    case "collectionType": return mutation.collectionType;
    case "rate": return mutation.rate;
    case "comment": return mutation.comment;
    case "tags": return mutation.tags;
    case "private": return mutation.private;
    case "episodeStatus": return mutation.episodeStatus;
    case "volumeStatus": return mutation.volumeStatus;
    default: throw new Error("unsupported_collection_field");
  }
}

function isNewer(next: StoredField, previous: StoredField): boolean {
  if (next.modifiedAt !== previous.modifiedAt) return next.modifiedAt > previous.modifiedAt;
  if (next.serverReceivedAt !== previous.serverReceivedAt) return next.serverReceivedAt > previous.serverReceivedAt;
  if (next.deviceId !== previous.deviceId) return next.deviceId > previous.deviceId;
  return next.mutationId > previous.mutationId;
}

function recordKey(kind: "subject" | "episode", subjectId: number, episodeId?: number): string {
  return RECORD_PREFIX + kind + ":" + subjectId + (episodeId == null ? "" : ":" + episodeId);
}

function logKey(revision: number): string {
  return LOG_PREFIX + String(revision).padStart(16, "0");
}

function toPublicRecord(record: StoredRecord): CollectionSyncRecord {
  const fields = record.fields;
  const value = (name: string) => fields[name]?.value as any;
  return {
    kind: record.kind,
    subjectId: record.subjectId,
    ...(record.episodeId == null ? {} : { episodeId: record.episodeId }),
    collectionType: value("collectionType"),
    rate: value("rate"),
    comment: value("comment"),
    tags: value("tags"),
    private: value("private"),
    episodeStatus: value("episodeStatus"),
    volumeStatus: value("volumeStatus"),
    deleted: value("collectionType") === "NOT_COLLECTED",
    fieldModifiedAt: Object.fromEntries(
      Object.entries(fields).map(([name, field]) => [name, field.modifiedAt]),
    ),
    revision: record.revision,
  };
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

const MAX_FUTURE_CLOCK_SKEW_MS = 5 * 60 * 1000;

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
    const initialCursor = request.cursor;
    const meta = await this.readMeta();
    const accepted: CollectionSyncResponse["accepted"] = [];
    const authoritativeKeys = new Set<string>();

    for (const mutation of request.changes) {
      const mutationKey = MUTATION_PREFIX + mutation.mutationId;
      const previous = await this.state.storage.get<StoredMutationResult>(mutationKey);
      if (previous) {
        accepted.push({
          mutationId: mutation.mutationId,
          revision: previous.revision,
          changed: previous.changed,
        });
        if (!previous.changed) authoritativeKeys.add(recordKey(mutation.kind, mutation.subjectId, mutation.episodeId));
        continue;
      }

      const normalized = normalizeMutationClock(mutation, now);
      const result = await this.applyMutation(normalized, now, meta);
      await this.state.storage.put(mutationKey, result);
      accepted.push({ mutationId: mutation.mutationId, revision: result.revision, changed: result.changed });
      if (!result.changed) authoritativeKeys.add(recordKey(normalized.kind, normalized.subjectId, normalized.episodeId));
      if (result.changed) {
        await this.state.storage.put(PENDING_PREFIX + mutation.mutationId, normalized);
      }
    }

    await this.state.storage.put(META_KEY, meta);
    const records = await this.readRecords(request, initialCursor, authoritativeKeys);
    const pendingChanges = await this.readPendingChanges();

    return {
      cursor: meta.cursor,
      records,
      accepted,
      upstreamStatus: pendingChanges.length > 0 ? "pending" : "not_requested",
      pendingCount: pendingChanges.length,
      pendingChanges: pendingChanges.slice(0, 200),
    };
  }

  private async applyMutation(
    mutation: CollectionSyncMutation,
    now: number,
    meta: StoredMeta,
  ): Promise<StoredMutationResult> {
    validateMutation(mutation);
    const key = recordKey(mutation.kind, mutation.subjectId, mutation.episodeId);
    const current = await this.state.storage.get<StoredRecord>(key) ?? {
      kind: mutation.kind,
      subjectId: mutation.subjectId,
      ...(mutation.episodeId == null ? {} : { episodeId: mutation.episodeId }),
      fields: {},
      revision: 0,
    } satisfies StoredRecord;
    const fields = { ...current.fields };
    let changed = false;

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
        fields[field] = next;
        changed = true;
      }
    }

    if (!changed) {
      return { revision: current.revision, changed: false, upstreamSynced: true };
    }

    meta.cursor += 1;
    const nextRecord: StoredRecord = {
      ...current,
      fields,
      revision: meta.cursor,
    };
    await this.state.storage.put(key, nextRecord);
    await this.state.storage.put(logKey(meta.cursor), {
      recordKey: key,
      revision: meta.cursor,
    } satisfies StoredLogEntry);
    return { revision: meta.cursor, changed: true, upstreamSynced: false };
  }

  private async markUpstreamSynced(mutationIds: string[]): Promise<CollectionStoreMarkSyncedResponse> {
    for (const mutationId of mutationIds) {
      if (!mutationId) continue;
      const result = await this.state.storage.get<StoredMutationResult>(MUTATION_PREFIX + mutationId);
      if (result) {
        await this.state.storage.put(MUTATION_PREFIX + mutationId, {
          ...result,
          upstreamSynced: true,
        } satisfies StoredMutationResult);
      }
      await this.state.storage.delete(PENDING_PREFIX + mutationId);
    }
    const pending = await this.readPendingChanges();
    return { pendingCount: pending.length };
  }

  private async readPendingChanges(): Promise<CollectionSyncMutation[]> {
    const pending = await this.state.storage.list<CollectionSyncMutation>({ prefix: PENDING_PREFIX });
    const active: CollectionSyncMutation[] = [];
    for (const [key, mutation] of pending) {
      const record = await this.state.storage.get<StoredRecord>(
        recordKey(mutation.kind, mutation.subjectId, mutation.episodeId),
      );
      const changedFields = mutation.changedFields.filter((field) =>
        record?.fields[field]?.mutationId === mutation.mutationId,
      );
      if (changedFields.length === 0) {
        await this.state.storage.delete(key);
        continue;
      }
      if (changedFields.length !== mutation.changedFields.length) {
        const trimmed = {
          ...mutation,
          changedFields,
          fieldModifiedAt: mutation.fieldModifiedAt
            ? Object.fromEntries(changedFields.map((field) => [field, mutation.fieldModifiedAt![field]]))
            : undefined,
        } satisfies CollectionSyncMutation;
        await this.state.storage.put(key, trimmed);
        active.push(trimmed);
      } else {
        active.push(mutation);
      }
    }
    return active.sort((left, right) => {
      if (left.modifiedAt !== right.modifiedAt) return left.modifiedAt - right.modifiedAt;
      return left.mutationId.localeCompare(right.mutationId);
    });
  }

  private async readRecords(
    request: CollectionSyncRequest,
    cursor: number,
    authoritativeKeys: Set<string> = new Set(),
  ): Promise<CollectionSyncRecord[]> {
    const keys = new Set<string>();
    const subjectIds = request.subjectIds ? new Set(request.subjectIds) : null;
    const shouldInclude = (record: StoredRecord) => {
      if (subjectIds && !subjectIds.has(record.subjectId)) return false;
      if (record.kind === "episode" && !request.includeEpisodes && !subjectIds) return false;
      return true;
    };

    if (request.fullSnapshot || cursor <= 0) {
      const records = await this.state.storage.list<StoredRecord>({ prefix: RECORD_PREFIX });
      for (const [key, record] of records) {
        if (shouldInclude(record)) keys.add(key);
      }
    } else {
      const logs = await this.state.storage.list<StoredLogEntry>({ prefix: LOG_PREFIX });
      for (const entry of logs.values()) {
        if (entry.revision <= cursor) continue;
        const record = await this.state.storage.get<StoredRecord>(entry.recordKey);
        if (record && shouldInclude(record)) keys.add(entry.recordKey);
      }
    }

    const records: CollectionSyncRecord[] = [];
    for (const key of authoritativeKeys) {
      const record = await this.state.storage.get<StoredRecord>(key);
      if (record && shouldInclude(record)) keys.add(key);
    }
    for (const key of keys) {
      const record = await this.state.storage.get<StoredRecord>(key);
      if (record) records.push(toPublicRecord(record));
    }
    return records.sort((left, right) => {
      if (left.revision !== right.revision) return left.revision - right.revision;
      if (left.subjectId !== right.subjectId) return left.subjectId - right.subjectId;
      return (left.episodeId ?? 0) - (right.episodeId ?? 0);
    });
  }

  private async readMeta(): Promise<StoredMeta> {
    return (await this.state.storage.get<StoredMeta>(META_KEY)) ?? { cursor: 0 };
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

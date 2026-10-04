import type {
  Env,
  OAuthStateRecord,
  OAuthTicketRecord,
  PlaybackChange,
  PlaybackServerChange,
  PlaybackSyncRequest,
  PlaybackSyncResponse,
  SessionRecord,
} from "./protocol";
import { isPlaybackChangeNewer } from "./playback-policy";

interface StoredPlaybackRecord extends PlaybackServerChange {
  deviceId: string;
  cursor: number;
}

type RpcCommand =
  | {
      op: "oauthStart";
      stateHash: string;
      record: OAuthStateRecord;
    }
  | {
      op: "oauthComplete";
      stateHash: string;
      ticketHash?: string;
      ticket?: OAuthTicketRecord;
      error?: string;
    }
  | {
      op: "oauthResult";
      stateHash: string;
      now: number;
    }
  | {
      op: "oauthState";
      stateHash: string;
      now: number;
    }
  | {
      op: "consumeTicket";
      ticketHash: string;
      now: number;
    }
  | {
      op: "putSession";
      sessionHash: string;
      record: SessionRecord;
    }
  | {
      op: "replaceSession";
      oldSessionHash: string;
      newSessionHash: string;
      record: SessionRecord;
    }
  | {
      op: "getSession";
      sessionHash: string;
      now: number;
    }
  | {
      op: "deleteSession";
      sessionHash: string;
    }
  | {
      op: "playbackSync";
      userKey: string;
      request: PlaybackSyncRequest;
      now: number;
    };

export class ReplayMarker {
  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env,
  ) {}

  async fetch(request: Request): Promise<Response> {
    if (request.method !== "POST") {
      return json({ ok: false, error: "method_not_allowed" }, 405);
    }

    try {
      const command = (await request.json()) as RpcCommand;
      const result = await this.execute(command);
      return json({ ok: true, result });
    } catch {
      return json({ ok: false, error: "durable_object_request_failed" }, 400);
    }
  }

  private async execute(command: RpcCommand): Promise<unknown> {
    switch (command.op) {
      case "oauthStart":
        await this.state.storage.put(stateKey(command.stateHash), command.record);
        return null;
      case "oauthComplete":
        return this.completeOAuth(command);
      case "oauthResult":
        return this.getOAuthResult(command.stateHash, command.now);
      case "oauthState":
        return this.getOAuthState(command.stateHash, command.now);
      case "consumeTicket":
        return this.consumeTicket(command.ticketHash, command.now);
      case "putSession":
        await this.state.storage.put(sessionKey(command.sessionHash), command.record);
        return null;
      case "replaceSession":
        await this.state.storage.delete(sessionKey(command.oldSessionHash));
        await this.state.storage.put(sessionKey(command.newSessionHash), command.record);
        return null;
      case "getSession":
        return this.getSession(command.sessionHash, command.now);
      case "deleteSession":
        await this.state.storage.delete(sessionKey(command.sessionHash));
        return null;
      case "playbackSync":
        return this.syncPlayback(command.userKey, command.request, command.now);
    }
  }

  private async completeOAuth(command: Extract<RpcCommand, { op: "oauthComplete" }>) {
    const key = stateKey(command.stateHash);
    const state = await this.state.storage.get<OAuthStateRecord>(key);
    if (!state || state.expiresAt <= Date.now() || state.status !== "pending") {
      return { accepted: false };
    }

    if (command.error) {
      await this.state.storage.put(key, {
        ...state,
        status: "error",
        error: command.error,
      } satisfies OAuthStateRecord);
      return { accepted: true };
    }

    if (!command.ticketHash || !command.ticket) {
      throw new Error("completed OAuth state requires a ticket");
    }
    await this.state.storage.put(ticketKey(command.ticketHash), command.ticket);
    await this.state.storage.put(key, {
      ...state,
      status: "complete",
      ticketHash: command.ticketHash,
    } satisfies OAuthStateRecord);
    return { accepted: true };
  }

  private async getOAuthResult(stateHash: string, now: number) {
    const key = stateKey(stateHash);
    const state = await this.state.storage.get<OAuthStateRecord>(key);
    if (!state || state.expiresAt <= now) {
      await this.state.storage.delete(key);
      return { status: "missing" as const };
    }
    if (state.status === "pending") return { status: "pending" as const };
    if (state.status === "error") {
      return { status: "error" as const, error: state.error ?? "oauth_failed" };
    }
    const ticket = state.ticketHash
      ? await this.state.storage.get<OAuthTicketRecord>(ticketKey(state.ticketHash))
      : null;
    if (!ticket || ticket.expiresAt <= now) {
      return { status: "exchanged" as const };
    }
    return {
      status: "complete" as const,
      payloadCiphertext: ticket.payloadCiphertext,
    };
  }

  private async getOAuthState(stateHash: string, now: number) {
    const key = stateKey(stateHash);
    const state = await this.state.storage.get<OAuthStateRecord>(key);
    if (!state || state.expiresAt <= now) {
      await this.state.storage.delete(key);
      return null;
    }
    return state;
  }

  private async consumeTicket(ticketHash: string, now: number) {
    const key = ticketKey(ticketHash);
    const ticket = await this.state.storage.get<OAuthTicketRecord>(key);
    if (!ticket || ticket.expiresAt <= now) {
      await this.state.storage.delete(key);
      return null;
    }
    await this.state.storage.delete(key);
    return ticket.payloadCiphertext;
  }

  private async getSession(sessionHash: string, now: number) {
    const key = sessionKey(sessionHash);
    const session = await this.state.storage.get<SessionRecord>(key);
    if (!session || session.expiresAt <= now) {
      await this.state.storage.delete(key);
      return null;
    }
    return session;
  }

  private async syncPlayback(
    userKey: string,
    request: PlaybackSyncRequest,
    now: number,
  ): Promise<PlaybackSyncResponse> {
    const user = storageKeyPart(userKey);
    const cursorKey = "playback-cursor:" + user;
    const initialCursor = Math.max(0, Math.floor(request.cursor));
    let cursor = (await this.state.storage.get<number>(cursorKey)) ?? 0;
    const accepted: PlaybackSyncResponse["accepted"] = [];

    for (const change of request.changes) {
      const recordKey = playbackRecordKey(user, change.subjectId, change.episodeId);
      const current = await this.state.storage.get<StoredPlaybackRecord>(recordKey);
      if (
        !current ||
        change.baseRevision === current.revision ||
        isPlaybackChangeNewer(change, current, request.deviceId)
      ) {
        cursor += 1;
        const next: StoredPlaybackRecord = {
          subjectId: change.subjectId,
          episodeId: change.episodeId,
          positionMs: change.deleted ? 0 : change.positionMs,
          durationMs: change.deleted ? 0 : change.durationMs,
          completed: change.deleted ? false : change.completed,
          lastPlayedAt: change.lastPlayedAt,
          revision: (current?.revision ?? 0) + 1,
          deleted: Boolean(change.deleted),
          deviceId: request.deviceId,
          cursor,
        };
        await this.state.storage.put(recordKey, next);
        accepted.push({
          subjectId: next.subjectId,
          episodeId: next.episodeId,
          revision: next.revision,
        });
      }
    }

    await this.state.storage.put(cursorKey, cursor);
    await this.pruneCompletedRecords(user);

    const serverChanges = new Map<string, PlaybackServerChange>();
    const records = await this.state.storage.list<StoredPlaybackRecord>({
      prefix: "playback-record:" + user + ":",
    });
    for (const record of records.values()) {
      if (record.cursor > initialCursor) {
        const change = toServerChange(record);
        serverChanges.set(changeIdentity(change), change);
      }
    }

    for (const change of request.changes) {
      const current = await this.state.storage.get<StoredPlaybackRecord>(
        playbackRecordKey(user, change.subjectId, change.episodeId),
      );
      if (current && change.baseRevision !== current.revision) {
        const serverChange = toServerChange(current);
        serverChanges.set(changeIdentity(serverChange), serverChange);
      }
    }

    return {
      cursor,
      serverChanges: [...serverChanges.values()].sort((left, right) => left.revision - right.revision),
      accepted,
    };
  }

  private async pruneCompletedRecords(user: string) {
    const records = await this.state.storage.list<StoredPlaybackRecord>({
      prefix: "playback-record:" + user + ":",
    });
    const completed = [...records.entries()]
      .filter(([, record]) => record.completed && !record.deleted)
      .sort(([, left], [, right]) => left.lastPlayedAt - right.lastPlayedAt);
    const excess = completed.length - MAX_COMPLETED_RECORDS;
    if (excess <= 0) return;
    await this.state.storage.delete(completed.slice(0, excess).map(([key]) => key));
  }
}

function toServerChange(record: StoredPlaybackRecord): PlaybackServerChange {
  return {
    subjectId: record.subjectId,
    episodeId: record.episodeId,
    positionMs: record.positionMs,
    durationMs: record.durationMs,
    completed: record.completed,
    lastPlayedAt: record.lastPlayedAt,
    revision: record.revision,
    deleted: record.deleted,
  };
}

function changeIdentity(change: PlaybackServerChange): string {
  return String(change.subjectId) + ":" + String(change.episodeId) + ":" + String(change.revision);
}

function storageKeyPart(value: string): string {
  return encodeURIComponent(value);
}

function stateKey(hash: string): string {
  return "oauth-state:" + hash;
}

function ticketKey(hash: string): string {
  return "oauth-ticket:" + hash;
}

function sessionKey(hash: string): string {
  return "session:" + hash;
}

function playbackRecordKey(user: string, subjectId: number, episodeId: number): string {
  return "playback-record:" + user + ":" + String(subjectId) + ":" + String(episodeId);
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

const MAX_COMPLETED_RECORDS = 10_000;

import { afterEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { hashToken } from "../src/crypto";
import { ReplayMarker } from "../src/replay-markers";
import type { Env, PlaybackSyncRequest } from "../src/protocol";

class MemoryStorage {
  readonly values = new Map<string, unknown>();

  async get<T>(key: string): Promise<T | undefined> {
    return this.values.get(key) as T | undefined;
  }

  async put<T>(key: string, value: T): Promise<void> {
    this.values.set(key, value);
  }

  async delete(key: string | string[]): Promise<boolean> {
    if (Array.isArray(key)) {
      let deleted = false;
      for (const item of key) deleted = this.values.delete(item) || deleted;
      return deleted;
    }
    return this.values.delete(key);
  }

  async list<T>(options?: { prefix?: string }): Promise<Map<string, T>> {
    const prefix = options?.prefix || "";
    return new Map(
      [...this.values.entries()]
        .filter(([key]) => key.startsWith(prefix))
        .map(([key, value]) => [key, value as T]),
    );
  }
}

function createMarker(storage = new MemoryStorage()): { marker: ReplayMarker; storage: MemoryStorage } {
  const marker = new ReplayMarker(
    { storage } as any,
    {} as Env,
  );
  return { marker, storage };
}

async function markerRpc<T>(marker: ReplayMarker, command: object): Promise<T> {
  const response = await marker.fetch(
    new Request("https://replay-markers/rpc", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(command),
    }),
  );
  const body = (await response.json()) as { ok: boolean; result: T };
  expect(response.ok).toBe(true);
  expect(body.ok).toBe(true);
  return body.result;
}

function createWorkerEnv(marker: ReplayMarker): Env {
  const namespace = {
    idFromName: () => ({}) as DurableObjectId,
    get: () => ({
      fetch: (input: string | URL | Request, init?: RequestInit) =>
        marker.fetch(input instanceof Request ? input : new Request(input, init)),
    }),
  } as any;
  return {
    REPLAY_MARKERS: namespace,
    BANGUMI_CLIENT_ID: "test-client",
    BANGUMI_CLIENT_SECRET: "test-secret",
    SESSION_ENCRYPTION_KEY: "test-session-encryption-key",
    BANGUMI_AUTHORIZE_URL: "https://bgm.tv/oauth/authorize",
    BANGUMI_TOKEN_URL: "https://mock.bangumi.test/access_token",
    BANGUMI_API_BASE_URL: "https://mock.bangumi.test",
    BANGUMI_CALLBACK_URL: "https://broker.test/api/v1/oauth/bangumi/callback",
    APP_LINK_REDIRECT_URI: "https://broker.test/app/oauth-complete",
    CUSTOM_SCHEME_REDIRECT_URI: "ani://bangumi-oauth-callback",
  };
}

async function workerRequest(env: Env, url: string, init?: RequestInit): Promise<Response> {
  return worker.fetch(new Request(url, init), env);
}

function playbackChange(
  overrides: Partial<PlaybackSyncRequest["changes"][number]> = {},
): PlaybackSyncRequest["changes"][number] {
  return {
    subjectId: 10,
    episodeId: 20,
    positionMs: 1000,
    durationMs: 10000,
    completed: false,
    lastPlayedAt: 100,
    baseRevision: 0,
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Wynime OAuth callback and ticket flow", () => {
  it("returns to the app, exchanges a one-time ticket, and rejects callback/ticket replay", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith("/access_token")) {
          return new Response(
            JSON.stringify({
              access_token: "access-token-from-bangumi",
              refresh_token: "refresh-token-from-bangumi",
              expires_in: 3600,
            }),
            { status: 200 },
          );
        }
        if (url.endsWith("/v0/me")) {
          return new Response(JSON.stringify({ id: 42 }), { status: 200 });
        }
        throw new Error("unexpected upstream URL: " + url);
      }),
    );

    const state = "oauth-state-123456";
    const start = await workerRequest(
      env,
      "https://broker.test/api/v1/oauth/bangumi/start?state=" + encodeURIComponent(state),
    );
    expect(start.status).toBe(200);
    const authorizeUrl = new URL(((await start.json()) as { url: string }).url);
    expect(authorizeUrl.host).toBe("bgm.tv");
    expect([...authorizeUrl.searchParams.keys()].sort()).toEqual([
      "client_id",
      "redirect_uri",
      "response_type",
      "state",
    ]);

    const callback = await workerRequest(
      env,
      "https://broker.test/api/v1/oauth/bangumi/callback?state=" +
        encodeURIComponent(state) +
        "&code=realistic-authorization-code",
    );
    expect(callback.status).toBe(302);
    const appLocation = new URL(callback.headers.get("location")!);
    expect(appLocation.toString()).toMatch(/^https:\/\/broker\.test\/app\/oauth-complete\?/);
    expect(appLocation.searchParams.get("state")).toBe(state);
    const ticket = appLocation.searchParams.get("ticket");
    expect(ticket).toBeTruthy();
    expect(appLocation.toString()).not.toContain("access-token-from-bangumi");
    expect(appLocation.toString()).not.toContain("refresh-token-from-bangumi");

    const exchanged = await workerRequest(env, "https://broker.test/api/v1/oauth/bangumi/ticket/exchange", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ ticket }),
    });
    expect(exchanged.status).toBe(200);
    await expect(exchanged.json()).resolves.toMatchObject({
      bangumiAccessToken: "access-token-from-bangumi",
    });

    const ticketReplay = await workerRequest(env, "https://broker.test/api/v1/oauth/bangumi/ticket/exchange", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ ticket }),
    });
    expect(ticketReplay.status).toBe(410);

    const callbackReplay = await workerRequest(
      env,
      "https://broker.test/api/v1/oauth/bangumi/callback?state=" +
        encodeURIComponent(state) +
        "&code=replayed-authorization-code",
    );
    expect(callbackReplay.status).toBe(409);
  });

  it("handles cancellation, state mismatch, and expired tickets without completing another state", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const state = "oauth-state-cancel-123";
    const start = await workerRequest(
      env,
      "https://broker.test/api/v1/oauth/bangumi/start?state=" + encodeURIComponent(state),
    );
    expect(start.status).toBe(200);

    const canceled = await workerRequest(
      env,
      "https://broker.test/api/v1/oauth/bangumi/callback?state=" +
        encodeURIComponent(state) +
        "&error=access_denied",
    );
    expect(canceled.status).toBe(302);
    expect(new URL(canceled.headers.get("location")!).searchParams.get("error")).toBe("access_denied");

    const canceledReplay = await workerRequest(
      env,
      "https://broker.test/api/v1/oauth/bangumi/callback?state=" +
        encodeURIComponent(state) +
        "&error=access_denied",
    );
    expect(canceledReplay.status).toBe(409);

    const stateMismatch = await workerRequest(
      env,
      "https://broker.test/api/v1/oauth/bangumi/callback?state=oauth-state-other-123456&code=wrong-state-code",
    );
    expect(stateMismatch.status).toBe(400);
    await expect(stateMismatch.json()).resolves.toEqual({ error: "expired_state" });

    const expiredTicket = "expired-ticket-123456";
    await markerRpc(marker, {
      op: "oauthStart",
      stateHash: "unused-state-hash",
      record: { redirectUri: "ani://bangumi-oauth-callback", expiresAt: Date.now() + 60_000, status: "pending" },
    });
    await markerRpc(marker, {
      op: "oauthComplete",
      stateHash: "unused-state-hash",
      ticketHash: await hashToken(expiredTicket),
      ticket: { expiresAt: 1, payloadCiphertext: "expired-payload" },
    });
    const expiredResponse = await workerRequest(env, "https://broker.test/api/v1/oauth/bangumi/ticket/exchange", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ ticket: expiredTicket }),
    });
    expect(expiredResponse.status).toBe(410);
  });
});

describe("Wynime playback Durable Object", () => {
  it("syncs A to B and B to A with composite identity and no event log", async () => {
    const { marker, storage } = createMarker();
    const request = (deviceId: string, cursor: number, changes: PlaybackSyncRequest["changes"]) =>
      markerRpc<{
        cursor: number;
        serverChanges: Array<Record<string, unknown>>;
        accepted: Array<Record<string, unknown>>;
      }>(marker, {
        op: "playbackSync",
        userKey: "bangumi:42",
        request: { deviceId, cursor, changes },
        now: Date.now(),
      });

    const fromA = await request("device-a", 0, [playbackChange()]);
    expect(fromA.accepted).toEqual([{ subjectId: 10, episodeId: 20, revision: 1 }]);

    const pulledByB = await request("device-b", 0, []);
    expect(pulledByB.cursor).toBe(1);
    expect(pulledByB.serverChanges).toContainEqual(
      expect.objectContaining({ subjectId: 10, episodeId: 20, positionMs: 1000, revision: 1 }),
    );

    const fromB = await request("device-b", 1, [
      playbackChange({ positionMs: 3000, lastPlayedAt: 200, baseRevision: 1 }),
      playbackChange({ subjectId: 11, positionMs: 500, lastPlayedAt: 150 }),
    ]);
    expect(fromB.accepted).toEqual([
      { subjectId: 10, episodeId: 20, revision: 2 },
      { subjectId: 11, episodeId: 20, revision: 1 },
    ]);

    const pulledByA = await request("device-a", 1, []);
    expect(pulledByA.serverChanges).toContainEqual(
      expect.objectContaining({ subjectId: 10, episodeId: 20, positionMs: 3000, revision: 2 }),
    );
    expect(pulledByA.serverChanges).toContainEqual(
      expect.objectContaining({ subjectId: 11, episodeId: 20, positionMs: 500, revision: 1 }),
    );

    const completedByB = await request("device-b", pulledByA.cursor, [
      playbackChange({ positionMs: 10000, completed: true, lastPlayedAt: 300, baseRevision: 2 }),
    ]);
    expect(completedByB.accepted).toEqual([{ subjectId: 10, episodeId: 20, revision: 3 }]);

    const staleIncomplete = await request("device-a", completedByB.cursor, [
      playbackChange({ positionMs: 6000, completed: false, lastPlayedAt: 300, baseRevision: 2 }),
    ]);
    expect(staleIncomplete.accepted).toEqual([]);
    expect(staleIncomplete.serverChanges).toContainEqual(
      expect.objectContaining({ subjectId: 10, episodeId: 20, completed: true, revision: 3 }),
    );

    expect([...storage.values.keys()].some((key) => key.startsWith("playback-event:"))).toBe(false);
    expect([...storage.values.keys()].filter((key) => key.startsWith("playback-record:"))).toHaveLength(2);
  });
});

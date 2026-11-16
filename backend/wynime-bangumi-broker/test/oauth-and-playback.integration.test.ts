import { afterEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { hashToken, encryptJson } from "../src/crypto";
import { CollectionStore } from "../src/collection-store";
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
  const collectionStore = new CollectionStore({ storage: new MemoryStorage() } as any);
  const collectionNamespace = {
    idFromName: () => ({}) as DurableObjectId,
    get: () => ({
      fetch: (input: string | URL | Request, init?: RequestInit) =>
        collectionStore.fetch(input instanceof Request ? input : new Request(input, init)),
    }),
  } as any;
  return {
    REPLAY_MARKERS: namespace,
    COLLECTION_STORES: collectionNamespace,
    BANGUMI_CLIENT_ID: "test-client",
    BANGUMI_CLIENT_SECRET: "test-secret",
    SESSION_ENCRYPTION_KEY: "test-session-encryption-key",
    BANGUMI_AUTHORIZE_URL: "https://bgm.tv/oauth/authorize",
    BANGUMI_TOKEN_URL: "https://mock.bangumi.test/access_token",
    BANGUMI_API_BASE_URL: "https://mock.bangumi.test",
    BANGUMI_CALLBACK_URL: "https://broker.test/api/v1/oauth/bangumi/callback",
    APP_LINK_REDIRECT_URI: "https://broker.test/app/oauth-complete",
    CUSTOM_SCHEME_REDIRECT_URI: "ani://bangumi-oauth-callback",
    ANDROID_APP_PACKAGES: "com.wynime.app",
    ANDROID_APP_SHA256_CERT_FINGERPRINTS:
      "57:F2:6D:84:A3:3C:10:A7:3B:42:9E:95:20:D7:2A:93:65:4E:90:AE:1F:E8:29:8D:47:9E:E3:42:D6:38:33:BD",
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
  it("publishes the collection API version and server clock", async () => {
    const { marker } = createMarker();
    const response = await workerRequest(createWorkerEnv(marker), "https://broker.test/healthz");

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      ok: true,
      apiVersion: 1,
    });
  });

  it("hands an App Link callback back to the app when Android opened the browser", async () => {
    const { marker } = createMarker();
    const response = await workerRequest(
      createWorkerEnv(marker),
      "https://broker.test/app/oauth-complete?state=oauth-state-fallback-123456&ticket=short-lived-ticket",
    );

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/html");
    const body = await response.text();
    expect(body).toContain("ani://bangumi-oauth-callback");
    expect(body).toContain("oauth-state-fallback-123456");
    expect(body).toContain("short-lived-ticket");
    expect(body).not.toContain("access-token");
  });

  it("publishes the current Android release App Link", async () => {
    const { marker } = createMarker();
    const response = await workerRequest(createWorkerEnv(marker), "https://broker.test/.well-known/assetlinks.json");

    expect(response.status).toBe(200);
    const statements = (await response.json()) as Array<{
      target: { package_name: string; sha256_cert_fingerprints: string[] };
    }>;
    const currentStatements = statements.filter(({ target }) =>
      ["com.wynime.app"].includes(target.package_name),
    );

    expect(currentStatements).toHaveLength(1);
    for (const statement of currentStatements) {
      expect(statement.target.sha256_cert_fingerprints).toEqual([
        "57:F2:6D:84:A3:3C:10:A7:3B:42:9E:95:20:D7:2A:93:65:4E:90:AE:1F:E8:29:8D:47:9E:E3:42:D6:38:33:BD",
      ]);
    }
  });

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

describe("Wynime authenticated collection removal", () => {
  async function saveSession(marker: ReplayMarker, env: Env, token: string, id: number, expiresAt = Date.now() + 86400000) {
    await markerRpc(marker, {
      op: "putSession", sessionHash: await hashToken(token),
      record: {
        userKey: `bangumi:${id}`, expiresAt,
        payloadCiphertext: await encryptJson({ userKey: `bangumi:${id}`, accessToken: `access-${id}`, refreshToken: "refresh", expiresAt }, env.SESSION_ENCRYPTION_KEY),
      },
    });
  }

  it("requires a valid session and isolates create/status/confirm by account", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-session-account-42-for-tests";
    const other = "collection-session-account-99-for-tests";
    await saveSession(marker, env, token, 42);
    await saveSession(marker, env, other, 99);
    const url = "https://broker.test/api/v1/collections/701779/removal";
    const request = (session: string, method = "GET", suffix = "") => workerRequest(env, url + suffix, {
      method, headers: { authorization: `Bearer ${session}` },
    });
    expect((await workerRequest(env, url, { method: "POST" })).status).toBe(401);
    expect((await request(token, "POST")).status).toBe(200);
    expect((await request(other)).status).toBe(404);
    expect((await request(other, "POST", "/confirm")).status).toBe(404);
    let upstreamStatus = 200;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(new Headers(init?.headers).get("authorization")).toBe("Bearer access-42");
      return String(input).endsWith("/me")
        ? Response.json({ id: 42, username: "account-42" })
        : new Response(null, { status: upstreamStatus });
    }));
    expect(await (await request(token, "POST", "/confirm")).json()).toMatchObject({ status: "awaiting_web_action" });
    upstreamStatus = 404;
    expect(await (await request(token, "POST", "/confirm")).json()).toMatchObject({ status: "confirmed" });
    expect(await (await request(token, "POST")).json()).toMatchObject({ status: "awaiting_web_action" });
    upstreamStatus = 429;
    expect((await request(token, "POST", "/confirm")).status).toBe(502);
    expect(await (await request(token)).json()).toMatchObject({ status: "awaiting_web_action" });
    await saveSession(marker, env, token, 42, 1);
    expect((await request(token, "POST", "/confirm")).status).toBe(401);
  });

  it("expires operations and lets the same account start a retry", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-session-expiry-check-for-tests";
    await saveSession(marker, env, token, 42);
    const url = "https://broker.test/api/v1/collections/701779/removal";
    const headers = { authorization: `Bearer ${token}` };
    await workerRequest(env, url, { method: "POST", headers });
    vi.useFakeTimers();
    try {
      vi.setSystemTime(Date.now() + 7200000);
      expect((await workerRequest(env, url, { headers })).status).toBe(404);
      expect((await workerRequest(env, url, { method: "POST", headers })).status).toBe(200);
    } finally { vi.useRealTimers(); }
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

    const staleOlderTimestampWithCurrentBase = await request("device-a", staleIncomplete.cursor, [
      playbackChange({ positionMs: 2000, completed: false, lastPlayedAt: 200, baseRevision: 3 }),
    ]);
    expect(staleOlderTimestampWithCurrentBase.accepted).toEqual([]);
    expect(staleOlderTimestampWithCurrentBase.serverChanges).toContainEqual(
      expect.objectContaining({ subjectId: 10, episodeId: 20, positionMs: 10000, completed: true, revision: 3 }),
    );

    expect([...storage.values.keys()].some((key) => key.startsWith("playback-event:"))).toBe(false);
    expect([...storage.values.keys()].filter((key) => key.startsWith("playback-record:"))).toHaveLength(2);
  });
});

describe("Wynime collection Durable Object", () => {
  async function saveSession(marker: ReplayMarker, env: Env, token: string, id = 42) {
    const expiresAt = Date.now() + 86400000;
    await markerRpc(marker, {
      op: "putSession",
      sessionHash: await hashToken(token),
      record: {
        userKey: `bangumi:${id}`,
        expiresAt,
        payloadCiphertext: await encryptJson(
          { userKey: `bangumi:${id}`, accessToken: `access-${id}`, refreshToken: "refresh", expiresAt },
          env.SESSION_ENCRYPTION_KEY,
        ),
      },
    });
  }

  it("performs an authenticated write/read/delete storage probe", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-storage-probe-session-token";
    await saveSession(marker, env, token);

    const response = await workerRequest(env, "https://broker.test/api/v1/diagnostics/storage-test", {
      method: "POST",
      headers: { authorization: `Bearer ${token}` },
    });
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ ok: true });
  });

  it("keeps the newest field and makes retries idempotent", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-sync-session-token-with-enough-length";
    await saveSession(marker, env, token);
    const url = "https://broker.test/api/v1/collections/sync";
    const request = (body: object) => workerRequest(env, url, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(body),
    });

    const firstMutation = {
      mutationId: "mutation-new",
      deviceId: "device-a",
      kind: "subject",
      subjectId: 701779,
      modifiedAt: 200,
      changedFields: ["collectionType"],
      collectionType: "DOING",
    };
    const first = await request({ deviceId: "device-a", cursor: 0, mode: "fast", changes: [firstMutation] });
    expect(first.status).toBe(200);
    const firstBody = await first.json() as { records: Array<Record<string, unknown>> };
    expect(firstBody.records).toContainEqual(expect.objectContaining({ collectionType: "DOING" }));

    const stale = await request({
      deviceId: "device-b",
      cursor: 0,
      mode: "fast",
      changes: [{
        ...firstMutation,
        mutationId: "mutation-old",
        deviceId: "device-b",
        modifiedAt: 100,
        collectionType: "WISH",
      }],
    });
    expect(stale.status).toBe(200);
    const staleBody = await stale.json() as { records: Array<Record<string, unknown>> };
    expect(staleBody.records).toContainEqual(expect.objectContaining({ collectionType: "DOING" }));

    const retry = await request({ deviceId: "device-a", cursor: 2, mode: "fast", changes: [firstMutation] });
    expect(retry.status).toBe(200);
    const retryBody = await retry.json() as { accepted: Array<Record<string, unknown>> };
    expect(retryBody.accepted).toEqual([{ mutationId: "mutation-new", revision: 1, changed: true }]);

    const staleWithCurrentCursor = await request({
      deviceId: "device-b",
      cursor: 1,
      mode: "fast",
      changes: [{
        ...firstMutation,
        mutationId: "mutation-old-with-current-cursor",
        deviceId: "device-b",
        modifiedAt: 100,
        collectionType: "WISH",
      }],
    });
    expect(staleWithCurrentCursor.status).toBe(200);
    await expect(staleWithCurrentCursor.json()).resolves.toMatchObject({
      accepted: [{ mutationId: "mutation-old-with-current-cursor", revision: 1, changed: false }],
      records: [expect.objectContaining({ collectionType: "DOING", revision: 1 })],
    });
  });

  it("accepts locally first and reconciles the durable queue to Bangumi", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-reconcile-session-token-with-enough-length";
    await saveSession(marker, env, token);
    const upstream = vi.fn(async (input: RequestInfo | URL) => {
      expect(String(input)).toContain("/v0/users/-/collections/701779");
      return new Response(null, { status: 200 });
    });
    vi.stubGlobal("fetch", upstream);

    const response = await workerRequest(env, "https://broker.test/api/v1/collections/sync", {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        deviceId: "device-a",
        cursor: 0,
        mode: "reconcile",
        changes: [{
          mutationId: "reconcile-mutation",
          deviceId: "device-a",
          kind: "subject",
          subjectId: 701779,
          modifiedAt: 300,
          changedFields: ["collectionType", "rate"],
          collectionType: "DOING",
          rate: 8,
        }],
      }),
    });
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      upstreamStatus: "synced",
      pendingCount: 0,
    });
    expect(upstream).toHaveBeenCalledTimes(1);
  });

  it("bounds a device clock that is far in the future", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-clock-skew-session-token-with-enough-length";
    await saveSession(marker, env, token);
    const before = Date.now();
    const response = await workerRequest(env, "https://broker.test/api/v1/collections/sync", {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        deviceId: "device-a",
        cursor: 0,
        mode: "fast",
        changes: [{
          mutationId: "future-clock-mutation",
          deviceId: "device-a",
          kind: "subject",
          subjectId: 701779,
          modifiedAt: before + 24 * 60 * 60 * 1000,
          changedFields: ["collectionType"],
          collectionType: "DOING",
        }],
      }),
    });

    expect(response.status).toBe(200);
    const body = await response.json() as { records: Array<{ fieldModifiedAt: Record<string, number> }> };
    expect(body.records[0].fieldModifiedAt.collectionType).toBeLessThanOrEqual(Date.now() + 5 * 60 * 1000);
  });

  it("drops superseded pending fields before pushing them upstream", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-superseded-session-token-with-enough-length";
    await saveSession(marker, env, token);
    const url = "https://broker.test/api/v1/collections/sync";
    const request = (body: object) => workerRequest(env, url, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(body),
    });

    await request({
      deviceId: "device-a",
      cursor: 0,
      mode: "fast",
      changes: [{
        mutationId: "superseded-old",
        deviceId: "device-a",
        kind: "subject",
        subjectId: 701779,
        modifiedAt: 100,
        changedFields: ["collectionType"],
        collectionType: "WISH",
      }],
    });
    await request({
      deviceId: "device-a",
      cursor: 1,
      mode: "fast",
      changes: [{
        mutationId: "superseded-new",
        deviceId: "device-a",
        kind: "subject",
        subjectId: 701779,
        modifiedAt: 200,
        changedFields: ["collectionType"],
        collectionType: "DONE",
      }],
    });

    const upstream = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toContain("/v0/users/-/collections/701779");
      expect(JSON.parse(String(init?.body))).toEqual({ type: 2 });
      return new Response(null, { status: 200 });
    });
    vi.stubGlobal("fetch", upstream);
    const response = await request({
      deviceId: "device-a",
      cursor: 2,
      mode: "reconcile",
      changes: [],
    });

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      upstreamStatus: "synced",
      pendingCount: 0,
    });
    expect(upstream).toHaveBeenCalledTimes(1);
  });

  it("reconciles a web-confirmed removal instead of overwriting it", async () => {
    const { marker } = createMarker();
    const env = createWorkerEnv(marker);
    const token = "collection-removal-session-token-with-enough-length";
    await saveSession(marker, env, token);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/v0/me")) {
        return new Response(JSON.stringify({ id: 42, username: "test-user" }), { status: 200 });
      }
      if (url.endsWith("/v0/users/test-user/collections/701779")) {
        return new Response(null, { status: 404 });
      }
      throw new Error("unexpected upstream URL: " + url);
    }));

    const response = await workerRequest(env, "https://broker.test/api/v1/collections/sync", {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        deviceId: "device-a",
        cursor: 0,
        mode: "reconcile",
        changes: [{
          mutationId: "removal-mutation",
          deviceId: "device-a",
          kind: "subject",
          subjectId: 701779,
          modifiedAt: 300,
          changedFields: ["collectionType"],
          collectionType: "NOT_COLLECTED",
        }],
      }),
    });

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      upstreamStatus: "synced",
      pendingCount: 0,
    });
  });
});

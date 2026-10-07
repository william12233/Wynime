import { afterEach, describe, expect, it, vi } from "vitest";
import { collectionRemoval, type CollectionRemoval } from "../src/collection-removal";
import { encryptJson } from "../src/crypto";
import type { Env, SessionRecord } from "../src/protocol";

const env = { SESSION_ENCRYPTION_KEY: "test-session-encryption-key", BANGUMI_API_BASE_URL: "https://bangumi.test" } as Env;
const storage = new Map<string, CollectionRemoval>();
const rpc = async <T>(_: Env, command: any): Promise<T> => {
  const key = `${command.userKey}:${command.subjectId ?? command.record.subjectId}`;
  if (command.op === "putCollectionRemoval") storage.set(key, command.record);
  return (storage.get(key) ?? null) as T;
};

async function session(userKey = "bangumi:123"): Promise<SessionRecord> {
  return {
    userKey, expiresAt: Date.now() + 60000,
    payloadCiphertext: await encryptJson({ accessToken: "test-token" }, env.SESSION_ENCRYPTION_KEY),
  };
}

afterEach(() => { storage.clear(); vi.unstubAllGlobals(); });

describe("collection removal verification", () => {
  it("starts a pending web action without declaring deletion", async () => {
    const result = await collectionRemoval(new Request("https://broker.test"), env, await session(), 10, "start", rpc);
    expect(await result.json()).toMatchObject({ status: "awaiting_web_action", webUrl: "https://bgm.tv/subject/10" });
  });

  it.each([200, 401, 429, 503])("does not confirm a collection response of %s", async (status) => {
    const current = await session();
    await collectionRemoval(new Request("https://broker.test"), env, current, 10, "start", rpc);
    vi.stubGlobal("fetch", vi.fn(async (url: string) => url.endsWith("/me")
      ? Response.json({ id: 123, username: "tester" }) : new Response(null, { status })));
    await collectionRemoval(new Request("https://broker.test"), env, current, 10, "confirm", rpc);
    expect(storage.get("bangumi:123:10")?.status).toBe("awaiting_web_action");
  });

  it("confirms only an authenticated same-account 404", async () => {
    const current = await session();
    await collectionRemoval(new Request("https://broker.test"), env, current, 10, "start", rpc);
    vi.stubGlobal("fetch", vi.fn(async (url: string) => url.endsWith("/me")
      ? Response.json({ id: 123, username: "tester" }) : new Response(null, { status: 404 })));
    const result = await collectionRemoval(new Request("https://broker.test"), env, current, 10, "confirm", rpc);
    expect(await result.json()).toMatchObject({ status: "confirmed" });
  });

  it("isolates operations by account", async () => {
    await collectionRemoval(new Request("https://broker.test"), env, await session(), 10, "start", rpc);
    const result = await collectionRemoval(new Request("https://broker.test"), env, await session("bangumi:456"), 10, "status", rpc);
    expect(result.status).toBe(404);
  });

  it("rejects an authenticated account mismatch", async () => {
    const current = await session();
    await collectionRemoval(new Request("https://broker.test"), env, current, 10, "start", rpc);
    vi.stubGlobal("fetch", vi.fn(async () => Response.json({ id: 456, username: "other" })));
    const result = await collectionRemoval(new Request("https://broker.test"), env, current, 10, "confirm", rpc);
    expect(result.status).toBe(409);
    expect(storage.get("bangumi:123:10")?.status).toBe("awaiting_web_action");
  });
});

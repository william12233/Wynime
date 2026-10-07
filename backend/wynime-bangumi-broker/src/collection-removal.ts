import { decryptJson } from "./crypto";
import type { BangumiGrant, Env, SessionRecord } from "./protocol";

export interface CollectionRemoval {
  subjectId: number;
  status: "awaiting_web_action" | "confirmed";
  webUrl: string;
  expiresAt: number;
}

type Rpc = <T>(env: Env, command: object) => Promise<T>;

export async function collectionRemoval(
  request: Request,
  env: Env,
  session: SessionRecord,
  subjectId: number,
  action: "start" | "status" | "confirm",
  rpc: Rpc,
): Promise<Response> {
  if (!Number.isSafeInteger(subjectId) || subjectId <= 0) return response({ error: "invalid_subject" }, 400);
  const now = Date.now();
  let record = await rpc<CollectionRemoval | null>(env, {
    op: "getCollectionRemoval", userKey: session.userKey, subjectId, now,
  });
  if (action === "start") {
    record = {
      subjectId, status: "awaiting_web_action", webUrl: `https://bgm.tv/subject/${subjectId}`,
      expiresAt: now + 60 * 60 * 1000,
    };
    await rpc(env, { op: "putCollectionRemoval", userKey: session.userKey, record });
  }
  if (!record) return response({ error: "removal_not_found" }, 404);
  if (action === "confirm") {
    const grant = await decryptJson<BangumiGrant>(session.payloadCiphertext, env.SESSION_ENCRYPTION_KEY);
    const base = env.BANGUMI_API_BASE_URL || "https://api.bgm.tv";
    const headers = { authorization: `Bearer ${grant.accessToken}`, accept: "application/json", "user-agent": "Wynime/1.0" };
    const userResponse = await fetch(`${base}/v0/me`, { headers });
    if (!userResponse.ok) return response({ error: "bangumi_authentication_failed" }, userResponse.status === 401 ? 401 : 502);
    const user = await userResponse.json() as { id: number; username: string };
    if (`bangumi:${user.id}` !== session.userKey || !user.username) return response({ error: "account_mismatch" }, 409);
    const remote = await fetch(`${base}/v0/users/${encodeURIComponent(user.username)}/collections/${subjectId}`, { headers });
    if (remote.status === 404) {
      record = { ...record, status: "confirmed" };
      await rpc(env, { op: "putCollectionRemoval", userKey: session.userKey, record });
    } else if (remote.ok) {
      record = { ...record, status: "awaiting_web_action" };
      await rpc(env, { op: "putCollectionRemoval", userKey: session.userKey, record });
    } else {
      return response({ error: "bangumi_verification_failed" }, 502);
    }
  }
  return response(record);
}

function response(body: object, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status, headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}

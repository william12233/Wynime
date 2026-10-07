import { decryptJson, encryptJson, hashToken, randomToken } from "./crypto";
import { ReplayMarker } from "./replay-markers";
import { collectionRemoval } from "./collection-removal";
import type {
  BangumiGrant,
  Env,
  OAuthStateRecord,
  OAuthTicketRecord,
  PlaybackChange,
  PlaybackSyncRequest,
  SessionRecord,
} from "./protocol";

export { ReplayMarker };

const DEFAULT_AUTHORIZE_URL = "https://bgm.tv/oauth/authorize";
const DEFAULT_TOKEN_URL = "https://bgm.tv/oauth/access_token";
const DEFAULT_API_BASE_URL = "https://api.bgm.tv";
const DEFAULT_APP_LINK_REDIRECT_URI =
  "https://wynime-bangumi-broker.wzhou785.workers.dev/app/oauth-complete";
const DEFAULT_CUSTOM_SCHEME_REDIRECT_URI = "ani://bangumi-oauth-callback";
const LEGACY_ASSETLINKS_PACKAGE = "io.github.william12233.wynime";
const LEGACY_ASSETLINKS_FINGERPRINT =
  "32:FA:14:32:9B:DA:4D:DD:9C:2F:9D:6B:E9:73:EF:70:A4:27:2B:56:30:AB:BA:B7:14:FF:50:F0:F6:25:4B:53";
const OAUTH_STATE_TTL_MS = 10 * 60 * 1000;
const OAUTH_TICKET_TTL_MS = 5 * 60 * 1000;
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_REQUEST_BYTES = 128 * 1024;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));

    try {
      const response = await route(request, env);
      return cors(response);
    } catch (error) {
      if (error instanceof HttpError) {
        return cors(json({ error: error.code }, error.status));
      }
      return cors(json({ error: "service_unavailable" }, 503));
    }
  },
};

async function route(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const removalMatch = /^\/api\/v1\/collections\/(\d+)\/removal(\/confirm)?$/.exec(url.pathname);
  if (removalMatch) {
    const action = removalMatch[2] ? "confirm" : request.method === "POST" ? "start" : "status";
    if (request.method !== (action === "status" ? "GET" : "POST")) throw new HttpError(405, "method_not_allowed");
    return collectionRemoval(request, env, await authenticateSession(request, env), Number(removalMatch[1]), action, rpc);
  }
  if (url.pathname === "/.well-known/assetlinks.json") {
    return assetLinks(env);
  }
  if (url.pathname === "/healthz" && request.method === "GET") {
    return json({ ok: true });
  }
  if (url.pathname === "/app/oauth-complete" && request.method === "GET") {
    return oauthAppLinkFallback(url, env);
  }
  if (url.pathname === "/api/v1/oauth/bangumi/start" && request.method === "GET") {
    return startOAuth(url, env);
  }
  if (url.pathname === "/api/v1/oauth/bangumi/callback" && request.method === "GET") {
    return completeOAuth(url, env);
  }
  if (url.pathname === "/api/v1/oauth/bangumi/result" && request.method === "GET") {
    return oauthResult(url, env);
  }
  if (url.pathname === "/api/v1/oauth/bangumi/ticket/exchange" && request.method === "POST") {
    return exchangeTicket(request, env);
  }
  if (url.pathname === "/api/v1/session/refresh" && request.method === "POST") {
    return refreshSession(request, env);
  }
  if (url.pathname === "/api/v1/session/revoke" && request.method === "POST") {
    return revokeSession(request, env);
  }
  if (url.pathname === "/api/v1/playback/sync" && request.method === "POST") {
    return syncPlayback(request, env);
  }
  throw new HttpError(404, "not_found");
}

async function startOAuth(url: URL, env: Env): Promise<Response> {
  const clientId = env.BANGUMI_CLIENT_ID;
  if (!isNonEmptyString(clientId)) throw new HttpError(503, "oauth_not_configured");

  const state = url.searchParams.get("state");
  if (!state || state.length < 16 || state.length > 256) {
    throw new HttpError(400, "invalid_state");
  }
  const redirectUri = selectAppRedirectUri(url.searchParams.get("redirectUri"), env);
  const stateHash = await hashToken(state);
  await rpc(env, {
    op: "oauthStart",
    stateHash,
    record: {
      redirectUri,
      expiresAt: Date.now() + OAUTH_STATE_TTL_MS,
      status: "pending",
    } satisfies OAuthStateRecord,
  });

  const callbackUri =
    env.BANGUMI_CALLBACK_URL ||
    new URL("/api/v1/oauth/bangumi/callback", url.origin).toString();
  const authorizeUrl = new URL(env.BANGUMI_AUTHORIZE_URL || DEFAULT_AUTHORIZE_URL);
  authorizeUrl.searchParams.set("client_id", clientId);
  authorizeUrl.searchParams.set("response_type", "code");
  authorizeUrl.searchParams.set("redirect_uri", callbackUri);
  authorizeUrl.searchParams.set("state", state);
  return json({ url: authorizeUrl.toString() });
}

async function completeOAuth(url: URL, env: Env): Promise<Response> {
  const state = url.searchParams.get("state");
  if (!state || state.length < 16 || state.length > 256) {
    throw new HttpError(400, "invalid_state");
  }
  const stateHash = await hashToken(state);
  const stateRecord = await rpc<OAuthStateRecord | null>(env, {
    op: "oauthState",
    stateHash,
    now: Date.now(),
  });
  if (!stateRecord) throw new HttpError(400, "expired_state");
  if (stateRecord.status !== "pending") {
    throw new HttpError(409, "oauth_state_already_completed");
  }

  const upstreamError = url.searchParams.get("error");
  if (upstreamError) {
    await markOAuthError(env, stateHash, "access_denied");
    return redirectToApp(stateRecord.redirectUri, state, undefined, "access_denied");
  }

  const code = url.searchParams.get("code");
  if (!code || code.length > 4096) {
    await markOAuthError(env, stateHash, "missing_code");
    return redirectToApp(stateRecord.redirectUri, state, undefined, "missing_code");
  }

  let failureStage = "validate_config";
  try {
    requireOAuthSecrets(env);
    const callbackUri =
      env.BANGUMI_CALLBACK_URL ||
      new URL("/api/v1/oauth/bangumi/callback", url.origin).toString();
    failureStage = "bangumi_token_exchange";
    const token = await exchangeBangumiToken(env, {
      grant_type: "authorization_code",
      code,
      redirect_uri: callbackUri,
    });
    if (!token.refreshToken) throw new Error("Bangumi token response is missing refresh token");
    failureStage = "bangumi_user_lookup";
    const userKey = await fetchBangumiUserKey(env, token.accessToken);
    const grant: BangumiGrant = {
      userKey,
      accessToken: token.accessToken,
      refreshToken: token.refreshToken,
      expiresAt: token.expiresAt,
    };
    const ticket = randomToken();
    const ticketHash = await hashToken(ticket);
    failureStage = "ticket_encrypt";
    const payloadCiphertext = await encryptJson({ ticket, grant }, env.SESSION_ENCRYPTION_KEY);
    const ticketRecord: OAuthTicketRecord = {
      expiresAt: Date.now() + OAUTH_TICKET_TTL_MS,
      payloadCiphertext,
    };
    failureStage = "oauth_state_commit";
    const result = await rpc<{ accepted: boolean }>(env, {
      op: "oauthComplete",
      stateHash,
      ticketHash,
      ticket: ticketRecord,
    });
    if (!result.accepted) throw new HttpError(409, "oauth_state_already_completed");
    return redirectToApp(stateRecord.redirectUri, state, ticket);
  } catch (error) {
    if (error instanceof HttpError && error.status === 409) throw error;
    console.error("oauth_complete_failed", {
      stage: failureStage,
      type: error instanceof Error ? error.message : "unknown",
    });
    await markOAuthError(env, stateHash, "upstream_authorization_failed");
    return redirectToApp(stateRecord.redirectUri, state, undefined, "upstream_authorization_failed");
  }
}

async function oauthResult(url: URL, env: Env): Promise<Response> {
  const state = url.searchParams.get("state");
  if (!state) throw new HttpError(400, "missing_state");
  const result = await rpc<{
    status: "pending" | "complete" | "error" | "missing" | "exchanged";
    payloadCiphertext?: string;
    error?: string;
  }>(env, {
    op: "oauthResult",
    stateHash: await hashToken(state),
    now: Date.now(),
  });
  if (result.status === "pending") return json({ ticket: null }, 425);
  if (result.status === "error") return json({ error: result.error || "oauth_failed" }, 400);
  if (result.status !== "complete" || !result.payloadCiphertext) {
    throw new HttpError(404, "oauth_ticket_unavailable");
  }
  const payload = await decryptJson<{ ticket: string; grant: BangumiGrant }>(
    result.payloadCiphertext,
    env.SESSION_ENCRYPTION_KEY,
  );
  return json({ ticket: payload.ticket });
}

async function exchangeTicket(request: Request, env: Env): Promise<Response> {
  const body = await readJson<{ ticket?: unknown }>(request);
  if (!isNonEmptyString(body.ticket) || body.ticket.length > 512) {
    throw new HttpError(400, "invalid_ticket");
  }
  const payloadCiphertext = await rpc<string | null>(env, {
    op: "consumeTicket",
    ticketHash: await hashToken(body.ticket),
    now: Date.now(),
  });
  if (!payloadCiphertext) throw new HttpError(410, "ticket_expired");

  const payload = await decryptJson<{ ticket: string; grant: BangumiGrant }>(
    payloadCiphertext,
    env.SESSION_ENCRYPTION_KEY,
  );
  if (payload.ticket !== body.ticket) throw new HttpError(410, "ticket_expired");
  return createSession(payload.grant, env);
}

async function refreshSession(request: Request, env: Env): Promise<Response> {
  const sessionToken = requireBearerToken(request);
  const oldSessionHash = await hashToken(sessionToken);
  const session = await rpc<SessionRecord | null>(env, {
    op: "getSession",
    sessionHash: oldSessionHash,
    now: Date.now(),
  });
  if (!session) throw new HttpError(401, "session_expired");

  const grant = await decryptJson<BangumiGrant>(session.payloadCiphertext, env.SESSION_ENCRYPTION_KEY);
  try {
    requireOAuthSecrets(env);
    const token = await exchangeBangumiToken(env, {
      grant_type: "refresh_token",
      refresh_token: grant.refreshToken,
    });
    const nextGrant: BangumiGrant = {
      ...grant,
      accessToken: token.accessToken,
      refreshToken: token.refreshToken || grant.refreshToken,
      expiresAt: token.expiresAt,
    };
    return rotateSession(oldSessionHash, nextGrant, env);
  } catch {
    throw new HttpError(502, "bangumi_refresh_failed");
  }
}

async function revokeSession(request: Request, env: Env): Promise<Response> {
  const sessionToken = requireBearerToken(request);
  await rpc(env, { op: "deleteSession", sessionHash: await hashToken(sessionToken) });
  return json({ ok: true });
}

async function syncPlayback(request: Request, env: Env): Promise<Response> {
  const session = await authenticateSession(request, env);
  const body = validatePlaybackRequest(await readJson<unknown>(request));
  return json(
    await rpc(env, {
      op: "playbackSync",
      userKey: session.userKey,
      request: body,
      now: Date.now(),
    }),
  );
}

async function authenticateSession(request: Request, env: Env): Promise<SessionRecord> {
  const token = requireBearerToken(request);
  const session = await rpc<SessionRecord | null>(env, {
    op: "getSession",
    sessionHash: await hashToken(token),
    now: Date.now(),
  });
  if (!session) throw new HttpError(401, "session_expired");
  return session;
}

async function createSession(grant: BangumiGrant, env: Env): Promise<Response> {
  const sessionToken = randomToken();
  await rpc(env, {
    op: "putSession",
    sessionHash: await hashToken(sessionToken),
    record: {
      userKey: grant.userKey,
      expiresAt: Date.now() + SESSION_TTL_MS,
      payloadCiphertext: await encryptJson(grant, env.SESSION_ENCRYPTION_KEY),
    } satisfies SessionRecord,
  });
  return json({
    sessionToken,
    bangumiAccessToken: grant.accessToken,
    expiresAtMillis: grant.expiresAt,
  });
}

async function rotateSession(
  oldSessionHash: string,
  grant: BangumiGrant,
  env: Env,
): Promise<Response> {
  const sessionToken = randomToken();
  await rpc(env, {
    op: "replaceSession",
    oldSessionHash,
    newSessionHash: await hashToken(sessionToken),
    record: {
      userKey: grant.userKey,
      expiresAt: Date.now() + SESSION_TTL_MS,
      payloadCiphertext: await encryptJson(grant, env.SESSION_ENCRYPTION_KEY),
    } satisfies SessionRecord,
  });
  return json({
    sessionToken,
    bangumiAccessToken: grant.accessToken,
    expiresAtMillis: grant.expiresAt,
  });
}

async function exchangeBangumiToken(
  env: Env,
  fields: Record<string, string>,
): Promise<{ accessToken: string; refreshToken: string; expiresAt: number }> {
  const body = new URLSearchParams({
    client_id: env.BANGUMI_CLIENT_ID,
    client_secret: env.BANGUMI_CLIENT_SECRET,
    ...fields,
  });
  const response = await fetch(env.BANGUMI_TOKEN_URL || DEFAULT_TOKEN_URL, {
    method: "POST",
    headers: {
      accept: "application/json",
      "content-type": "application/x-www-form-urlencoded",
    },
    body,
  });
  if (!response.ok) throw new Error("Bangumi token exchange failed");
  const payload = (await response.json()) as Record<string, unknown>;
  const accessToken = typeof payload.access_token === "string" ? payload.access_token : "";
  const refreshToken = typeof payload.refresh_token === "string" ? payload.refresh_token : "";
  if (!accessToken) throw new Error("Bangumi token response is incomplete");
  const expiresIn = Number(payload.expires_in);
  const expiresAt = Date.now() + (Number.isFinite(expiresIn) && expiresIn > 0 ? expiresIn * 1000 : 30 * 86400000);
  return { accessToken, refreshToken, expiresAt };
}

async function fetchBangumiUserKey(env: Env, accessToken: string): Promise<string> {
  const response = await fetch((env.BANGUMI_API_BASE_URL || DEFAULT_API_BASE_URL) + "/v0/me", {
    headers: {
      authorization: "Bearer " + accessToken,
      accept: "application/json",
      "user-agent": "Wynime/1.0",
    },
  });
  if (!response.ok) throw new Error("Bangumi user lookup failed");
  const payload = (await response.json()) as Record<string, unknown>;
  const id = payload.id ?? payload.user_id;
  if (typeof id !== "number" && typeof id !== "string") throw new Error("Bangumi user id is missing");
  return "bangumi:" + String(id);
}

function validatePlaybackRequest(value: unknown): PlaybackSyncRequest {
  if (!isRecord(value)) throw new HttpError(400, "invalid_playback_request");
  const deviceId = value.deviceId;
  const cursor = value.cursor;
  const changes = value.changes;
  if (!isNonEmptyString(deviceId) || deviceId.length > 256 || !isSafeNonNegativeInteger(cursor)) {
    throw new HttpError(400, "invalid_playback_request");
  }
  if (!Array.isArray(changes) || changes.length > 100) {
    throw new HttpError(400, "invalid_playback_request");
  }
  return {
    deviceId,
    cursor,
    changes: changes.map(validatePlaybackChange),
  };
}

function validatePlaybackChange(value: unknown): PlaybackChange {
  if (!isRecord(value)) throw new HttpError(400, "invalid_playback_change");
  const subjectId = value.subjectId;
  const episodeId = value.episodeId;
  const positionMs = value.positionMs;
  const durationMs = value.durationMs;
  const completed = value.completed;
  const lastPlayedAt = value.lastPlayedAt;
  const baseRevision = value.baseRevision;
  const deleted = value.deleted;
  if (
    !isSafePositiveInteger(subjectId) ||
    !isSafePositiveInteger(episodeId) ||
    !isSafeNonNegativeInteger(positionMs) ||
    !isSafeNonNegativeInteger(durationMs) ||
    typeof completed !== "boolean" ||
    !isSafeNonNegativeInteger(lastPlayedAt) ||
    !isSafeNonNegativeInteger(baseRevision) ||
    (deleted !== undefined && typeof deleted !== "boolean")
  ) {
    throw new HttpError(400, "invalid_playback_change");
  }
  return {
    subjectId,
    episodeId,
    positionMs,
    durationMs,
    completed,
    lastPlayedAt,
    baseRevision,
    deleted: deleted === true,
  };
}

function selectAppRedirectUri(requested: string | null, env: Env): string {
  const appLink = env.APP_LINK_REDIRECT_URI || DEFAULT_APP_LINK_REDIRECT_URI;
  const customScheme = env.CUSTOM_SCHEME_REDIRECT_URI || DEFAULT_CUSTOM_SCHEME_REDIRECT_URI;
  const candidate = requested || appLink;
  if (candidate !== appLink && candidate !== customScheme) {
    throw new HttpError(400, "invalid_redirect_uri");
  }
  return candidate;
}

async function markOAuthError(env: Env, stateHash: string, error: string) {
  await rpc(env, { op: "oauthComplete", stateHash, error });
}

function redirectToApp(
  redirectUri: string,
  state: string,
  ticket?: string,
  error?: string,
): Response {
  const target = new URL(redirectUri);
  target.searchParams.set("state", state);
  if (ticket) target.searchParams.set("ticket", ticket);
  if (error) target.searchParams.set("error", error);
  return Response.redirect(target.toString(), 302);
}

   
                                                                               
                                                                     
                                                                       
                                                                             
   
function oauthAppLinkFallback(url: URL, env: Env): Response {
  const state = url.searchParams.get("state");
  const ticket = url.searchParams.get("ticket");
  const error = url.searchParams.get("error");
  if (!state || (!ticket && !error)) {
    return new Response("Wynime OAuth callback is missing its result.", {
      status: 400,
      headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
    });
  }

  const target = new URL(env.CUSTOM_SCHEME_REDIRECT_URI || DEFAULT_CUSTOM_SCHEME_REDIRECT_URI);
  target.searchParams.set("state", state);
  if (ticket) target.searchParams.set("ticket", ticket);
  if (error) target.searchParams.set("error", error);

  const targetUrl = target.toString();
  const escapedTargetUrl = escapeHtml(targetUrl);
  const scriptTargetUrl = JSON.stringify(targetUrl)
    .replaceAll("<", "\\u003c")
    .replaceAll(">", "\\u003e")
    .replaceAll("&", "\\u0026");
  return new Response(
    `<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="0;url=${escapedTargetUrl}">
<title>Return to Wynime</title>
</head><body>
<p>Returning to Wynime…</p>
<p><a href="${escapedTargetUrl}">Open Wynime</a></p>
<script>window.location.replace(${scriptTargetUrl});</script>
</body></html>`,
    {
      headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
    },
  );
}

function escapeHtml(value: string): string {
  const entities: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  };
  return value.replace(/[&<>"']/g, (character) => entities[character]);
}

async function assetLinks(env: Env): Promise<Response> {
  const statements = [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: LEGACY_ASSETLINKS_PACKAGE,
        sha256_cert_fingerprints: [LEGACY_ASSETLINKS_FINGERPRINT],
      },
    },
  ];

  const packages = splitEnvironmentList(env.ANDROID_APP_PACKAGES || env.ANDROID_APP_PACKAGE);
  const fingerprints = splitEnvironmentList(
    env.ANDROID_APP_SHA256_CERT_FINGERPRINTS || env.ANDROID_APP_SHA256_CERT_FINGERPRINT,
  );
  if (packages.length > 0 && fingerprints.length > 0) {
    for (const packageName of packages) {
      statements.push({
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: packageName,
          sha256_cert_fingerprints: fingerprints,
        },
      });
    }
  }
  return json(statements);
}

function splitEnvironmentList(value: string | undefined): string[] {
  return value
    ?.split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0) || [];
}

function requireBearerToken(request: Request): string {
  const header = request.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) throw new HttpError(401, "missing_session");
  const token = header.slice("Bearer ".length).trim();
  if (token.length < 32 || token.length > 512) throw new HttpError(401, "invalid_session");
  return token;
}

function requireOAuthSecrets(env: Env) {
  if (!isNonEmptyString(env.BANGUMI_CLIENT_ID) ||
      !isNonEmptyString(env.BANGUMI_CLIENT_SECRET) ||
      !isNonEmptyString(env.SESSION_ENCRYPTION_KEY)) {
    throw new HttpError(503, "oauth_not_configured");
  }
}

async function readJson<T>(request: Request): Promise<T> {
  const text = await request.text();
  if (text.length > MAX_REQUEST_BYTES) throw new HttpError(413, "request_too_large");
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new HttpError(400, "invalid_json");
  }
}

async function rpc<T>(env: Env, command: object): Promise<T> {
  const id = env.REPLAY_MARKERS.idFromName("wynime-global");
  const response = await env.REPLAY_MARKERS.get(id).fetch("https://replay-markers/rpc", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(command),
  });
  const payload = (await response.json()) as {
    ok: boolean;
    result?: T;
  };
  if (!response.ok || !payload.ok) throw new HttpError(503, "storage_unavailable");
  return payload.result as T;
}

function isRecord(value: unknown): value is Record<string, any> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isSafeNonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isSafePositiveInteger(value: unknown): value is number {
  return isSafeNonNegativeInteger(value) && value > 0;
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "cache-control": "no-store",
      "content-type": "application/json; charset=utf-8",
    },
  });
}

function cors(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set("access-control-allow-origin", "*");
  headers.set("access-control-allow-headers", "authorization, content-type");
  headers.set("access-control-allow-methods", "GET, POST, OPTIONS");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super(code);
  }
}

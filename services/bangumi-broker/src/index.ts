export interface Env {
  BANGUMI_CLIENT_ID: string;
  BANGUMI_CLIENT_SECRET: string;
  BANGUMI_AUTHORIZE_URL?: string;
  BANGUMI_TOKEN_URL?: string;
  BANGUMI_TOKEN_STATUS_URL?: string;
  APP_LINK_HOST?: string;
  TICKET_KEY_B64: string;
  ANDROID_PACKAGE_NAME?: string;
  ANDROID_CERT_SHA256?: string;
  REPLAY_MARKERS: DurableObjectNamespace;
}

type OAuthTicket = {
  accountId: string;
  accessToken: string;
  refreshToken: string;
  /** The short lifetime of this one-time browser-to-app handoff ticket. */
  expiresAt: number;
  /** The upstream Bangumi access-token expiry, independent of the ticket. */
  accessTokenExpiresAt: number;
  state: string;
  nonce: string;
};

type OAuthState = {
  state: string;
  redirectUri: string;
  expiresAt: number;
};

const DEFAULT_AUTHORIZE_URL = 'https://bgm.tv/oauth/authorize';
const DEFAULT_TOKEN_URL = 'https://bgm.tv/oauth/access_token';
const DEFAULT_TOKEN_STATUS_URL = 'https://bgm.tv/oauth/token_status';
const BROKER_USER_AGENT = 'Wynime-Bangumi-Broker/1';
const PROVIDER_CALLBACK_PATH = '/oauth/callback';
const ANDROID_APP_CALLBACK_PATH = '/oauth/app-callback';
const TICKET_TTL_SECONDS = 300;
const MAX_ACCESS_TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60;
const MAX_BODY_BYTES = 64 * 1024;

export class ReplayMarker {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);
    const nonce = (await request.text()).trim();
    if (!/^[A-Za-z0-9_-]{32,256}$/.test(nonce)) {
      return json({ error: 'invalid_nonce' }, 400);
    }
    const existing = await this.state.storage.get<number>(nonce);
    if (typeof existing === 'number' && existing > Date.now()) {
      return json({ error: 'replay' }, 409);
    }
    if (existing != null) await this.state.storage.delete(nonce);
    await this.state.storage.put(nonce, Date.now() + TICKET_TTL_SECONDS * 1000);
    return json({ ok: true });
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    try {
      if (request.method === 'GET' && url.pathname === '/healthz') {
        return json({ ok: true });
      }
      if (
        request.method === 'GET' &&
        url.pathname === '/.well-known/assetlinks.json'
      ) {
        return assetLinks(env);
      }
      if (request.method === 'GET' && url.pathname === '/oauth/start') {
        return await start(url, env);
      }
      if (request.method === 'GET' && url.pathname === PROVIDER_CALLBACK_PATH) {
        return await callback(url, env);
      }
      if (request.method === 'GET' && url.pathname === ANDROID_APP_CALLBACK_PATH) {
        return androidAppCallback();
      }
      if (request.method === 'POST' && url.pathname === '/oauth/redeem') {
        return await redeem(request, env);
      }
      if (request.method === 'POST' && url.pathname === '/oauth/refresh') {
        return await refresh(request, env);
      }
      return json({ error: 'not_found' }, 404);
    } catch (error) {
      if (error instanceof BrokerError) return json({ error: error.code }, error.status);
      // Never include upstream responses, tokens, secrets or stack traces in
      // the public error body.
      return json({ error: 'broker_failed' }, 500);
    }
  },
};

async function start(url: URL, env: Env): Promise<Response> {
  const state = url.searchParams.get('state') ?? '';
  const redirectUri = url.searchParams.get('redirect_uri') ?? '';
  const clientId = url.searchParams.get('client_id') ?? '';
  if (!isSafeState(state) || !isSafeRedirectUri(redirectUri, env) || clientId !== env.BANGUMI_CLIENT_ID) {
    throw new BrokerError('invalid_oauth_request', 400);
  }
  const providerState = await encryptValue({
    state,
    redirectUri,
    expiresAt: Math.floor(Date.now() / 1000) + TICKET_TTL_SECONDS,
  } satisfies OAuthState, env);
  const authorize = new URL(env.BANGUMI_AUTHORIZE_URL ?? DEFAULT_AUTHORIZE_URL);
  authorize.searchParams.set('response_type', 'code');
  authorize.searchParams.set('client_id', env.BANGUMI_CLIENT_ID);
  authorize.searchParams.set(
    'redirect_uri',
    `${new URL(url).origin}${PROVIDER_CALLBACK_PATH}`,
  );
  authorize.searchParams.set('state', providerState);
  return Response.redirect(authorize.toString(), 302);
}

async function callback(url: URL, env: Env): Promise<Response> {
  const providerState = url.searchParams.get('state') ?? '';
  const redirectUri = `${url.origin}${PROVIDER_CALLBACK_PATH}`;
  if (providerState.length < 43 || providerState.length > 16384) {
    return json({ error: 'oauth_callback_invalid' }, 400);
  }
  let state: OAuthState;
  try {
    const value = await decryptValue(providerState, env);
    if (!isRecord(value) || typeof value.state !== 'string' ||
        typeof value.redirectUri !== 'string' || typeof value.expiresAt !== 'number' ||
        !isSafeState(value.state) || !isSafeRedirectUri(value.redirectUri, env) ||
        value.expiresAt <= Math.floor(Date.now() / 1000)) {
      throw new Error('invalid_state');
    }
    state = value as OAuthState;
  } catch (_) {
    return json({ error: 'oauth_state_invalid' }, 400);
  }
  const providerError = url.searchParams.get('error');
  if (providerError != null) {
    return redirectWithError(
      state.redirectUri,
      state.state,
      'oauth_denied',
    );
  }
  const code = url.searchParams.get('code') ?? '';
  if (code.length < 1 || code.length > 4096) {
    return redirectWithError(
      state.redirectUri,
      state.state,
      'oauth_callback_invalid',
    );
  }
  try {
    const token = await exchangeCode(code, redirectUri, env);
    const accountId = token.user_id;
    if (accountId == null) {
      throw new BrokerError('oauth_payload_invalid', 502);
    }
    const issuedAt = Math.floor(Date.now() / 1000);
    const ticket: OAuthTicket = {
      accountId,
      accessToken: token.access_token,
      refreshToken: token.refresh_token,
      // A redeem ticket is intentionally much shorter lived than the access
      // session. Keep the encrypted ticket lifetime and replay marker lifetime
      // identical so an unclaimed ticket can never outlive its nonce claim.
      expiresAt: issuedAt + TICKET_TTL_SECONDS,
      // The app must receive the provider token lifetime, not the five-minute
      // browser handoff lifetime. These are deliberately separate fields.
      accessTokenExpiresAt: issuedAt + token.expires_in,
      state: state.state,
      nonce: randomToken(32),
    };
    const encoded = await encryptTicket(ticket, env);
    const target = new URL(state.redirectUri);
    target.searchParams.set('state', state.state);
    target.searchParams.set('ticket', encoded);
    return Response.redirect(target.toString(), 302);
  } catch (error) {
    return redirectWithError(
      state.redirectUri,
      state.state,
      safeOAuthFailureCode(error),
    );
  }
}

async function redeem(request: Request, env: Env): Promise<Response> {
  const body = await readJson(request);
  const ticket = stringField(body, 'ticket', 4096);
  const state = stringField(body, 'state', 256);
  const clientId = stringField(body, 'client_id', 256);
  if (clientId !== env.BANGUMI_CLIENT_ID) throw new BrokerError('invalid_client', 401);
  const decoded = await decryptTicket(ticket, env);
  const now = Math.floor(Date.now() / 1000);
  if (decoded.state !== state || decoded.expiresAt <= now ||
      decoded.accessTokenExpiresAt <= now) {
    throw new BrokerError('ticket_invalid', 401);
  }
  await claimReplay(decoded.nonce, env);
  return json({
    account_id: decoded.accountId,
    access_token: decoded.accessToken,
    refresh_token: decoded.refreshToken,
    expires_in: decoded.accessTokenExpiresAt - now,
  });
}

async function refresh(request: Request, env: Env): Promise<Response> {
  const body = await readJson(request);
  const refreshToken = stringField(body, 'refresh_token', 4096);
  const accountId = stringField(body, 'account_id', 256);
  const clientId = stringField(body, 'client_id', 256);
  if (clientId !== env.BANGUMI_CLIENT_ID) throw new BrokerError('invalid_client', 401);
  const redirectUri = `${new URL(request.url).origin}${PROVIDER_CALLBACK_PATH}`;
  const token = await exchangeRefresh(refreshToken, redirectUri, env);
  const identity = token.user_id == null
    ? await tokenStatus(token.access_token, env)
    : { id: token.user_id };
  if (identity.id !== accountId) throw new BrokerError('account_mismatch', 409);
  return json({
    account_id: identity.id,
    access_token: token.access_token,
    refresh_token: token.refresh_token,
    expires_in: token.expires_in,
  });
}

async function exchangeCode(code: string, redirectUri: string, env: Env): Promise<TokenResponse> {
  return tokenRequest(new URL(env.BANGUMI_TOKEN_URL ?? DEFAULT_TOKEN_URL), {
    grant_type: 'authorization_code',
    client_id: env.BANGUMI_CLIENT_ID,
    client_secret: env.BANGUMI_CLIENT_SECRET,
    code,
    redirect_uri: redirectUri,
  }, true);
}

async function exchangeRefresh(
  refreshToken: string,
  redirectUri: string,
  env: Env,
): Promise<TokenResponse> {
  return tokenRequest(new URL(env.BANGUMI_TOKEN_URL ?? DEFAULT_TOKEN_URL), {
    grant_type: 'refresh_token',
    client_id: env.BANGUMI_CLIENT_ID,
    client_secret: env.BANGUMI_CLIENT_SECRET,
    refresh_token: refreshToken,
    redirect_uri: redirectUri,
  }, false);
}

type TokenResponse = {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user_id?: string;
};

async function tokenRequest(
  url: URL,
  fields: Record<string, string>,
  requireUserId: boolean,
): Promise<TokenResponse> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-www-form-urlencoded',
      accept: 'application/json',
      'user-agent': BROKER_USER_AGENT,
    },
    body: new URLSearchParams(fields),
  });
  const body = await boundedText(response);
  if (!response.ok) throw new BrokerError('oauth_provider_rejected', 502);
  let value: unknown;
  try {
    value = JSON.parse(body);
  } catch (_) {
    throw new BrokerError('oauth_payload_invalid', 502);
  }
  if (!isRecord(value) || typeof value.access_token !== 'string' ||
      typeof value.refresh_token !== 'string' || typeof value.expires_in !== 'number' ||
      !Number.isFinite(value.expires_in) || value.expires_in < 30 ||
      value.expires_in > MAX_ACCESS_TOKEN_TTL_SECONDS) {
    throw new BrokerError('oauth_payload_invalid', 502);
  }
  const userId = normalizeUserId(value.user_id);
  if (value.user_id != null && userId == null) {
    throw new BrokerError('oauth_payload_invalid', 502);
  }
  if (requireUserId && userId == null) {
    throw new BrokerError('oauth_payload_invalid', 502);
  }
  return {
    access_token: value.access_token,
    refresh_token: value.refresh_token,
    expires_in: value.expires_in,
    ...(userId == null ? {} : { user_id: userId }),
  };
}

async function tokenStatus(
  accessToken: string,
  env: Env,
): Promise<{ id: string }> {
  const response = await fetch(
    new URL(env.BANGUMI_TOKEN_STATUS_URL ?? DEFAULT_TOKEN_STATUS_URL),
    {
      method: 'POST',
      headers: {
        'content-type': 'application/x-www-form-urlencoded',
        accept: 'application/json',
        'user-agent': BROKER_USER_AGENT,
      },
      body: new URLSearchParams({ access_token: accessToken }),
    },
  );
  const body = await boundedText(response);
  if (!response.ok) throw new BrokerError('bangumi_identity_failed', 502);
  let value: unknown;
  try {
    value = JSON.parse(body);
  } catch (_) {
    throw new BrokerError('bangumi_identity_invalid', 502);
  }
  const id = isRecord(value) ? normalizeUserId(value.user_id) : null;
  if (id == null) throw new BrokerError('bangumi_identity_invalid', 502);
  return { id };
}

function normalizeUserId(value: unknown): string | null {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0) {
    return String(value);
  }
  if (typeof value === 'string' && /^[0-9]{1,256}$/.test(value)) {
    return value;
  }
  return null;
}

async function encryptTicket(ticket: OAuthTicket, env: Env): Promise<string> {
  return encryptValue(ticket, env);
}

async function encryptValue(value: OAuthTicket | OAuthState, env: Env): Promise<string> {
  const key = await keyFromEnv(env);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const plaintext = new TextEncoder().encode(JSON.stringify(value));
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plaintext));
  const output = new Uint8Array(iv.length + ciphertext.length);
  output.set(iv, 0);
  output.set(ciphertext, iv.length);
  return base64Url(output);
}

async function decryptTicket(encoded: string, env: Env): Promise<OAuthTicket> {
  const value = await decryptValue(encoded, env);
  if (!isRecord(value) || typeof value.accountId !== 'string' ||
      typeof value.accessToken !== 'string' || typeof value.refreshToken !== 'string' ||
      typeof value.expiresAt !== 'number' ||
      typeof value.accessTokenExpiresAt !== 'number' ||
      !Number.isFinite(value.expiresAt) ||
      !Number.isFinite(value.accessTokenExpiresAt) ||
      typeof value.state !== 'string' ||
      typeof value.nonce !== 'string') {
    throw new BrokerError('ticket_invalid', 401);
  }
  return value as OAuthTicket;
}

async function decryptValue(encoded: string, env: Env): Promise<unknown> {
  const bytes = fromBase64Url(encoded);
  if (bytes.length < 28) throw new BrokerError('ticket_invalid', 401);
  const key = await keyFromEnv(env);
  const iv = bytes.slice(0, 12);
  const ciphertext = bytes.slice(12);
  let plaintext: ArrayBuffer;
  try {
    plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, ciphertext);
  } catch (_) {
    throw new BrokerError('ticket_invalid', 401);
  }
  try {
    return JSON.parse(new TextDecoder().decode(plaintext));
  } catch (_) {
    throw new BrokerError('ticket_invalid', 401);
  }
}

async function keyFromEnv(env: Env): Promise<CryptoKey> {
  const key = new Uint8Array(fromBase64Url(env.TICKET_KEY_B64));
  if (key.length !== 32) throw new BrokerError('broker_key_invalid', 500);
  return crypto.subtle.importKey('raw', key.buffer as ArrayBuffer, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']);
}

async function claimReplay(nonce: string, env: Env): Promise<void> {
  const id = env.REPLAY_MARKERS.idFromName(nonce);
  const response = await env.REPLAY_MARKERS.get(id).fetch('https://replay/claim', {
    method: 'POST',
    body: nonce,
  });
  if (response.status === 409) throw new BrokerError('ticket_replay', 409);
  if (response.status < 200 || response.status >= 300) {
    throw new BrokerError('replay_marker_failed', 500);
  }
}

async function readJson(request: Request): Promise<Record<string, unknown>> {
  const body = await boundedText(request);
  const value: unknown = JSON.parse(body);
  if (!isRecord(value)) throw new BrokerError('invalid_request', 400);
  return value;
}

async function boundedText(response: Response | Request): Promise<string> {
  const length = response.headers.get('content-length');
  if (length != null && Number(length) > MAX_BODY_BYTES) throw new BrokerError('body_too_large', 413);
  const text = await response.text();
  if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) throw new BrokerError('body_too_large', 413);
  return text;
}

function stringField(body: Record<string, unknown>, key: string, max: number): string {
  const value = body[key];
  if (typeof value !== 'string' || value.length === 0 || value.length > max) {
    throw new BrokerError(`invalid_${key}`, 400);
  }
  return value;
}

function isRecord(value: unknown): value is Record<string, any> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isSafeState(value: string): boolean {
  return /^[A-Za-z0-9_-]{43,256}$/.test(value);
}

function isSafeRedirectUri(value: string, env: Env): boolean {
  try {
    const uri = new URL(value);
    const isLoopbackCallback = uri.pathname === PROVIDER_CALLBACK_PATH;
    const isAndroidAppCallback = uri.pathname === ANDROID_APP_CALLBACK_PATH;
    return (
      uri.search === '' &&
      uri.hash === '' &&
      ((uri.protocol === 'http:' &&
        uri.hostname === '127.0.0.1' &&
        uri.port !== '' &&
        isLoopbackCallback) ||
        (uri.protocol === 'https:' &&
          uri.hostname === (env.APP_LINK_HOST ?? '') &&
          uri.port === '' &&
          isAndroidAppCallback))
    );
  } catch (_) {
    return false;
  }
}

function randomToken(bytes: number): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(bytes)));
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}

function fromBase64Url(value: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]{20,16384}$/.test(value)) throw new BrokerError('ticket_invalid', 401);
  const padding = (4 - (value.length % 4)) % 4;
  const padded = value.replaceAll('-', '+').replaceAll('_', '/') + '='.repeat(padding);
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function assetLinks(env: Env): Response {
  const packageName = env.ANDROID_PACKAGE_NAME;
  const fingerprint = env.ANDROID_CERT_SHA256;
  if (packageName == null || fingerprint == null) return json([]);
  if (!/^[A-Za-z0-9_.]+$/.test(packageName) ||
      !/^[0-9A-Fa-f:]{32,128}$/.test(fingerprint)) {
    throw new BrokerError('asset_links_config_invalid', 500);
  }
  return json([
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: packageName,
        sha256_cert_fingerprints: [fingerprint.toUpperCase()],
      },
    },
  ]);
}

function redirectWithError(redirectUri: string, state: string, error: string): Response {
  const target = new URL(redirectUri);
  target.searchParams.set('state', state);
  target.searchParams.set('error', error);
  return Response.redirect(target.toString(), 302);
}

function safeOAuthFailureCode(error: unknown): string {
  if (error instanceof BrokerError) {
    switch (error.code) {
      case 'oauth_provider_rejected':
      case 'oauth_payload_invalid':
      case 'bangumi_identity_failed':
      case 'bangumi_identity_invalid':
        return error.code;
    }
  }
  return 'oauth_exchange_failed';
}

function androidAppCallback(): Response {
  return new Response(
    '<!doctype html><title>Wynime</title>' +
      '<p>請返回 Wynime 完成 Bangumi 登入。</p>',
    {
      status: 200,
      headers: {
        'content-type': 'text/html; charset=utf-8',
        'cache-control': 'no-store',
      },
    },
  );
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });
}

class BrokerError extends Error {
  constructor(public readonly code: string, public readonly status: number) {
    super(code);
  }
}

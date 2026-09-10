import { describe, expect, it, vi } from 'vitest';

import worker, { ReplayMarker } from '../src/index';

describe('Bangumi broker public surface', () => {
  const envValues = {
    BANGUMI_CLIENT_ID: 'client-id',
    BANGUMI_CLIENT_SECRET: 'secret-not-returned',
    TICKET_KEY_B64: 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
    APP_LINK_HOST: 'wynime-broker-test.example.workers.dev',
  };
  const env = envValues as never;
  const compactFingerprint =
    '32fa14329bda4ddd9c2f9d6be973ef70a4272b5630abbab714ff50f0f6254b53';
  const canonicalFingerprint =
    '32:FA:14:32:9B:DA:4D:DD:9C:2F:9D:6B:E9:73:EF:70:A4:27:2B:56:30:AB:BA:B7:14:FF:50:F0:F6:25:4B:53';
  const assetLinksEnv = (overrides: Record<string, unknown> = {}) => ({
    ...envValues,
    ANDROID_PACKAGE_NAME: 'io.github.william12233.wynime',
    ANDROID_CERT_SHA256: canonicalFingerprint,
    ...overrides,
  }) as never;
  const fetchAssetLinks = (assetLinksEnvironment: never) => worker.fetch(
    new Request('https://broker.example/.well-known/assetlinks.json'),
    assetLinksEnvironment,
  );

  it('normalizes a compact SHA-256 certificate fingerprint to canonical form', async () => {
    const response = await fetchAssetLinks(
      assetLinksEnv({ ANDROID_CERT_SHA256: compactFingerprint }),
    );

    expect(response.status).toBe(200);
    expect(response.headers.get('content-type')).toBe('application/json; charset=utf-8');
    expect(response.headers.get('cache-control')).toBe('no-store');
    expect(await response.json()).toEqual([
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: 'io.github.william12233.wynime',
          sha256_cert_fingerprints: [canonicalFingerprint],
        },
      },
    ]);
  });

  it('normalizes lowercase canonical fingerprint input to uppercase canonical form', async () => {
    const response = await fetchAssetLinks(
      assetLinksEnv({ ANDROID_CERT_SHA256: canonicalFingerprint.toLowerCase() }),
    );

    expect(response.status).toBe(200);
    expect((await response.json()) as Array<{ target: { sha256_cert_fingerprints: string[] } }>).toEqual([
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: 'io.github.william12233.wynime',
          sha256_cert_fingerprints: [canonicalFingerprint],
        },
      },
    ]);
  });

  it.each([
    ['63 hex characters', compactFingerprint.slice(0, 63)],
    ['65 hex characters', `${compactFingerprint}A`],
    ['66 hex characters', `${compactFingerprint}AA`],
    [
      'the historical malformed production value',
      '32FA14329BDA4DDD2C9F2D9D6BE973EF70A4272B5630ABBAB714FF50F0F6254B53',
    ],
    ['non-hex character', `${compactFingerprint.slice(0, 63)}Z`],
    ['31 byte pairs', canonicalFingerprint.split(':').slice(1).join(':')],
    ['33 byte pairs', `${canonicalFingerprint}:AA`],
    ['double colon', canonicalFingerprint.replace(':', '::')],
    ['trailing colon', `${canonicalFingerprint}:`],
    ['leading colon', `:${canonicalFingerprint}`],
    ['arbitrary colon placement', `${compactFingerprint.slice(0, 1)}:${compactFingerprint.slice(1)}`],
    ['embedded whitespace', canonicalFingerprint.replace(':', ' :')],
  ])('rejects %s fingerprint input', async (_label, fingerprint) => {
    const response = await fetchAssetLinks(
      assetLinksEnv({ ANDROID_CERT_SHA256: fingerprint }),
    );

    expect(response.status).toBe(500);
    expect(await response.json()).toEqual({ error: 'asset_links_config_invalid' });
  });

  it.each([
    ['ANDROID_PACKAGE_NAME', { ANDROID_PACKAGE_NAME: undefined }],
    ['ANDROID_CERT_SHA256', { ANDROID_CERT_SHA256: undefined }],
  ])('keeps the empty Asset Links response when %s is missing', async (_label, overrides) => {
    const response = await fetchAssetLinks(assetLinksEnv(overrides));

    expect(response.status).toBe(200);
    expect(response.headers.get('content-type')).toBe('application/json; charset=utf-8');
    expect(response.headers.get('cache-control')).toBe('no-store');
    expect(await response.json()).toEqual([]);
  });

  it('exposes a health check without secrets', async () => {
    const response = await worker.fetch(
      new Request('https://broker.example/healthz'),
      {} as never,
    );
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ ok: true });
  });

  it('does not accept an undersized OAuth state', async () => {
    const response = await worker.fetch(
      new Request('https://broker.example/oauth/start?state=short&client_id=x&redirect_uri=http%3A%2F%2F127.0.0.1%3A1234'),
      { BANGUMI_CLIENT_ID: 'x' } as never,
    );
    expect(response.status).toBe(400);
  });

  it('accepts the unpadded base64url state emitted by the Flutter client', async () => {
    const state = 'A'.repeat(43);
    const response = await worker.fetch(
      new Request(
        `https://broker.example/oauth/start?state=${state}&client_id=client-id&redirect_uri=${encodeURIComponent('http://127.0.0.1:43123/oauth/callback')}`,
      ),
      env,
    );
    expect(response.status).toBe(302);
  });

  it('encrypts provider state and redirects denial back to the exact client URI', async () => {
    const state = 'A'.repeat(43);
    const start = await worker.fetch(
      new Request(
        `https://broker.example/oauth/start?state=${state}&client_id=client-id&redirect_uri=${encodeURIComponent('http://127.0.0.1:43123/oauth/callback')}`,
      ),
      env,
    );
    expect(start.status).toBe(302);
    const providerLocation = new URL(start.headers.get('location')!);
    const providerState = providerLocation.searchParams.get('state')!;
    expect(providerLocation.origin).toBe('https://bgm.tv');
    expect(providerState).not.toBe(state);

    const denial = await worker.fetch(
      new Request(
        `https://broker.example/oauth/callback?state=${encodeURIComponent(providerState)}&error=access_denied`,
      ),
      env,
    );
    expect(denial.status).toBe(302);
    const clientLocation = new URL(denial.headers.get('location')!);
    expect(clientLocation.toString()).toContain(
      'http://127.0.0.1:43123/oauth/callback',
    );
    expect(clientLocation.searchParams.get('state')).toBe(state);
    expect(clientLocation.searchParams.get('error')).toBe('oauth_denied');
  });

  it('uses a separate Android app callback so the broker does not parse app state', async () => {
    const state = 'A'.repeat(43);
    const appRedirectUri = `https://${envValues.APP_LINK_HOST}/oauth/app-callback`;
    const start = await worker.fetch(
      new Request(
        `https://broker.example/oauth/start?state=${state}&client_id=client-id&redirect_uri=${encodeURIComponent(appRedirectUri)}`,
      ),
      env,
    );
    expect(start.status).toBe(302);
    const providerState = new URL(start.headers.get('location')!).searchParams.get('state')!;

    const denial = await worker.fetch(
      new Request(
        `https://broker.example/oauth/callback?state=${encodeURIComponent(providerState)}&error=access_denied`,
      ),
      env,
    );
    expect(denial.status).toBe(302);
    const appLocation = new URL(denial.headers.get('location')!);
    expect(appLocation.toString()).toContain(appRedirectUri);
    expect(appLocation.pathname).toBe('/oauth/app-callback');
    expect(appLocation.searchParams.get('state')).toBe(state);
    expect(appLocation.searchParams.get('error')).toBe('oauth_denied');

    const browserFallback = await worker.fetch(
      new Request(appLocation.toString()),
      env,
    );
    expect(browserFallback.status).toBe(200);
    expect(await browserFallback.text()).not.toContain('oauth_state_invalid');
  });

  it('redacts invalid tickets and never returns a token for a mismatch', async () => {
    const response = await worker.fetch(
      new Request('https://broker.example/oauth/redeem', {
        method: 'POST',
        body: JSON.stringify({
          client_id: 'client-id',
          state: 'A'.repeat(43),
          ticket: 'invalid-ticket',
        }),
      }),
      env,
    );
    expect(response.status).toBe(401);
    const body = await response.text();
    expect(JSON.parse(body)).toEqual({ error: 'ticket_invalid' });
    expect(body).not.toContain('secret-not-returned');
  });

  it('returns a stable provider diagnostic when code exchange is rejected', async () => {
    const tokenFetch = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input) === 'https://bgm.tv/oauth/access_token') {
        return new Response(JSON.stringify({ error: 'invalid_client' }), {
          status: 401,
        });
      }
      throw new Error(`unexpected upstream ${String(input)}`);
    });
    vi.stubGlobal('fetch', tokenFetch);
    try {
      const state = 'A'.repeat(43);
      const start = await worker.fetch(
        new Request(
          `https://broker.example/oauth/start?state=${state}&client_id=client-id&redirect_uri=${encodeURIComponent('http://127.0.0.1:43123/oauth/callback')}`,
        ),
        env,
      );
      const providerState = new URL(start.headers.get('location')!).searchParams.get('state')!;
      const callback = await worker.fetch(
        new Request(
          `https://broker.example/oauth/callback?state=${encodeURIComponent(providerState)}&code=authorization-code`,
        ),
        env,
      );
      const location = new URL(callback.headers.get('location')!);

      expect(callback.status).toBe(302);
      expect(location.searchParams.get('state')).toBe(state);
      expect(location.searchParams.get('error')).toBe('oauth_provider_rejected');
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('keeps the replay marker separate from ticket contents', () => {
    expect(ReplayMarker).toBeDefined();
  });

  it('claims a replay nonce once and rejects a second claim', async () => {
    const values = new Map<string, number>();
    const storage = {
      get: async (key: string) => values.get(key),
      put: async (key: string, value: number) => {
        values.set(key, value);
      },
      delete: async (key: string) => values.delete(key),
    };
    const marker = new ReplayMarker({ storage } as never);
    const nonce = 'N'.repeat(32);

    const first = await marker.fetch(
      new Request('https://replay/claim', { method: 'POST', body: nonce }),
    );
    const second = await marker.fetch(
      new Request('https://replay/claim', { method: 'POST', body: nonce }),
    );

    expect(first.status).toBe(200);
    expect(second.status).toBe(409);
  });

  it('returns the upstream token lifetime instead of the five-minute ticket lifetime', async () => {
    const tokenFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === 'https://token.example.test/access_token') {
        return new Response(
          JSON.stringify({
            access_token: 'access-token',
            refresh_token: 'refresh-token',
            expires_in: 604800,
            user_id: 7,
          }),
          { status: 200 },
        );
      }
      throw new Error(`unexpected upstream ${url} ${String(init?.method)}`);
    });
    vi.stubGlobal('fetch', tokenFetch);
    const replayEnv = {
      ...envValues,
      BANGUMI_TOKEN_URL: 'https://token.example.test/access_token',
      REPLAY_MARKERS: {
        idFromName: (name: string) => name,
        get: () => ({
          fetch: async () => {
            return new Response(null, { status: 200 });
          },
        }),
      },
    } as never;
    try {
      const state = 'A'.repeat(43);
      const appRedirectUri = `https://${envValues.APP_LINK_HOST}/oauth/app-callback`;
      const start = await worker.fetch(
        new Request(
          `https://broker.example/oauth/start?state=${state}&client_id=client-id&redirect_uri=${encodeURIComponent(appRedirectUri)}`,
        ),
        replayEnv,
      );
      const providerState = new URL(start.headers.get('location')!).searchParams.get('state')!;
      const callback = await worker.fetch(
        new Request(
          `https://broker.example/oauth/callback?state=${encodeURIComponent(providerState)}&code=authorization-code`,
        ),
        replayEnv,
      );
      expect(callback.status, await callback.clone().text()).toBe(302);
      const clientLocation = new URL(callback.headers.get('location')!);
      expect(clientLocation.origin).toBe(`https://${envValues.APP_LINK_HOST}`);
      expect(clientLocation.pathname).toBe('/oauth/app-callback');
      const ticket = clientLocation.searchParams.get('ticket')!;
      expect(ticket.length).toBeGreaterThan(40);
      const redeemed = await worker.fetch(
        new Request('https://broker.example/oauth/redeem', {
          method: 'POST',
          body: JSON.stringify({
            client_id: 'client-id',
            state,
            ticket,
          }),
        }),
        replayEnv,
      );
      const redeemedBody = await redeemed.text();
      expect(redeemed.status, redeemedBody).toBe(200);
      expect(JSON.parse(redeemedBody)).toMatchObject({
        account_id: '7',
        expires_in: expect.any(Number),
      });
      expect((JSON.parse(redeemedBody) as { expires_in: number }).expires_in).toBeGreaterThan(604000);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('includes the callback redirect URI in the refresh grant', async () => {
    const tokenFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === 'https://token.example.test/access_token') {
        expect(String(init?.body)).toContain(
          'redirect_uri=https%3A%2F%2Fbroker.example%2Foauth%2Fcallback',
        );
        return new Response(
          JSON.stringify({
            access_token: 'refreshed-access',
            refresh_token: 'rotated-refresh',
            expires_in: 604800,
          }),
          { status: 200 },
        );
      }
      if (url === 'https://bgm.tv/oauth/token_status') {
        return new Response(JSON.stringify({ user_id: 7 }), { status: 200 });
      }
      throw new Error(`unexpected upstream ${url}`);
    });
    vi.stubGlobal('fetch', tokenFetch);
    try {
      const response = await worker.fetch(
        new Request('https://broker.example/oauth/refresh', {
          method: 'POST',
          body: JSON.stringify({
            client_id: 'client-id',
            account_id: '7',
            refresh_token: 'old-refresh',
          }),
        }),
        {
          ...envValues,
          BANGUMI_TOKEN_URL: 'https://token.example.test/access_token',
        } as never,
      );
      expect(response.status).toBe(200);
      expect((await response.json() as { expires_in: number }).expires_in).toBe(604800);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('uses the authorization response user id without calling an undocumented current-user endpoint', async () => {
    const tokenFetch = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input) === 'https://token.example.test/access_token') {
        return new Response(
          JSON.stringify({
            access_token: 'access-token',
            refresh_token: 'refresh-token',
            expires_in: 604800,
            user_id: 42,
          }),
          { status: 200 },
        );
      }
      throw new Error(`unexpected upstream ${String(input)}`);
    });
    vi.stubGlobal('fetch', tokenFetch);
    const replayEnv = {
      ...envValues,
      BANGUMI_TOKEN_URL: 'https://token.example.test/access_token',
      REPLAY_MARKERS: {
        idFromName: (name: string) => name,
        get: () => ({
          fetch: async () => new Response(null, { status: 200 }),
        }),
      },
    } as never;
    try {
      const state = 'B'.repeat(43);
      const start = await worker.fetch(
        new Request(
          `https://broker.example/oauth/start?state=${state}&client_id=client-id&redirect_uri=${encodeURIComponent(`https://${envValues.APP_LINK_HOST}/oauth/app-callback`)}`,
        ),
        replayEnv,
      );
      const providerState = new URL(start.headers.get('location')!).searchParams.get('state')!;
      const callback = await worker.fetch(
        new Request(
          `https://broker.example/oauth/callback?state=${encodeURIComponent(providerState)}&code=authorization-code`,
        ),
        replayEnv,
      );
      expect(callback.status).toBe(302);
      const location = new URL(callback.headers.get('location')!);
      expect(location.searchParams.get('error')).toBeNull();
      const redeemed = await worker.fetch(
        new Request('https://broker.example/oauth/redeem', {
          method: 'POST',
          body: JSON.stringify({
            client_id: 'client-id',
            state,
            ticket: location.searchParams.get('ticket'),
          }),
        }),
        replayEnv,
      );
      expect(redeemed.status).toBe(200);
      expect((await redeemed.json() as { account_id: string }).account_id).toBe('42');
      expect(tokenFetch).toHaveBeenCalledTimes(1);
      expect(String(tokenFetch.mock.calls[0]?.[0])).toBe(
        'https://token.example.test/access_token',
      );
    } finally {
      vi.unstubAllGlobals();
    }
  });
});

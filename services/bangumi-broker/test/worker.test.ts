import { describe, expect, it, vi } from 'vitest';

import worker, { ReplayMarker } from '../src/index';

describe('Bangumi broker public surface', () => {
  const envValues = {
    BANGUMI_CLIENT_ID: 'client-id',
    BANGUMI_CLIENT_SECRET: 'secret-not-returned',
    TICKET_KEY_B64: 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
    APP_LINK_HOST: 'auth.wynime.app',
  };
  const env = envValues as never;

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
          }),
          { status: 200 },
        );
      }
      if (url === 'https://api.bgm.tv/v0/me') {
        return new Response(JSON.stringify({ id: 7 }), { status: 200 });
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
      const start = await worker.fetch(
        new Request(
          `https://broker.example/oauth/start?state=${state}&client_id=client-id&redirect_uri=${encodeURIComponent('http://127.0.0.1:43123/oauth/callback')}`,
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
      if (url === 'https://api.bgm.tv/v0/me') {
        return new Response(JSON.stringify({ id: 7 }), { status: 200 });
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
});

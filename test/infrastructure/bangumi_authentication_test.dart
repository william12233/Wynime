import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_authentication.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_api_client.dart';

void main() {
  final redirectUri = Uri.parse('http://127.0.0.1:43123/oauth/callback');
  const brokerHost = 'wynime-broker-test.example.workers.dev';
  final workerOrigin = Uri.parse('https://$brokerHost');

  test('begin creates a high-entropy state and fixed safe callback', () async {
    final transport = AuthTransport();
    final authentication = BangumiBrokerAuthentication(
      workerOrigin: workerOrigin,
      clientId: 'wynime-client',
      verifiedAppLinkHost: brokerHost,
      redirectUri: redirectUri,
      transport: transport,
    );

    final request = await authentication.begin();

    expect(request.redirectUri, redirectUri);
    expect(request.state.length, greaterThanOrEqualTo(40));
    expect(request.state, matches(RegExp(r'^[A-Za-z0-9_-]+$')));
    expect(request.state, isNot(contains('=')));
    expect(request.authorizationUri.host, brokerHost);
    expect(request.authorizationUri.path, '/oauth/start');
    expect(request.authorizationUri.queryParameters['state'], request.state);
    expect(
      request.authorizationUri.queryParameters['redirect_uri'],
      redirectUri.toString(),
    );
    expect(
      request.authorizationUri.queryParameters['client_id'],
      'wynime-client',
    );
  });

  test('redeem validates state and keeps tokens out of diagnostics', () async {
    final transport = AuthTransport(
      responseBody: {
        'account_id': '7',
        'access_token': 'access-secret',
        'refresh_token': 'refresh-secret',
        'expires_in': 604800,
      },
    );
    final authentication = BangumiBrokerAuthentication(
      workerOrigin: workerOrigin,
      clientId: 'wynime-client',
      verifiedAppLinkHost: brokerHost,
      redirectUri: redirectUri,
      transport: transport,
    );
    final request = await authentication.begin();

    final session = await authentication.redeem(
      BangumiAuthCallback(state: request.state, ticket: 'one-time-ticket'),
    );

    expect(session.accountId, '7');
    expect(
      session.expiresAt.difference(DateTime.now().toUtc()),
      greaterThan(const Duration(days: 6)),
    );
    expect(session.toString(), isNot(contains('access-secret')));
    expect(session.toString(), isNot(contains('refresh-secret')));
    expect(transport.calls.single.body, contains('one-time-ticket'));
    expect(transport.calls.single.body, contains(request.state));
  });

  test(
    'persists only the refresh session and rotates it after refresh',
    () async {
      final store = _FakeRefreshTokenStore();
      final transport = AuthTransport(
        responseBody: {
          'account_id': '7',
          'access_token': 'access-secret',
          'refresh_token': 'refresh-secret',
          'expires_in': 604800,
        },
      );
      final authentication = BangumiBrokerAuthentication(
        workerOrigin: workerOrigin,
        clientId: 'wynime-client',
        verifiedAppLinkHost: brokerHost,
        redirectUri: redirectUri,
        refreshTokenStore: store,
        transport: transport,
      );
      final request = await authentication.begin();

      await authentication.redeem(
        BangumiAuthCallback(state: request.state, ticket: 'one-time-ticket'),
      );
      expect(store.value?.accountId, '7');
      expect(store.value?.refreshToken, 'refresh-secret');

      final restored = await authentication.restoreSession();
      expect(restored?.accountId, '7');
      expect(restored?.accessToken, isEmpty);
      expect(restored?.refreshToken, 'refresh-secret');

      transport.responseBody = {
        'account_id': '7',
        'access_token': 'new-access',
        'refresh_token': 'rotated-refresh',
        'expires_in': 3600,
      };
      final refreshed = await authentication.refresh(restored!);
      expect(refreshed.accessToken, 'new-access');
      expect(store.value?.refreshToken, 'rotated-refresh');

      await authentication.signOut();
      expect(store.value, isNull);
    },
  );

  test(
    'preserves broker-prefixed OAuth diagnostics without double prefixing',
    () async {
      final authentication = BangumiBrokerAuthentication(
        workerOrigin: workerOrigin,
        clientId: 'wynime-client',
        verifiedAppLinkHost: brokerHost,
        redirectUri: redirectUri,
        transport: AuthTransport(),
      );
      final request = await authentication.begin();

      expect(
        () => authentication.redeem(
          BangumiAuthCallback(
            state: request.state,
            error: 'oauth_provider_rejected',
          ),
        ),
        throwsA(
          isA<BangumiApiException>().having(
            (error) => error.code,
            'code',
            'oauth_provider_rejected',
          ),
        ),
      );
    },
  );

  test(
    'redeem survives an Android-style isolate restart with persisted state',
    () async {
      final transport = AuthTransport(
        responseBody: {
          'account_id': '7',
          'access_token': 'access-secret',
          'refresh_token': 'refresh-secret',
          'expires_in': 604800,
        },
      );
      final stateStore = _FakeOAuthStateStore();
      final callbackPort = _FakeCallbackPort(
        Uri.https(brokerHost, '/oauth/app-callback'),
      );
      final firstAuthentication = BangumiBrokerAuthentication(
        workerOrigin: workerOrigin,
        clientId: 'wynime-client',
        verifiedAppLinkHost: brokerHost,
        callbackPort: callbackPort,
        oauthStateStore: stateStore,
        transport: transport,
      );
      final request = await firstAuthentication.begin();

      // A new authentication object models the new Dart isolate created after
      // Android killed the app while the browser was open.
      final recreatedAuthentication = BangumiBrokerAuthentication(
        workerOrigin: workerOrigin,
        clientId: 'wynime-client',
        verifiedAppLinkHost: brokerHost,
        callbackPort: callbackPort,
        oauthStateStore: stateStore,
        transport: transport,
      );
      final session = await recreatedAuthentication.redeem(
        BangumiAuthCallback(state: request.state, ticket: 'one-time-ticket'),
      );

      expect(session.accountId, '7');
      expect(stateStore.pending, isNull);
      expect(callbackPort.prepareCalls, 2);
      expect(transport.calls.single.body, contains(request.state));
    },
  );

  test(
    'rejects mismatched and expired state before contacting the worker',
    () async {
      var now = DateTime.utc(2026, 9, 8, 12);
      final transport = AuthTransport();
      final authentication = BangumiBrokerAuthentication(
        workerOrigin: workerOrigin,
        clientId: 'wynime-client',
        verifiedAppLinkHost: brokerHost,
        redirectUri: redirectUri,
        transport: transport,
        clock: () => now,
        ticketLifetime: const Duration(minutes: 5),
      );
      final request = await authentication.begin();

      expect(
        () => authentication.redeem(
          BangumiAuthCallback(state: 'wrong', ticket: 'ticket'),
        ),
        throwsA(
          isA<BangumiApiException>().having(
            (error) => error.code,
            'code',
            'oauth_state_mismatch',
          ),
        ),
      );
      expect(transport.calls, isEmpty);

      final second = await authentication.begin();
      now = now.add(const Duration(minutes: 6));
      expect(
        () => authentication.redeem(
          BangumiAuthCallback(state: second.state, ticket: 'ticket'),
        ),
        throwsA(
          isA<BangumiApiException>().having(
            (error) => error.code,
            'code',
            'oauth_state_mismatch',
          ),
        ),
      );
      expect(request.state, isNot(second.state));
      expect(transport.calls, isEmpty);
    },
  );

  test('refresh rejects account rotation and provider denial', () async {
    final transport = AuthTransport(
      responseBody: {
        'account_id': '8',
        'access_token': 'new-access',
        'refresh_token': 'new-refresh',
        'expires_in': 3600,
      },
    );
    final authentication = BangumiBrokerAuthentication(
      workerOrigin: workerOrigin,
      clientId: 'wynime-client',
      verifiedAppLinkHost: brokerHost,
      redirectUri: redirectUri,
      transport: transport,
    );
    final session = BangumiAuthSession(
      accountId: '7',
      accessToken: 'old-access',
      refreshToken: 'old-refresh',
      expiresAt: DateTime.utc(2030),
    );

    expect(
      () => authentication.refresh(session),
      throwsA(
        isA<BangumiApiException>().having(
          (error) => error.code,
          'code',
          'oauth_account_mismatch',
        ),
      ),
    );

    transport.responseStatus = 403;
    expect(
      () => authentication.refresh(session),
      throwsA(
        isA<BangumiApiException>().having(
          (error) => error.code,
          'code',
          'oauth_rejected',
        ),
      ),
    );
  });

  test(
    'accepts the configured app-link return path and rejects unsafe variants',
    () async {
      final authentication = BangumiBrokerAuthentication(
        workerOrigin: workerOrigin,
        clientId: 'wynime-client',
        verifiedAppLinkHost: brokerHost,
        redirectUri: Uri.https(brokerHost, '/oauth/app-callback'),
        transport: AuthTransport(),
      );

      final request = await authentication.begin();
      expect(request.redirectUri, Uri.https(brokerHost, '/oauth/app-callback'));

      expect(
        () => BangumiBrokerAuthentication(
          workerOrigin: workerOrigin,
          clientId: 'wynime-client',
          verifiedAppLinkHost: brokerHost,
          redirectUri: Uri.https(brokerHost, '/oauth/callback'),
          transport: AuthTransport(),
        ),
        throwsA(isA<ArgumentError>()),
      );
      expect(
        () => BangumiBrokerAuthentication(
          workerOrigin: workerOrigin,
          clientId: 'wynime-client',
          verifiedAppLinkHost: brokerHost,
          redirectUri: Uri.https(
            'other.example.workers.dev',
            '/oauth/app-callback',
          ),
          transport: AuthTransport(),
        ),
        throwsA(isA<ArgumentError>()),
      );
    },
  );
}

final class AuthTransport implements BangumiHttpTransport {
  AuthTransport({this.responseBody, this.responseStatus = 200});

  Map<String, Object?>? responseBody;
  int responseStatus;
  final List<AuthCall> calls = <AuthCall>[];

  @override
  Future<BangumiHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
    required int maxResponseBytes,
  }) async {
    calls.add(AuthCall(method: method, uri: uri, body: body));
    return BangumiHttpResponse(
      statusCode: responseStatus,
      body: jsonEncode(responseBody ?? <String, Object?>{}),
    );
  }
}

final class AuthCall {
  const AuthCall({required this.method, required this.uri, required this.body});

  final String method;
  final Uri uri;
  final String? body;
}

final class _FakeOAuthStateStore implements BangumiOAuthStateStore {
  BangumiPendingOAuthState? pending;

  @override
  Future<void> savePendingState({
    required String state,
    required DateTime createdAt,
  }) async {
    pending = BangumiPendingOAuthState(state: state, createdAt: createdAt);
  }

  @override
  Future<BangumiPendingOAuthState?> loadPendingState() async => pending;

  @override
  Future<void> clearPendingState() async {
    pending = null;
  }
}

final class _FakeRefreshTokenStore implements BangumiRefreshTokenStore {
  BangumiStoredRefreshToken? value;

  @override
  Future<void> saveRefreshToken({
    required String accountId,
    required String refreshToken,
  }) async {
    value = BangumiStoredRefreshToken(
      accountId: accountId,
      refreshToken: refreshToken,
    );
  }

  @override
  Future<BangumiStoredRefreshToken?> loadRefreshToken() async => value;

  @override
  Future<void> clearRefreshToken() async {
    value = null;
  }
}

final class _FakeCallbackPort implements BangumiCallbackPort {
  _FakeCallbackPort(this.redirectUri);

  final Uri redirectUri;
  int prepareCalls = 0;

  @override
  Future<Uri> prepareRedirectUri() async {
    prepareCalls++;
    return redirectUri;
  }

  @override
  Future<BangumiAuthCallback> waitForCallback() => throw UnimplementedError();

  @override
  Future<void> close() async {}
}

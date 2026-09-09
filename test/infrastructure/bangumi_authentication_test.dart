import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
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
    'accepts the configured app-link host and rejects a sibling host',
    () async {
      final authentication = BangumiBrokerAuthentication(
        workerOrigin: workerOrigin,
        clientId: 'wynime-client',
        verifiedAppLinkHost: brokerHost,
        redirectUri: Uri.https(brokerHost, '/oauth/callback'),
        transport: AuthTransport(),
      );

      final request = await authentication.begin();
      expect(request.redirectUri, Uri.https(brokerHost, '/oauth/callback'));

      expect(
        () => BangumiBrokerAuthentication(
          workerOrigin: workerOrigin,
          clientId: 'wynime-client',
          verifiedAppLinkHost: brokerHost,
          redirectUri: Uri.https(
            'other.example.workers.dev',
            '/oauth/callback',
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

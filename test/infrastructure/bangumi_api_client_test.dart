import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_api_client.dart';

void main() {
  final session = BangumiAuthSession(
    accountId: '7',
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresAt: DateTime.utc(2030),
  );

  test('reads identity, paged collections and paged episodes', () async {
    final transport = RecordingBangumiTransport((method, uri, headers, body) {
      expect(method, 'GET');
      expect(headers['Authorization'], 'Bearer access-token');
      expect(headers.containsKey('Cookie'), isFalse);
      switch (uri.path) {
        case '/v0/me':
          return _jsonResponse({
            'id': 7,
            'username': 'alice',
            'nickname': 'Alice',
            'avatar': {
              'large': 'https://lain.bgm.tv/pic/user/l/7.jpg',
              'medium': 'https://lain.bgm.tv/pic/user/m/7.jpg',
            },
          });
        case '/v0/users/alice/collections':
          expect(uri.queryParameters['subject_type'], '2');
          expect(uri.queryParameters['limit'], '2');
          expect(uri.queryParameters['offset'], '0');
          return _jsonResponse({
            'total': 2,
            'data': [
              {
                'subject_id': 42,
                'type': 1,
                'subject': {'name': 'Title', 'name_cn': '作品'},
              },
              {'subject_id': 43, 'type': 3},
            ],
          });
        case '/v0/episodes':
          expect(uri.queryParameters['subject_id'], '42');
          expect(uri.queryParameters['limit'], '100');
          return _jsonResponse({
            'total': 2,
            'data': [
              {
                'id': 1001,
                'subject_id': 42,
                'name': 'Episode 1',
                'name_cn': '第一集',
                'sort': 1,
                'type': 0,
              },
              {
                'id': 1002,
                'subject_id': 42,
                'name': 'Episode 2',
                'name_cn': '第二集',
                'sort': 2,
                'type': 0,
              },
            ],
          });
        default:
          return _jsonResponse({}, statusCode: 500);
      }
    });
    final client = BangumiApiClient(
      sessionProvider: () => session,
      transport: transport,
    );

    final identity = await client.currentUser();
    final collections = await client.collections(limit: 2);
    final episodes = await client.episodes('42');

    expect(identity.id, '7');
    expect(identity.username, 'alice');
    expect(
      identity.avatarUrl,
      Uri.parse('https://lain.bgm.tv/pic/user/l/7.jpg'),
    );
    expect(collections.collections, hasLength(2));
    expect(collections.collections.first.status, BangumiCollectionStatus.wish);
    expect(episodes.episodes, hasLength(2));
    expect(episodes.episodes.last.nameCn, '第二集');
  });

  test(
    'reads official calendar items whose subject fields are at item root',
    () async {
      final transport = RecordingBangumiTransport((method, uri, headers, body) {
        expect(uri.path, '/calendar');
        return _jsonResponse([
          {
            'weekday': {'id': 1},
            'items': [
              {
                'id': 42,
                'name': 'Title',
                'name_cn': '作品',
                'images': {'common': 'https://lain.bgm.tv/pic/cover/c/42.jpg'},
              },
            ],
          },
        ]);
      });
      final client = BangumiApiClient(
        sessionProvider: () => session,
        transport: transport,
      );

      final entries = await client.calendar();

      expect(entries, hasLength(1));
      expect(entries.single.subjectId, '42');
      expect(entries.single.subjectName, '作品');
      expect(
        entries.single.imageUrl,
        Uri.parse('https://lain.bgm.tv/pic/cover/c/42.jpg'),
      );
    },
  );

  test('uses official mutation paths and official watched values', () async {
    final transport = RecordingBangumiTransport((method, uri, headers, body) {
      if (uri.path == '/v0/me') {
        return _jsonResponse({'id': 7, 'username': 'alice'});
      }
      if (method == 'POST' && uri.path == '/v0/users/-/collections/42') {
        expect(jsonDecode(body!), {'type': 3});
        return _emptyResponse();
      }
      if (method == 'PUT' &&
          uri.path == '/v0/users/-/collections/-/episodes/1001') {
        expect(jsonDecode(body!), {'type': 2});
        return _emptyResponse();
      }
      return _jsonResponse({}, statusCode: 404);
    });
    final client = BangumiApiClient(
      sessionProvider: () => session,
      transport: transport,
    );

    await client.currentUser();
    await client.setCollectionStatus('42', BangumiCollectionStatus.watching);
    await client.setEpisodeWatched('42', '1001', true);

    expect(transport.calls.map((call) => call.uri.path), [
      '/v0/me',
      '/v0/users/-/collections/42',
      '/v0/users/-/collections/-/episodes/1001',
    ]);
  });

  test(
    'builds remote state from collection and watched episode pages',
    () async {
      final transport = RecordingBangumiTransport((method, uri, headers, body) {
        switch (uri.path) {
          case '/v0/me':
            return _jsonResponse({'id': 7, 'username': 'alice'});
          case '/v0/users/alice/collections/42':
            return _jsonResponse({'type': 3});
          case '/v0/users/-/collections/42/episodes':
            expect(uri.queryParameters['limit'], '100');
            return _jsonResponse({
              'total': 2,
              'data': [
                {'episode_id': 1001, 'type': 2},
                {'episode_id': 1002, 'type': 1},
              ],
            });
          default:
            return _jsonResponse({}, statusCode: 500);
        }
      });
      final client = BangumiApiClient(
        sessionProvider: () => session,
        transport: transport,
      );

      await client.currentUser();
      final remote = await client.remoteState('42');

      expect(remote.accountId, '7');
      expect(remote.status, BangumiCollectionStatus.watching);
      expect(remote.watchedEpisodeIds, {'1001'});
      expect(
        remote.remoteRevision,
        BangumiRemoteState.fingerprint(
          subjectId: '42',
          status: BangumiCollectionStatus.watching,
          watchedEpisodeIds: const ['1001'],
        ),
      );
    },
  );

  test('maps auth, not-found, rate-limit and server failures stably', () async {
    for (final value in <({int status, String code, bool retryable})>[
      (status: 401, code: 'auth_required', retryable: false),
      (status: 404, code: 'not_found', retryable: false),
      (status: 429, code: 'rate_limited', retryable: true),
      (status: 503, code: 'remote_server_error', retryable: true),
    ]) {
      final transport = RecordingBangumiTransport(
        (method, uri, headers, body) =>
            _jsonResponse({}, statusCode: value.status),
      );
      final client = BangumiApiClient(
        sessionProvider: () => session,
        transport: transport,
      );
      try {
        await client.currentUser();
        fail('expected ${value.code}');
      } on BangumiApiException catch (exception) {
        expect(exception.code, value.code);
        expect(exception.retryable, value.retryable);
      }
    }
  });

  test(
    'rejects non-production API origins unless explicitly debug-enabled',
    () {
      expect(
        () => BangumiApiClient(
          sessionProvider: () => session,
          apiOrigin: Uri.parse('https://api.bgm38.tv'),
        ),
        throwsArgumentError,
      );
      expect(
        () => BangumiApiClient(
          sessionProvider: () => session,
          apiOrigin: Uri.parse('https://api.bgm38.tv'),
          allowDebugHost: true,
        ),
        returnsNormally,
      );
    },
  );

  test('malformed payloads become bounded payload errors', () async {
    final transport = RecordingBangumiTransport(
      (method, uri, headers, body) => _jsonResponse({'username': 'alice'}),
    );
    final client = BangumiApiClient(
      sessionProvider: () => session,
      transport: transport,
    );

    try {
      await client.currentUser();
      fail('expected malformed payload');
    } on BangumiPayloadException catch (error) {
      expect(error.code, 'id_missing');
    }
  });
}

final class RecordingBangumiTransport implements BangumiHttpTransport {
  RecordingBangumiTransport(this.handler);

  final Future<BangumiHttpResponse> Function(
    String method,
    Uri uri,
    Map<String, String> headers,
    String? body,
  )
  handler;
  final List<BangumiTransportCall> calls = <BangumiTransportCall>[];

  @override
  Future<BangumiHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
    required int maxResponseBytes,
  }) async {
    calls.add(
      BangumiTransportCall(
        method: method,
        uri: uri,
        headers: Map.unmodifiable(headers),
        body: body,
      ),
    );
    return handler(method, uri, headers, body);
  }
}

final class BangumiTransportCall {
  const BangumiTransportCall({
    required this.method,
    required this.uri,
    required this.headers,
    required this.body,
  });

  final String method;
  final Uri uri;
  final Map<String, String> headers;
  final String? body;
}

Future<BangumiHttpResponse> _jsonResponse(
  Object body, {
  int statusCode = 200,
}) async => BangumiHttpResponse(statusCode: statusCode, body: jsonEncode(body));

Future<BangumiHttpResponse> _emptyResponse() async =>
    const BangumiHttpResponse(statusCode: 204, body: '');

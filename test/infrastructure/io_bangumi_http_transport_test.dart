import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_api_client.dart';

void main() {
  test(
    'sends collection and episode mutations with the official wire contract',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final received = <WireRequest>[];
      final requestsDone = Completer<void>();
      final subscription = server.listen((request) async {
        final body = await utf8.decoder.bind(request).join();
        final authorization = request.headers.value(
          HttpHeaders.authorizationHeader,
        );
        received.add(
          WireRequest(
            method: request.method,
            path: request.uri.path,
            contentType: request.headers.contentType?.mimeType,
            accept: request.headers.value(HttpHeaders.acceptHeader),
            authorizationPresent: authorization != null,
            authorizationMatches: authorization == 'Bearer access-token',
            body: body,
          ),
        );
        request.response.statusCode = HttpStatus.noContent;
        await request.response.close();
        if (received.length == 2 && !requestsDone.isCompleted) {
          requestsDone.complete();
        }
      });
      addTearDown(subscription.cancel);

      final ioTransport = IoBangumiHttpTransport();
      addTearDown(ioTransport.close);
      final client = BangumiApiClient(
        sessionProvider: () => _session,
        transport: LoopbackForwardingTransport(
          delegate: ioTransport,
          baseUri: Uri.parse('http://127.0.0.1:${server.port}'),
        ),
      );

      await client.setCollectionStatus('42', BangumiCollectionStatus.watching);
      await client.setEpisodeWatched('42', '1001', true);
      await requestsDone.future.timeout(const Duration(seconds: 2));

      expect(received, hasLength(2));
      expect(received[0].method, 'POST');
      expect(received[0].path, '/v0/users/-/collections/42');
      expect(received[0].contentType, 'application/json');
      expect(received[0].accept, 'application/json');
      expect(received[0].authorizationPresent, isTrue);
      expect(received[0].authorizationMatches, isTrue);
      expect(received[0].body, '{"type":3}');

      expect(received[1].method, 'PUT');
      expect(received[1].path, '/v0/users/-/collections/-/episodes/1001');
      expect(received[1].contentType, 'application/json');
      expect(received[1].accept, 'application/json');
      expect(received[1].authorizationPresent, isTrue);
      expect(received[1].authorizationMatches, isTrue);
      expect(received[1].body, '{"type":2}');
    },
  );
}

final class LoopbackForwardingTransport implements BangumiHttpTransport {
  LoopbackForwardingTransport({required this.delegate, required this.baseUri});

  final IoBangumiHttpTransport delegate;
  final Uri baseUri;

  @override
  Future<BangumiHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
    required int maxResponseBytes,
  }) {
    return delegate.send(
      method: method,
      uri: baseUri.replace(path: uri.path, query: uri.query),
      headers: headers,
      body: body,
      maxResponseBytes: maxResponseBytes,
    );
  }
}

final class WireRequest {
  const WireRequest({
    required this.method,
    required this.path,
    required this.contentType,
    required this.accept,
    required this.authorizationPresent,
    required this.authorizationMatches,
    required this.body,
  });

  final String method;
  final String path;
  final String? contentType;
  final String? accept;
  final bool authorizationPresent;
  final bool authorizationMatches;
  final String body;
}

final _session = BangumiAuthSession(
  accountId: '7',
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  expiresAt: DateTime.utc(2030),
);

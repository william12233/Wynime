import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/hls_manifest.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/infrastructure/playback/hls_ad_planner.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_fingerprinter.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_parser.dart';
import 'package:wynime/src/infrastructure/playback/loopback_playback_proxy.dart';
import 'package:wynime/src/infrastructure/playback/proxy_upstream_client.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test(
    'proxy rewrites HLS, forwards authority data, Range, HEAD, and no secrets',
    () async {
      final upstream = _FakeUpstreamClient((request) async {
        if (request.uri.path.endsWith('master.m3u8')) {
          return _textResponse(
            200,
            '''#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="key.bin?token=key-secret"
#EXTINF:6,
segment.ts?token=segment-secret
''',
            headers: const {
              'content-type': ['application/vnd.apple.mpegurl'],
              'set-cookie': ['upstream=must-not-leak'],
            },
          );
        }
        if (request.uri.path.endsWith('segment.ts')) {
          return ProxyUpstreamResponse(
            statusCode: 206,
            headers: const {
              'content-type': ['video/mp2t'],
              'content-length': ['4'],
              'content-range': ['bytes 0-3/4'],
              'set-cookie': ['segment=must-not-leak'],
            },
            body: Stream.value([1, 2, 3, 4]),
          );
        }
        if (request.uri.path.endsWith('key.bin')) {
          return ProxyUpstreamResponse(
            statusCode: 200,
            headers: const {
              'content-type': ['application/octet-stream'],
              'content-length': ['2'],
            },
            body: Stream.value([8, 9]),
          );
        }
        throw StateError('Unexpected fake upstream URI.');
      });
      final service = LoopbackPlaybackProxyService(
        upstreamClient: upstream,
        random: Random(7),
      );
      final client = HttpClient()..autoUncompress = false;
      addTearDown(() async {
        client.close(force: true);
        await service.close();
      });

      final session = testPlaybackSession(
        headers: const {'Authorization': 'Bearer upstream-secret'},
        cookies: const {'sid': 'cookie-secret'},
        referer: Uri.parse('https://media.example/episode/1'),
        origin: Uri.parse('https://media.example'),
        userAgent: 'Wynime Proxy Test',
      );
      final lease = await service.expose(
        PlaybackProxyRequest(
          session: session,
          securityPolicy: testSourcePolicy(),
          budget: testProxyBudget(),
        ),
      );

      expect(lease.playbackUri.host, '127.0.0.1');
      expect(lease.playbackUri.hasQuery, isFalse);
      expect(lease.playbackUri.toString(), isNot(contains('media.example')));

      final masterRequest = await client.getUrl(lease.playbackUri);
      final masterResponse = await masterRequest.close();
      final playlist = await masterResponse.transform(utf8.decoder).join();

      expect(masterResponse.statusCode, 200);
      expect(masterResponse.cookies, isEmpty);
      expect(playlist, isNot(contains('segment-secret')));
      expect(playlist, isNot(contains('key-secret')));
      expect(playlist, contains(lease.playbackUri.origin));

      final segmentLine = playlist
          .split('\n')
          .firstWhere((line) => line.startsWith('http://'));
      final segmentRequest = await client.getUrl(Uri.parse(segmentLine));
      segmentRequest.headers.set(HttpHeaders.rangeHeader, 'bytes=0-3');
      final segmentResponse = await segmentRequest.close();
      final segmentBytes = await segmentResponse.fold<List<int>>(
        <int>[],
        (buffer, chunk) => buffer..addAll(chunk),
      );

      expect(segmentResponse.statusCode, 206);
      expect(segmentResponse.cookies, isEmpty);
      expect(segmentBytes, [1, 2, 3, 4]);
      final forwarded = upstream.requests.last;
      expect(forwarded.uri.queryParameters['token'], 'segment-secret');
      expect(forwarded.headers['authorization'], 'Bearer upstream-secret');
      expect(forwarded.headers['cookie'], 'sid=cookie-secret');
      expect(forwarded.headers['referer'], session.referer.toString());
      expect(forwarded.headers['origin'], session.origin.toString());
      expect(forwarded.headers['user-agent'], 'Wynime Proxy Test');
      expect(forwarded.headers['range'], 'bytes=0-3');
      expect(forwarded.headers['accept-encoding'], 'identity');

      final headRequest = await client.openUrl('HEAD', lease.playbackUri);
      final headResponse = await headRequest.close();
      expect(headResponse.statusCode, 200);
      expect(upstream.requests.last.method, 'HEAD');
    },
  );

  test(
    'active AdRemovalPlan sanitizes a matching media playlist before URI rewriting',
    () async {
      final source = File('test/fixtures/hls/cue_ads.m3u8').readAsStringSync();
      const parser = HlsManifestParser();
      const fingerprinter = HlsManifestFingerprinter();
      const planner = HlsAdPlanner();
      final mediaUri = Uri.parse('https://media.example/vod/playlist.m3u8');
      final playlist =
          parser.parse(source: source, sourceUri: mediaUri) as HlsMediaPlaylist;
      final fingerprint = fingerprinter.fingerprint(playlist);
      final episode = testEpisode();
      final plan = planner.createPlan(
        episode: episode,
        playlist: playlist,
        fingerprint: fingerprint,
        mode: AdRemovalMode.safe,
      );
      final upstream = _FakeUpstreamClient(
        (request) async => _textResponse(
          200,
          source,
          headers: const {
            'content-type': ['application/vnd.apple.mpegurl'],
          },
        ),
      );
      final service = LoopbackPlaybackProxyService(
        upstreamClient: upstream,
        random: Random(9),
      );
      final client = HttpClient();
      addTearDown(() async {
        client.close(force: true);
        await service.close();
      });
      final lease = await service.expose(
        PlaybackProxyRequest(
          session: testPlaybackSession(
            episode: episode,
            mediaUri: mediaUri,
            adRemovalPlan: plan,
          ),
          securityPolicy: testSourcePolicy(),
          budget: testProxyBudget(),
        ),
      );

      final response = await (await client.getUrl(lease.playbackUri)).close();
      final sanitized = await response.transform(utf8.decoder).join();

      expect(response.statusCode, 200);
      expect(sanitized, isNot(contains('ads.example')));
      expect(sanitized, isNot(contains('ad-1.ts')));
      expect(sanitized, isNot(contains('ad-2.ts')));
      expect(sanitized, contains('#EXTINF:10,content-a'));
      expect(sanitized, contains(lease.playbackUri.origin));
      expect(upstream.requests, hasLength(1));
    },
  );

  test(
    'unknown tokens, invalid Range, and closed leases fail closed',
    () async {
      final upstream = _FakeUpstreamClient(
        (request) async => _textResponse(
          200,
          '#EXTM3U\nsegment.ts\n',
          headers: const {
            'content-type': ['application/vnd.apple.mpegurl'],
          },
        ),
      );
      final service = LoopbackPlaybackProxyService(
        upstreamClient: upstream,
        random: Random(11),
      );
      final client = HttpClient();
      addTearDown(() async {
        client.close(force: true);
        await service.close();
      });
      final lease = await service.expose(
        PlaybackProxyRequest(
          session: testPlaybackSession(),
          securityPolicy: testSourcePolicy(),
          budget: testProxyBudget(),
        ),
      );

      final segments = [...lease.playbackUri.pathSegments];
      segments[2] = 'not-the-capability-token';
      final unknown = lease.playbackUri.replace(pathSegments: segments);
      expect((await (await client.getUrl(unknown)).close()).statusCode, 404);

      final invalidRangeRequest = await client.getUrl(lease.playbackUri);
      invalidRangeRequest.headers.set(HttpHeaders.rangeHeader, 'bytes=0-1,4-5');
      expect((await invalidRangeRequest.close()).statusCode, 416);
      expect(upstream.requests, isEmpty);

      await lease.close();
      expect(
        (await (await client.getUrl(lease.playbackUri)).close()).statusCode,
        404,
      );
    },
  );

  test(
    'redirects and child resources must stay inside the declared allowlist',
    () async {
      var mode = 'allowed-redirect';
      final upstream = _FakeUpstreamClient((request) async {
        if (mode == 'allowed-redirect' &&
            request.uri.path.endsWith('master.m3u8')) {
          mode = 'allowed-target';
          return ProxyUpstreamResponse(
            statusCode: 302,
            headers: const {
              'location': ['https://cdn.media.example/redirected.m3u8'],
            },
            body: const Stream<List<int>>.empty(),
          );
        }
        if (mode == 'allowed-target') {
          mode = 'outside-child';
          return _textResponse(
            200,
            '#EXTM3U\nsegment.ts\n',
            headers: const {
              'content-type': ['application/vnd.apple.mpegurl'],
            },
          );
        }
        return _textResponse(
          200,
          '#EXTM3U\nhttps://outside.example/segment.ts\n',
          headers: const {
            'content-type': ['application/vnd.apple.mpegurl'],
          },
        );
      });
      final service = LoopbackPlaybackProxyService(
        upstreamClient: upstream,
        random: Random(13),
      );
      final client = HttpClient();
      addTearDown(() async {
        client.close(force: true);
        await service.close();
      });
      final lease = await service.expose(
        PlaybackProxyRequest(
          session: testPlaybackSession(),
          securityPolicy: testSourcePolicy(),
          budget: testProxyBudget(),
        ),
      );

      final allowed = await (await client.getUrl(lease.playbackUri)).close();
      expect(allowed.statusCode, 200);
      await allowed.drain<void>();
      expect(upstream.requests.length, 2);

      final rejected = await (await client.getUrl(lease.playbackUri)).close();
      expect(rejected.statusCode, 502);
      await rejected.drain<void>();
    },
  );

  test(
    'closing a lease cancels an in-flight upstream body subscription',
    () async {
      final listened = Completer<void>();
      final cancelled = Completer<void>();
      late StreamController<List<int>> body;
      body = StreamController<List<int>>(
        onListen: () => listened.complete(),
        onCancel: () {
          if (!cancelled.isCompleted) {
            cancelled.complete();
          }
        },
      );
      final upstream = _FakeUpstreamClient(
        (request) async => ProxyUpstreamResponse(
          statusCode: 200,
          headers: const {
            'content-type': ['video/mp4'],
          },
          body: body.stream,
        ),
      );
      final service = LoopbackPlaybackProxyService(
        upstreamClient: upstream,
        random: Random(17),
      );
      final client = HttpClient();
      addTearDown(() async {
        await body.close();
        client.close(force: true);
        await service.close();
      });
      final lease = await service.expose(
        PlaybackProxyRequest(
          session: testPlaybackSession(
            mediaUri: Uri.parse('https://media.example/video/file.mp4'),
          ),
          securityPolicy: testSourcePolicy(),
          budget: testProxyBudget(),
        ),
      );

      final outgoing = await client.getUrl(lease.playbackUri);
      final responseFuture = outgoing.close();
      await listened.future.timeout(const Duration(seconds: 2));
      await lease.close();
      await cancelled.future.timeout(const Duration(seconds: 2));
      final response = await responseFuture.timeout(const Duration(seconds: 2));
      expect(response.statusCode, 502);
      await response.drain<void>();
    },
  );
}

final class _FakeUpstreamClient implements ProxyUpstreamClient {
  _FakeUpstreamClient(this._handler);

  final Future<ProxyUpstreamResponse> Function(ProxyUpstreamRequest request)
  _handler;
  final List<ProxyUpstreamRequest> requests = [];
  bool closed = false;

  @override
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request) {
    if (closed) {
      throw StateError('Fake upstream client is closed.');
    }
    requests.add(request);
    return _handler(request);
  }

  @override
  Future<void> close() async {
    closed = true;
  }
}

ProxyUpstreamResponse _textResponse(
  int status,
  String text, {
  Map<String, List<String>> headers = const {},
}) {
  final bytes = utf8.encode(text);
  return ProxyUpstreamResponse(
    statusCode: status,
    headers: {
      ...headers,
      'content-length': [bytes.length.toString()],
    },
    body: Stream.value(bytes),
  );
}

import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/infrastructure/playback/bounded_media_probe.dart';
import 'package:wynime/src/infrastructure/playback/proxy_upstream_client.dart';
import 'package:wynime/src/infrastructure/web_capture/web_capture_accumulator.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test('A: HEAD 403 does not mask a bounded GET Range 206', () async {
    final upstream = _FakeUpstreamClient((request) async {
      if (request.method == 'HEAD') {
        return _response(403, const {
          'content-type': ['text/html'],
        }, '<html>');
      }
      return _response(206, const {
        'content-type': ['video/mp4'],
      }, 'media-bytes');
    });
    final result = await _probe(
      upstream,
      session: testPlaybackSession(
        mediaUri: Uri.parse('https://media.example/video/file.mp4'),
      ),
      kind: WebCandidateKind.video,
    );

    expect(result.passed, isTrue);
    expect(upstream.requests, hasLength(1));
    expect(upstream.requests.single.method, 'GET');
    expect(upstream.requests.single.headers['range'], 'bytes=0-131071');
  });

  test('B: redirects retain bounded Range semantics', () async {
    final upstream = _FakeUpstreamClient((request) async {
      if (request.uri.host == 'media.example') {
        return _response(302, const {
          'location': ['https://cdn.media.example/video.mp4'],
        }, '');
      }
      return _response(206, const {
        'content-type': ['video/mp4'],
      }, 'media-bytes');
    });
    final result = await _probe(
      upstream,
      session: testPlaybackSession(
        mediaUri: Uri.parse('https://media.example/video.mp4'),
      ),
      kind: WebCandidateKind.video,
      policy: testSourcePolicy(
        domains: [
          SourceDomainRule(host: 'media.example', includeSubdomains: true),
        ],
      ),
    );

    expect(result.passed, isTrue);
    expect(upstream.requests, hasLength(2));
    expect(
      upstream.requests.map((request) => request.headers['range']),
      everyElement('bytes=0-131071'),
    );
    expect(result.requestShapes.map((shape) => shape.redirectCount), [0, 1]);
  });

  test(
    'C: captured Referer is used only in the matching A/B variant',
    () async {
      final upstream = _FakeUpstreamClient((request) async {
        if (request.headers['referer'] != 'https://media.example/watch') {
          return _response(403, const {
            'content-type': ['text/html'],
          }, '<html>hotlink denied</html>');
        }
        return _response(206, const {
          'content-type': ['video/mp4'],
        }, 'media-bytes');
      });
      final session = testPlaybackSession(
        mediaUri: Uri.parse('https://media.example/video/file.mp4'),
        referer: Uri.parse('https://media.example/watch'),
      );
      final withoutReferer = await _probe(
        upstream,
        session: session,
        kind: WebCandidateKind.video,
        includeReferer: false,
      );
      final withReferer = await _probe(
        upstream,
        session: session,
        kind: WebCandidateKind.video,
        includeReferer: true,
      );

      expect(withoutReferer.passed, isFalse);
      expect(withoutReferer.reasonCode, 'external_http_403');
      expect(withReferer.passed, isTrue);
      expect(
        upstream.requests[1].headers['referer'],
        session.referer.toString(),
      );
    },
  );

  test('D: a cookie domain mismatch is not forwarded after redirect', () async {
    final upstream = _FakeUpstreamClient((request) async {
      if (request.uri.host == 'media.example') {
        return _response(302, const {
          'location': ['https://other.example/video.mp4'],
        }, '');
      }
      expect(request.headers, isNot(contains('cookie')));
      return _response(206, const {
        'content-type': ['video/mp4'],
      }, 'media-bytes');
    });
    final episode = testEpisode();
    final session = PlaybackSession(
      sessionId: 'cookie-scope-session',
      episode: episode,
      mediaUri: Uri.parse('https://media.example/video.mp4'),
      pageUri: Uri.parse('https://media.example/watch'),
      cookies: const {'sid': 'opaque'},
      cookieMetadata: [
        WebCaptureCookie(name: 'sid', value: 'opaque', domain: 'media.example'),
      ],
      adRemovalPlan: testAdRemovalPlan(episode),
    );
    final result = await _probe(
      upstream,
      session: session,
      kind: WebCandidateKind.video,
      policy: testSourcePolicy(
        domains: [
          SourceDomainRule(host: 'media.example'),
          SourceDomainRule(host: 'other.example'),
        ],
      ),
    );

    expect(result.passed, isTrue);
    expect(upstream.requests.last.headers, isNot(contains('cookie')));
  });

  test('E: Origin is not invented when capture did not observe it', () async {
    final upstream = _FakeUpstreamClient((request) async {
      expect(request.headers, isNot(contains('origin')));
      return _response(206, const {
        'content-type': ['video/mp4'],
      }, 'media-bytes');
    });
    final result = await _probe(
      upstream,
      session: testPlaybackSession(
        mediaUri: Uri.parse('https://media.example/video.mp4'),
      ),
      kind: WebCandidateKind.video,
      includeOrigin: true,
    );

    expect(result.passed, isTrue);
    expect(result.requestShapes.single.headerPresence['origin'], isFalse);
  });

  test('F: later valid candidate ranks before retained redirector', () {
    final request = WebCaptureRequest(
      initialUri: Uri.parse('https://media.example/watch'),
      securityPolicy: testSourcePolicy(
        permissions: {
          SourcePermission.network,
          SourcePermission.webView,
          SourcePermission.mediaRequestInspection,
        },
      ),
      budget: WebCaptureBudget(
        maxEvents: 10,
        maxCandidates: 5,
        maxHeaderBytes: 4096,
        maxCookieBytes: 4096,
      ),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.platformDefault,
      ),
      captureMediaRequests: true,
      completionPolicy:
          WebCaptureCompletionPolicy.firstValidatedPlayableCandidateAfterLoad,
    );
    final accumulator = WebCaptureAccumulator(request);
    accumulator.add(
      WebCaptureEvent(
        sequence: 0,
        kind: WebRequestKind.resource,
        uri: Uri.parse('https://media.example/wrapper.mp4'),
        isRedirect: true,
      ),
    );
    accumulator.add(
      WebCaptureEvent(
        sequence: 1,
        kind: WebRequestKind.resource,
        uri: Uri.parse('https://media.example/video.mp4'),
        headers: const {'content-type': 'video/mp4'},
      ),
    );

    final snapshot = accumulator.finish(
      finalUri: Uri.parse('https://media.example/watch'),
    );
    expect(snapshot.candidates, hasLength(2));
    expect(snapshot.candidates.first.isRedirect, isFalse);
    expect(snapshot.candidates.last.isRedirect, isTrue);
    expect(accumulator.hasValidatedPlayableCandidate, isTrue);
  });

  test('G: HTML 200 is not accepted as media', () async {
    final upstream = _FakeUpstreamClient(
      (request) async => _response(200, const {
        'content-type': ['text/html'],
      }, '<!doctype html><html>access denied</html>'),
    );
    final result = await _probe(
      upstream,
      session: testPlaybackSession(
        mediaUri: Uri.parse('https://media.example/video.mp4'),
      ),
      kind: WebCandidateKind.video,
    );

    expect(result.passed, isFalse);
    expect(result.reasonCode, 'html_200_not_media');
    expect(result.finalBodyClassification, 'html_document');
  });

  test(
    'H: HLS manifest and one bounded segment prove media reachability',
    () async {
      final upstream = _FakeUpstreamClient((request) async {
        if (request.uri.path.endsWith('.m3u8')) {
          return _response(
            200,
            const {
              'content-type': ['application/vnd.apple.mpegurl'],
            },
            '''#EXTM3U
#EXT-X-TARGETDURATION:4
#EXTINF:4,
segment.ts
#EXT-X-ENDLIST
''',
          );
        }
        expect(request.headers['range'], 'bytes=0-131071');
        return _response(206, const {
          'content-type': ['video/mp2t'],
        }, 'segment-bytes');
      });
      final result = await _probe(
        upstream,
        session: testPlaybackSession(
          mediaUri: Uri.parse('https://media.example/video/master.m3u8'),
        ),
        kind: WebCandidateKind.hls,
      );

      expect(result.passed, isTrue);
      expect(result.reasonCode, 'hls_manifest_and_segment_playable');
      expect(upstream.requests, hasLength(2));
      expect(upstream.requests.first.headers, isNot(contains('range')));
      expect(upstream.requests.last.headers['range'], 'bytes=0-131071');
    },
  );

  test('I: response body reads are timeout-bounded and cancelled', () async {
    final cancelled = Completer<void>();
    final body = StreamController<List<int>>(
      onCancel: () {
        if (!cancelled.isCompleted) cancelled.complete();
      },
    );
    final upstream = _FakeUpstreamClient(
      (request) async => ProxyUpstreamResponse(
        statusCode: 206,
        headers: const {
          'content-type': ['video/mp4'],
        },
        body: body.stream,
      ),
    );

    final result = await _probe(
      upstream,
      session: testPlaybackSession(
        mediaUri: Uri.parse('https://media.example/video.mp4'),
      ),
      kind: WebCandidateKind.video,
      budget: testProxyBudget(upstreamTimeout: const Duration(seconds: 1)),
    );

    expect(result.passed, isFalse);
    expect(result.reasonCode, 'probe_timeout');
    await cancelled.future.timeout(const Duration(seconds: 1));
    await body.close();
  });
}

Future<BoundedMediaProbeResult> _probe(
  _FakeUpstreamClient upstream, {
  required PlaybackSession session,
  required WebCandidateKind kind,
  SourceSecurityPolicy? policy,
  bool includeReferer = true,
  bool includeOrigin = true,
  bool includeUserAgent = true,
  bool includeCookies = true,
  PlaybackProxyBudget? budget,
}) => BoundedMediaProbe(upstreamClient: upstream).probe(
  session: session,
  securityPolicy: policy ?? testSourcePolicy(),
  budget: budget ?? testProxyBudget(),
  candidateKind: kind,
  includeReferer: includeReferer,
  includeOrigin: includeOrigin,
  includeUserAgent: includeUserAgent,
  includeCookies: includeCookies,
);

ProxyUpstreamResponse _response(
  int status,
  Map<String, List<String>> headers,
  String body,
) => ProxyUpstreamResponse(
  statusCode: status,
  headers: headers,
  body: Stream<List<int>>.value(body.codeUnits),
);

final class _FakeUpstreamClient implements ProxyUpstreamClient {
  _FakeUpstreamClient(this.handler);

  final Future<ProxyUpstreamResponse> Function(ProxyUpstreamRequest request)
  handler;
  final requests = <ProxyUpstreamRequest>[];

  @override
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request) {
    requests.add(request);
    return handler(request);
  }

  @override
  Future<void> close() async {}
}

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/infrastructure/web_capture/web_capture_accumulator.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  WebCaptureRequest request({
    int maxEvents = 20,
    int maxCandidates = 5,
    int maxHeaderBytes = 4096,
    int maxRedirects = 3,
    bool captureDocument = false,
    bool allowRuntimeMediaOrigins = false,
    String? acquisitionId,
  }) {
    return WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/watch'),
      securityPolicy: testSourcePolicy(
        permissions: {
          SourcePermission.network,
          SourcePermission.webView,
          SourcePermission.cookies,
          SourcePermission.mediaRequestInspection,
        },
        budget: testSourceBudget(maxRedirects: maxRedirects),
      ),
      budget: WebCaptureBudget(
        maxEvents: maxEvents,
        maxCandidates: maxCandidates,
        maxHeaderBytes: maxHeaderBytes,
        maxCookieBytes: 4096,
      ),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.platformDefault,
      ),
      captureMediaRequests: true,
      captureDocument: captureDocument,
      allowRuntimeMediaOrigins: allowRuntimeMediaOrigins,
      acquisitionId: acquisitionId,
    );
  }

  test('media candidates are classified, deduplicated, and kept in memory', () {
    final accumulator = WebCaptureAccumulator(request());
    final first = WebCaptureEvent(
      sequence: 0,
      kind: WebRequestKind.fetch,
      uri: Uri.parse('https://cdn.example.com/master.m3u8?token=secret'),
      headers: {
        'Authorization': 'Bearer secret',
        'Content-Type': 'application/vnd.apple.mpegurl',
      },
    );

    expect(accumulator.add(first), isTrue);
    expect(
      accumulator.add(
        WebCaptureEvent(
          sequence: 1,
          kind: WebRequestKind.resource,
          uri: Uri.parse(
            'https://cdn.example.com/master.m3u8?token=secret#fragment',
          ),
          headers: first.headers,
        ),
      ),
      isTrue,
    );

    final snapshot = accumulator.finish(
      finalUri: Uri.parse('https://example.com/watch'),
    );
    expect(snapshot.events, hasLength(2));
    expect(snapshot.candidates, hasLength(1));
    expect(snapshot.candidates.single.kind, WebCandidateKind.hls);
    expect(
      snapshot.candidates.single.headers['authorization'],
      'Bearer secret',
    );
    expect(snapshot.toString(), isNot(contains('Bearer secret')));
    expect(
      snapshot.candidates.single.toString(),
      isNot(contains('token=secret')),
    );
  });

  test('disallowed request targets fail closed', () {
    final accumulator = WebCaptureAccumulator(request());

    expect(
      () => accumulator.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://evil-example.com/video.mp4'),
        ),
      ),
      throwsA(isA<WebCaptureSecurityException>()),
    );
  });

  test('dynamic media origin is exact, short-lived, and acquisition-bound', () {
    final runtimeRequest = request(
      allowRuntimeMediaOrigins: true,
      acquisitionId: 'episode-a-1',
    );
    final accumulator = WebCaptureAccumulator(runtimeRequest);
    expect(
      accumulator.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://dynamic-cdn.invalid/stream?id=opaque'),
          headers: const {
            'Range': 'bytes=0-',
            'Referer': 'https://example.com/watch',
          },
          runtimeOriginValidated: true,
        ),
      ),
      isTrue,
    );

    final candidate = accumulator
        .finish(finalUri: Uri.parse('https://example.com/watch'))
        .candidates
        .single;
    expect(candidate.kind, WebCandidateKind.video);
    expect(candidate.runtimeOriginGrant, isNotNull);
    expect(
      candidate.runtimeOriginGrant!.allows(
        candidate.uri,
        acquisitionId: 'episode-a-1',
      ),
      isTrue,
    );
    expect(
      candidate.runtimeOriginGrant!.allows(
        candidate.uri,
        acquisitionId: 'episode-b-1',
      ),
      isFalse,
    );
    expect(
      candidate.runtimeOriginGrant!.allows(
        Uri.parse('https://other-cdn.invalid/video.mp4'),
        acquisitionId: 'episode-a-1',
      ),
      isFalse,
    );
  });

  test('runtime origin cannot be admitted without validated provenance', () {
    final accumulator = WebCaptureAccumulator(
      request(allowRuntimeMediaOrigins: true, acquisitionId: 'episode-a-2'),
    );
    expect(
      () => accumulator.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://dynamic-cdn.invalid/video.mp4'),
        ),
      ),
      throwsA(isA<WebCaptureSecurityException>()),
    );
  });

  test('runtime origin rejects unrelated ranged resources', () {
    final accumulator = WebCaptureAccumulator(
      request(allowRuntimeMediaOrigins: true, acquisitionId: 'episode-a-3'),
    );
    expect(
      () => accumulator.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://assets.invalid/poster'),
          headers: const {
            'Range': 'bytes=0-1023',
            'Sec-Fetch-Dest': 'image',
            'Accept': 'image/avif,image/webp',
          },
          runtimeOriginValidated: true,
        ),
      ),
      throwsA(
        isA<WebCaptureSecurityException>().having(
          (error) => error.code,
          'code',
          'uri_not_allowed',
        ),
      ),
    );
  });

  test('multiple candidates use deterministic media evidence ranking', () {
    final accumulator = WebCaptureAccumulator(request());
    expect(
      accumulator.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://cdn.example.com/opaque'),
          headers: const {'Range': 'bytes=0-'},
        ),
      ),
      isTrue,
    );
    expect(
      accumulator.add(
        WebCaptureEvent(
          sequence: 1,
          kind: WebRequestKind.fetch,
          uri: Uri.parse('https://cdn.example.com/master.m3u8'),
          headers: const {'Content-Type': 'application/vnd.apple.mpegurl'},
        ),
      ),
      isTrue,
    );

    final candidates = accumulator
        .finish(finalUri: Uri.parse('https://example.com/watch'))
        .candidates;
    expect(candidates, hasLength(2));
    expect(candidates.first.kind, WebCandidateKind.hls);
    expect(candidates.first.sourceEventSequence, 1);
    expect(candidates.last.sourceEventSequence, 0);
  });

  test('event sequence must increase strictly', () {
    final accumulator = WebCaptureAccumulator(request());
    expect(
      accumulator.add(
        WebCaptureEvent(
          sequence: 4,
          kind: WebRequestKind.navigation,
          uri: Uri.parse('https://example.com/watch'),
        ),
      ),
      isTrue,
    );

    expect(
      () => accumulator.add(
        WebCaptureEvent(
          sequence: 4,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://example.com/video.mp4'),
        ),
      ),
      throwsA(
        isA<WebCaptureSecurityException>().having(
          (error) => error.code,
          'code',
          'event_sequence_invalid',
        ),
      ),
    );
  });

  test('event, redirect, and header budgets stop capture explicitly', () {
    final eventLimited = WebCaptureAccumulator(request(maxEvents: 1));
    expect(
      eventLimited.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.navigation,
          uri: Uri.parse('https://example.com/watch'),
        ),
      ),
      isTrue,
    );
    expect(
      eventLimited.add(
        WebCaptureEvent(
          sequence: 1,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://example.com/video.mp4'),
        ),
      ),
      isFalse,
    );
    expect(
      eventLimited
          .finish(finalUri: Uri.parse('https://example.com/watch'))
          .stopReason,
      WebCaptureStopReason.eventBudgetExceeded,
    );

    final redirectLimited = WebCaptureAccumulator(request(maxRedirects: 0));
    expect(
      redirectLimited.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.navigation,
          uri: Uri.parse('https://example.com/redirected'),
          isRedirect: true,
        ),
      ),
      isFalse,
    );
    expect(
      redirectLimited
          .finish(finalUri: Uri.parse('https://example.com/redirected'))
          .stopReason,
      WebCaptureStopReason.redirectBudgetExceeded,
    );

    final headerLimited = WebCaptureAccumulator(request(maxHeaderBytes: 4));
    expect(
      headerLimited.add(
        WebCaptureEvent(
          sequence: 0,
          kind: WebRequestKind.resource,
          uri: Uri.parse('https://example.com/video.mp4'),
          headers: {'x-long': 'value'},
        ),
      ),
      isFalse,
    );
    expect(
      headerLimited
          .finish(finalUri: Uri.parse('https://example.com/watch'))
          .stopReason,
      WebCaptureStopReason.headerBudgetExceeded,
    );
  });

  test('exported cookies require permission, budget, and allowlist', () {
    final accumulator = WebCaptureAccumulator(request());
    final snapshot = accumulator.finish(
      finalUri: Uri.parse('https://example.com/watch'),
      cookies: [
        WebCaptureCookie(
          name: 'session',
          value: 'secret',
          domain: 'example.com',
        ),
      ],
    );

    expect(snapshot.cookies, hasLength(1));
    expect(snapshot.toString(), isNot(contains('secret')));
  });

  test('document snapshots remain bounded and opt-in', () {
    final documentRequest = request(captureDocument: true);
    final documentSnapshot = WebCaptureAccumulator(documentRequest).finish(
      finalUri: Uri.parse('https://example.com/search'),
      documentBody: '<main>hydrated</main>',
    );
    expect(documentSnapshot.documentBody, '<main>hydrated</main>');

    expect(
      () => WebCaptureAccumulator(request()).finish(
        finalUri: Uri.parse('https://example.com/search'),
        documentBody: '<main>not requested</main>',
      ),
      throwsA(
        isA<WebCaptureSecurityException>().having(
          (error) => error.code,
          'code',
          'document_not_requested',
        ),
      ),
    );
  });
}

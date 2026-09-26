import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_capture_coordinator.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/source_live_capture.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('accepts a completed snapshot with exact source provenance', () async {
    final request = _request();
    final event = WebCaptureEvent(
      sequence: 0,
      kind: WebRequestKind.resource,
      uri: Uri.parse('https://cdn.example.com/video.m3u8'),
      headers: const {'Referer': 'https://example.com/watch'},
    );
    final snapshot = _snapshot(
      events: [event],
      candidates: [
        WebMediaCandidate(
          kind: WebCandidateKind.hls,
          uri: event.uri.replace(fragment: ''),
          headers: const {'Referer': 'https://example.com/watch'},
          sourceEventSequence: 0,
        ),
      ],
      cookies: [
        WebCaptureCookie(
          name: 'session',
          value: 'secret-cookie',
          domain: 'example.com',
        ),
      ],
    );
    final port = _FakeCapturePort((_) async => snapshot);
    final coordinator = SourceLiveCaptureCoordinator(port: port);

    final result = await coordinator.capture(request);
    expect(
      result.status,
      SourceLiveCaptureStatus.captured,
      reason: result.toString(),
    );
    expect(result.snapshot, same(snapshot));
    expect(result.packageId, request.packageId);
    expect(result.packageVersion, request.packageVersion);
    expect(result.programId, request.programId);
    expect(port.requests.single, same(request));
    expect(coordinator.latestResult, same(result));
    expect(result.toString(), contains('candidateCount: 1'));
    expect(result.toString(), isNot(contains('video.m3u8')));
    expect(result.toString(), isNot(contains('secret-cookie')));
  });

  test(
    'rejects an explicitly incomplete capture as a budget outcome',
    () async {
      final request = _request();
      final snapshot = _snapshot(
        stopReason: WebCaptureStopReason.eventBudgetExceeded,
      );
      final result = await SourceLiveCaptureCoordinator(
        port: _FakeCapturePort((_) async => snapshot),
      ).capture(request);

      expect(result.status, SourceLiveCaptureStatus.budgetExceeded);
      expect(result.reasonCode, 'capture_event_budget_exceeded');
      expect(result.snapshot, isNull);
    },
  );

  test('rechecks event, header, candidate and redirect budgets', () async {
    final event = WebCaptureEvent(
      sequence: 0,
      kind: WebRequestKind.resource,
      uri: Uri.parse('https://example.com/video.mp4'),
      headers: const {'x': '123'},
    );

    final tooManyEvents = await SourceLiveCaptureCoordinator(
      port: _FakeCapturePort(
        (_) async => _snapshot(events: [event, event.copyWith(sequence: 1)]),
      ),
    ).capture(_request(maxEvents: 1));
    expect(tooManyEvents.status, SourceLiveCaptureStatus.budgetExceeded);
    expect(tooManyEvents.reasonCode, 'capture_event_budget_exceeded');

    final tooManyHeaders = await SourceLiveCaptureCoordinator(
      port: _FakeCapturePort((_) async => _snapshot(events: [event])),
    ).capture(_request(maxHeaderBytes: 7));
    expect(tooManyHeaders.status, SourceLiveCaptureStatus.budgetExceeded);
    expect(tooManyHeaders.reasonCode, 'capture_header_budget_exceeded');

    final tooManyCandidates = await SourceLiveCaptureCoordinator(
      port: _FakeCapturePort(
        (_) async => _snapshot(
          candidates: List.generate(
            2,
            (index) => WebMediaCandidate(
              kind: WebCandidateKind.video,
              uri: Uri.parse('https://cdn.example.com/video-$index.mp4'),
              headers: const {},
              sourceEventSequence: 0,
            ),
          ),
        ),
      ),
    ).capture(_request(maxCandidates: 1));
    expect(tooManyCandidates.status, SourceLiveCaptureStatus.budgetExceeded);
    expect(tooManyCandidates.reasonCode, 'capture_candidate_budget_exceeded');

    final tooManyCandidateHeaders = await SourceLiveCaptureCoordinator(
      port: _FakeCapturePort(
        (_) async => _snapshot(
          events: [event],
          candidates: [
            WebMediaCandidate(
              kind: WebCandidateKind.video,
              uri: event.uri.replace(fragment: ''),
              headers: event.headers,
              sourceEventSequence: 0,
            ),
            WebMediaCandidate(
              kind: WebCandidateKind.hls,
              uri: event.uri.replace(fragment: ''),
              headers: event.headers,
              sourceEventSequence: 0,
            ),
          ],
        ),
      ),
    ).capture(_request(maxHeaderBytes: 10, maxCandidates: 2));
    expect(
      tooManyCandidateHeaders.status,
      SourceLiveCaptureStatus.budgetExceeded,
    );
    expect(
      tooManyCandidateHeaders.reasonCode,
      'capture_header_budget_exceeded',
    );

    final redirect = event.copyWith(isRedirect: true);
    final tooManyRedirects = await SourceLiveCaptureCoordinator(
      port: _FakeCapturePort((_) async => _snapshot(events: [redirect])),
    ).capture(_request(maxRedirects: 0));
    expect(tooManyRedirects.status, SourceLiveCaptureStatus.budgetExceeded);
    expect(tooManyRedirects.reasonCode, 'capture_redirect_budget_exceeded');
  });

  test('rechecks URI, permission, sequence and candidate provenance', () async {
    final disallowedFinalUri = await _captureInvalid(
      _request(),
      _snapshot(finalUri: Uri.parse('https://evil.example.net/watch')),
    );
    expect(disallowedFinalUri.reasonCode, 'final_uri_not_allowed');

    final disallowedEvent = await _captureInvalid(
      _request(),
      _snapshot(
        events: [
          WebCaptureEvent(
            sequence: 0,
            kind: WebRequestKind.resource,
            uri: Uri.parse('https://evil.example.net/video.mp4'),
          ),
        ],
      ),
    );
    expect(disallowedEvent.reasonCode, 'event_uri_not_allowed');

    final duplicateSequence = await _captureInvalid(
      _request(),
      _snapshot(events: [_event(0), _event(0)]),
    );
    expect(duplicateSequence.reasonCode, 'event_sequence_invalid');

    final disallowedCandidate = await _captureInvalid(
      _request(),
      _snapshot(
        candidates: [
          WebMediaCandidate(
            kind: WebCandidateKind.hls,
            uri: Uri.parse('https://evil.example.net/video.m3u8'),
            headers: const {},
            sourceEventSequence: 0,
          ),
        ],
      ),
    );
    expect(disallowedCandidate.reasonCode, 'candidate_uri_not_allowed');

    final candidateNotDerived = await _captureInvalid(
      _request(),
      _snapshot(
        candidates: [
          WebMediaCandidate(
            kind: WebCandidateKind.hls,
            uri: Uri.parse('https://cdn.example.com/video.m3u8'),
            headers: const {},
            sourceEventSequence: 0,
          ),
        ],
      ),
    );
    expect(candidateNotDerived.reasonCode, 'candidate_provenance_invalid');

    final candidateHeadersNotDerived = await _captureInvalid(
      _request(),
      _snapshot(
        events: [
          WebCaptureEvent(
            sequence: 0,
            kind: WebRequestKind.resource,
            uri: Uri.parse('https://cdn.example.com/video.m3u8'),
            headers: const {'x-source': 'one'},
          ),
        ],
        candidates: [
          WebMediaCandidate(
            kind: WebCandidateKind.hls,
            uri: Uri.parse('https://cdn.example.com/video.m3u8#'),
            headers: const {},
            sourceEventSequence: 0,
          ),
        ],
      ),
    );
    expect(
      candidateHeadersNotDerived.reasonCode,
      'candidate_provenance_invalid',
    );

    final missingCandidateEvent = await _captureInvalid(
      _request(),
      _snapshot(
        candidates: [
          WebMediaCandidate(
            kind: WebCandidateKind.hls,
            uri: Uri.parse('https://cdn.example.com/video.m3u8'),
            headers: const {},
            sourceEventSequence: 99,
          ),
        ],
      ),
    );
    expect(
      missingCandidateEvent.reasonCode,
      'candidate_event_sequence_invalid',
    );

    final mediaNotRequested = await _captureInvalid(
      _request(captureMediaRequests: false),
      _snapshot(
        candidates: [
          WebMediaCandidate(
            kind: WebCandidateKind.hls,
            uri: Uri.parse('https://cdn.example.com/video.m3u8'),
            headers: const {},
            sourceEventSequence: 0,
          ),
        ],
      ),
    );
    expect(mediaNotRequested.reasonCode, 'media_candidates_not_requested');

    final cookiePermissionMissing = await _captureInvalid(
      _request(
        permissions: const {
          SourcePermission.network,
          SourcePermission.webView,
          SourcePermission.mediaRequestInspection,
        },
      ),
      _snapshot(
        cookies: [
          WebCaptureCookie(
            name: 'session',
            value: 'value',
            domain: 'example.com',
          ),
        ],
      ),
    );
    expect(cookiePermissionMissing.reasonCode, 'cookie_permission_missing');

    final cookieDomainNotAllowed = await _captureInvalid(
      _request(),
      _snapshot(
        cookies: [
          WebCaptureCookie(
            name: 'session',
            value: 'value',
            domain: 'evil.example.net',
          ),
        ],
      ),
    );
    expect(cookieDomainNotAllowed.reasonCode, 'cookie_domain_not_allowed');
  });

  test('maps platform failures to a stable redacted error', () async {
    final request = _request();
    final result = await SourceLiveCaptureCoordinator(
      port: _FakeCapturePort(
        (_) async => throw StateError('Authorization=secret-token'),
      ),
    ).capture(request);

    expect(result.status, SourceLiveCaptureStatus.failed);
    expect(result.reasonCode, 'capture_failed');
    expect(result.toString(), isNot(contains('Authorization')));
    expect(result.toString(), isNot(contains('secret-token')));
  });

  test('preserves a bounded platform capture stage code', () async {
    final result = await SourceLiveCaptureCoordinator(
      port: _FakeCapturePort(
        (_) async => throw const _CaptureFailure('browser_capture_timeout'),
      ),
    ).capture(_request());

    expect(result.status, SourceLiveCaptureStatus.failed);
    expect(result.reasonCode, 'browser_capture_timeout');
  });

  test(
    'supersedes an older completion and keeps the newer result current',
    () async {
      final firstCompleter = Completer<WebCaptureSnapshot>();
      final secondCompleter = Completer<WebCaptureSnapshot>();
      final port = _FakeCapturePort((request) {
        return request.programId == 'first'
            ? firstCompleter.future
            : secondCompleter.future;
      });
      final coordinator = SourceLiveCaptureCoordinator(port: port);

      final firstRequest = _request(programId: 'first');
      final secondRequest = _request(programId: 'second');
      final firstFuture = coordinator.capture(firstRequest);
      final secondFuture = coordinator.capture(secondRequest);

      final secondSnapshot = _snapshot(
        finalUri: Uri.parse('https://example.com/second'),
      );
      secondCompleter.complete(secondSnapshot);
      final secondResult = await secondFuture;

      firstCompleter.complete(_snapshot());
      final firstResult = await firstFuture;

      expect(secondResult.status, SourceLiveCaptureStatus.captured);
      expect(firstResult.status, SourceLiveCaptureStatus.superseded);
      expect(firstResult.reasonCode, 'capture_superseded');
      expect(firstResult.snapshot, isNull);
      expect(coordinator.latestResult, same(secondResult));
      expect(coordinator.latestResult!.programId, 'second');
    },
  );

  test(
    'close invalidates pending and future captures without state mutation',
    () async {
      final pending = Completer<WebCaptureSnapshot>();
      final request = _request();
      final coordinator = SourceLiveCaptureCoordinator(
        port: _FakeCapturePort((_) => pending.future),
      );
      final pendingResult = coordinator.capture(request);

      coordinator.close();
      pending.complete(_snapshot());

      final lateResult = await pendingResult;
      final afterClose = await coordinator.capture(request);

      expect(coordinator.isClosed, isTrue);
      expect(lateResult.status, SourceLiveCaptureStatus.closed);
      expect(lateResult.reasonCode, 'capture_closed');
      expect(afterClose.status, SourceLiveCaptureStatus.closed);
      expect(afterClose.reasonCode, 'capture_closed');
      expect(coordinator.latestResult, isNull);
    },
  );

  test('late errors are ignored after supersession and close', () async {
    final firstCompleter = Completer<WebCaptureSnapshot>();
    final secondCompleter = Completer<WebCaptureSnapshot>();
    final coordinator = SourceLiveCaptureCoordinator(
      port: _FakeCapturePort((request) {
        return request.programId == 'first'
            ? firstCompleter.future
            : secondCompleter.future;
      }),
    );

    final firstFuture = coordinator.capture(_request(programId: 'first'));
    final secondFuture = coordinator.capture(_request(programId: 'second'));
    secondCompleter.complete(_snapshot());
    final secondResult = await secondFuture;

    firstCompleter.completeError(StateError('raw-token=secret'));
    final firstResult = await firstFuture;

    expect(secondResult.status, SourceLiveCaptureStatus.captured);
    expect(firstResult.status, SourceLiveCaptureStatus.superseded);
    expect(firstResult.reasonCode, 'capture_superseded');
    expect(coordinator.latestResult, same(secondResult));

    final closedCompleter = Completer<WebCaptureSnapshot>();
    final closedCoordinator = SourceLiveCaptureCoordinator(
      port: _FakeCapturePort((_) => closedCompleter.future),
    );
    final closedFuture = closedCoordinator.capture(_request());
    closedCoordinator.close();
    closedCompleter.completeError(StateError('Cookie=secret'));

    final closedResult = await closedFuture;
    expect(closedResult.status, SourceLiveCaptureStatus.closed);
    expect(closedResult.reasonCode, 'capture_closed');
    expect(closedCoordinator.latestResult, isNull);
  });

  test('request and result invariants reject unsafe identities', () {
    final webRequest = _request().webCaptureRequest;
    expect(
      () => SourceLiveCaptureRequest(
        packageId: 'Example.Anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'watch',
        webCaptureRequest: webRequest,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCaptureRequest(
        packageId: ' example.anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'watch',
        webCaptureRequest: webRequest,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCaptureRequest(
        packageId: 'example.anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'Watch',
        webCaptureRequest: webRequest,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCaptureRequest(
        packageId: 'example.anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'watch ',
        webCaptureRequest: webRequest,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCaptureResult(
        packageId: 'example.anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'watch',
        status: SourceLiveCaptureStatus.captured,
        reasonCode: 'capture_failed',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCaptureResult(
        packageId: 'example.anime',
        packageVersion: Version.parse('1.0.0'),
        programId: 'watch',
        status: SourceLiveCaptureStatus.failed,
        reasonCode: 'raw-error',
      ),
      throwsArgumentError,
    );
  });
}

Future<SourceLiveCaptureResult> _captureInvalid(
  SourceLiveCaptureRequest request,
  WebCaptureSnapshot snapshot,
) {
  return SourceLiveCaptureCoordinator(
    port: _FakeCapturePort((_) async => snapshot),
  ).capture(request);
}

SourceLiveCaptureRequest _request({
  String packageId = 'example.anime',
  String programId = 'watch',
  bool captureMediaRequests = true,
  Set<SourcePermission>? permissions,
  int maxEvents = 20,
  int maxCandidates = 5,
  int maxHeaderBytes = 4096,
  int maxCookieBytes = 4096,
  int maxRedirects = 3,
}) {
  final effectivePermissions =
      permissions ??
      const {
        SourcePermission.network,
        SourcePermission.webView,
        SourcePermission.cookies,
        SourcePermission.mediaRequestInspection,
      };
  return SourceLiveCaptureRequest(
    packageId: packageId,
    packageVersion: Version.parse('1.2.3'),
    programId: programId,
    webCaptureRequest: WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/watch'),
      securityPolicy: testSourcePolicy(
        permissions: effectivePermissions,
        budget: testSourceBudget(maxRedirects: maxRedirects),
      ),
      budget: WebCaptureBudget(
        maxEvents: maxEvents,
        maxCandidates: maxCandidates,
        maxHeaderBytes: maxHeaderBytes,
        maxCookieBytes: maxCookieBytes,
      ),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.platformDefault,
      ),
      captureMediaRequests: captureMediaRequests,
    ),
  );
}

WebCaptureSnapshot _snapshot({
  Iterable<WebCaptureEvent>? events,
  Iterable<WebMediaCandidate> candidates = const [],
  Iterable<WebCaptureCookie> cookies = const [],
  WebCaptureStopReason stopReason = WebCaptureStopReason.completed,
  Uri? finalUri,
}) {
  return WebCaptureSnapshot(
    events: events ?? [_event(0)],
    candidates: candidates,
    cookies: cookies,
    stopReason: stopReason,
    finalUri: finalUri ?? Uri.parse('https://example.com/watch'),
  );
}

WebCaptureEvent _event(int sequence, {bool isRedirect = false}) =>
    WebCaptureEvent(
      sequence: sequence,
      kind: WebRequestKind.navigation,
      uri: Uri.parse('https://example.com/watch'),
      isRedirect: isRedirect,
    );

final class _FakeCapturePort implements SourceLiveCapturePort {
  _FakeCapturePort(this.handler);

  final Future<WebCaptureSnapshot> Function(SourceLiveCaptureRequest request)
  handler;
  final requests = <SourceLiveCaptureRequest>[];

  @override
  Future<WebCaptureSnapshot> capture(SourceLiveCaptureRequest request) {
    requests.add(request);
    return handler(request);
  }
}

final class _CaptureFailure implements SourceLiveCaptureFailure {
  const _CaptureFailure(this.code);

  @override
  final String code;
}

extension on WebCaptureEvent {
  WebCaptureEvent copyWith({int? sequence, bool? isRedirect}) {
    return WebCaptureEvent(
      sequence: sequence ?? this.sequence,
      kind: kind,
      uri: uri,
      method: method,
      headers: headers,
      isMainFrame: isMainFrame,
      isRedirect: isRedirect ?? this.isRedirect,
    );
  }
}

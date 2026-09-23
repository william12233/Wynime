import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  SourceSecurityPolicy capturePolicy({Set<SourcePermission>? permissions}) =>
      testSourcePolicy(
        permissions:
            permissions ??
            {
              SourcePermission.network,
              SourcePermission.webView,
              SourcePermission.desktopUserAgent,
              SourcePermission.cookies,
              SourcePermission.mediaRequestInspection,
            },
      );

  WebCaptureBudget budget() => WebCaptureBudget(
    maxEvents: 20,
    maxCandidates: 5,
    maxHeaderBytes: 4096,
    maxCookieBytes: 4096,
  );

  test('capture request enforces declared permissions and allowlist', () {
    final desktop = WebUserAgentPolicy(
      mode: WebUserAgentMode.desktop,
      value: 'Mozilla/5.0 Wynime Desktop Capture',
    );

    expect(
      () => WebCaptureRequest(
        initialUri: Uri.parse('https://example.com/watch'),
        securityPolicy: capturePolicy(permissions: {SourcePermission.network}),
        budget: budget(),
        userAgentPolicy: desktop,
        captureMediaRequests: true,
      ),
      throwsArgumentError,
    );
    expect(
      () => WebCaptureRequest(
        initialUri: Uri.parse('https://evil-example.com/watch'),
        securityPolicy: capturePolicy(),
        budget: budget(),
        userAgentPolicy: desktop,
        captureMediaRequests: true,
      ),
      throwsArgumentError,
    );
  });

  test('cookies and headers never expose values through diagnostics', () {
    final cookie = WebCaptureCookie(
      name: 'session',
      value: 'secret-cookie-value',
      domain: 'example.com',
      isHttpOnly: true,
    );
    final request = WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/watch?token=secret'),
      securityPolicy: capturePolicy(),
      budget: budget(),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.desktop,
        value: 'Mozilla/5.0 Wynime Desktop Capture',
      ),
      captureMediaRequests: true,
      initialHeaders: {'Authorization': 'Bearer secret-token'},
      initialCookies: [cookie],
    );
    final event = WebCaptureEvent(
      sequence: 0,
      kind: WebRequestKind.resource,
      uri: Uri.parse('https://example.com/video.m3u8?token=secret'),
      headers: {'Cookie': 'session=secret-cookie-value'},
    );

    expect(cookie.toString(), isNot(contains('secret-cookie-value')));
    expect(request.toString(), isNot(contains('secret-token')));
    expect(event.toString(), isNot(contains('token=secret')));
    expect(event.toString(), isNot(contains('secret-cookie-value')));
  });

  test('header and cookie budgets count UTF-8 bytes', () {
    final unicode = List.filled(4, '界').join();
    expect(webCaptureHeaderBytes({'x': unicode}), greaterThan(unicode.length));

    expect(
      () => WebCaptureRequest(
        initialUri: Uri.parse('https://example.com/watch'),
        securityPolicy: capturePolicy(),
        budget: WebCaptureBudget(
          maxEvents: 20,
          maxCandidates: 5,
          maxHeaderBytes: 10,
          maxCookieBytes: 4096,
        ),
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: false,
        initialHeaders: {'x': unicode},
      ),
      throwsArgumentError,
    );

    final cookie = WebCaptureCookie(
      name: 'session',
      value: unicode,
      domain: 'example.com',
    );
    expect(
      cookie.encodedBytes,
      greaterThan('session${unicode}example.com/'.length),
    );
  });

  test('cookie domains remain inside the source authority', () {
    expect(
      () => WebCaptureRequest(
        initialUri: Uri.parse('https://example.com/watch'),
        securityPolicy: capturePolicy(),
        budget: budget(),
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: false,
        initialCookies: [
          WebCaptureCookie(
            name: 'session',
            value: 'value',
            domain: 'other.example',
          ),
        ],
      ),
      throwsArgumentError,
    );
  });

  test('unavailable runtime status is explicit', () {
    final status = WebCaptureRuntimeStatus(
      state: WebCaptureRuntimeState.unavailable,
      platform: WebCapturePlatform.windowsWebView2,
      reasonCode: 'webview2_runtime_missing',
    );

    expect(status.isAvailable, isFalse);
    expect(status.reasonCode, 'webview2_runtime_missing');
  });

  test('interactive completion is bounded and requires media inspection', () {
    expect(
      () => WebCaptureRequest(
        initialUri: Uri.parse('https://example.com/watch'),
        securityPolicy: capturePolicy(
          permissions: {SourcePermission.network, SourcePermission.webView},
        ),
        budget: budget(),
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: false,
        completionPolicy:
            WebCaptureCompletionPolicy.firstPlayableCandidateAfterLoad,
      ),
      throwsArgumentError,
    );

    final request = WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/watch'),
      securityPolicy: capturePolicy(),
      budget: budget(),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.platformDefault,
      ),
      captureMediaRequests: true,
      completionPolicy:
          WebCaptureCompletionPolicy.firstPlayableCandidateAfterLoad,
      postLoadTimeout: const Duration(seconds: 20),
    );
    expect(
      request.completionPolicy,
      WebCaptureCompletionPolicy.firstPlayableCandidateAfterLoad,
    );
    expect(request.postLoadTimeout, const Duration(seconds: 20));
  });

  test('document completion is bounded and explicitly requested', () {
    expect(
      () => WebCaptureRequest(
        initialUri: Uri.parse('https://example.com/search'),
        securityPolicy: capturePolicy(
          permissions: {SourcePermission.network, SourcePermission.webView},
        ),
        budget: budget(),
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: false,
        completionPolicy: WebCaptureCompletionPolicy.documentAfterLoad,
      ),
      throwsArgumentError,
    );

    final request = WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/search'),
      securityPolicy: capturePolicy(
        permissions: {SourcePermission.network, SourcePermission.webView},
      ),
      budget: budget(),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.platformDefault,
      ),
      captureMediaRequests: false,
      captureDocument: true,
      completionPolicy: WebCaptureCompletionPolicy.documentAfterLoad,
      postLoadTimeout: const Duration(seconds: 2),
    );
    expect(request.captureDocument, isTrue);
    expect(
      request.completionPolicy,
      WebCaptureCompletionPolicy.documentAfterLoad,
    );

    final snapshot = WebCaptureSnapshot(
      events: const [],
      candidates: const [],
      cookies: const [],
      stopReason: WebCaptureStopReason.completed,
      finalUri: request.initialUri,
      documentBody: '<main><a href="/anime/1">title</a></main>',
    );
    expect(snapshot.documentBody, contains('/anime/1'));
    expect(snapshot.toString(), contains('hasDocument: true'));
    expect(snapshot.toString(), isNot(contains('title')));
  });
}

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/web_source_browser.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_capture_view.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_source_live_capture_port.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_source_live_capture_view.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  testWidgets('port forwards one snapshot and ignores duplicate callbacks', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final request = _request();
    final port = InAppWebViewSourceLiveCapturePort(
      browserPort: _BrowserPort(),
      viewBuilder: probe.build,
    );
    final future = port.capture(request);

    await tester.pumpWidget(Builder(builder: port.buildView));
    probe.runtimeStatus!(_availableStatus());
    final snapshot = _snapshot(request.webCaptureRequest);
    probe.snapshot!(snapshot);
    probe.snapshot!(snapshot);

    expect(await future, same(snapshot));
    expect(probe.request, same(request.webCaptureRequest));
  });

  testWidgets('unavailable runtime fails the typed port without hanging', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final port = InAppWebViewSourceLiveCapturePort(
      browserPort: _BrowserPort(),
      viewBuilder: probe.build,
    );
    final future = port.capture(_request());

    await tester.pumpWidget(Builder(builder: port.buildView));
    probe.runtimeStatus!(
      WebCaptureRuntimeStatus(
        state: WebCaptureRuntimeState.unavailable,
        platform: WebCapturePlatform.windowsWebView2,
        reasonCode: 'webview2_runtime_missing',
      ),
    );

    await expectLater(
      future,
      throwsA(
        isA<InAppWebViewSourceLiveCaptureException>().having(
          (error) => error.code,
          'code',
          'capture_runtime_unavailable',
        ),
      ),
    );
  });

  testWidgets('a newer direct port capture supersedes the older completion', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final port = InAppWebViewSourceLiveCapturePort(
      browserPort: _BrowserPort(),
      viewBuilder: probe.build,
    );
    final oldFuture = port.capture(_request(programId: 'watch'));
    final oldExpectation = expectLater(
      oldFuture,
      throwsA(
        isA<InAppWebViewSourceLiveCaptureException>().having(
          (error) => error.code,
          'code',
          'capture_superseded',
        ),
      ),
    );
    await tester.pumpWidget(Builder(builder: port.buildView));
    final oldSnapshotCallback = probe.snapshot!;

    final newFuture = port.capture(_request(programId: 'detail'));
    await tester.pumpWidget(Builder(builder: port.buildView));
    oldSnapshotCallback(_snapshot(_request().webCaptureRequest));
    final newSnapshot = _snapshot(
      _request(programId: 'detail').webCaptureRequest,
    );
    probe.snapshot!(newSnapshot);

    expect(await newFuture, same(newSnapshot));
    await oldExpectation;
  });

  testWidgets('a capture surface build failure completes as a safe error', (
    tester,
  ) async {
    final port = InAppWebViewSourceLiveCapturePort(
      browserPort: _BrowserPort(),
      viewBuilder:
          (
            context, {
            required request,
            required browserPort,
            required onSnapshot,
            required onRuntimeStatus,
            required onSecurityFailure,
            required onCaptureFailure,
            loadingBuilder,
            unavailableBuilder,
          }) => throw StateError('test-only build failure'),
    );
    final future = port.capture(_request());
    final expectation = expectLater(
      future,
      throwsA(
        isA<InAppWebViewSourceLiveCaptureException>().having(
          (error) => error.code,
          'code',
          'capture_view_build_failed',
        ),
      ),
    );

    await tester.pumpWidget(Builder(builder: port.buildView));
    await expectation;
  });

  testWidgets('fatal WebView failure is distinct from policy notifications', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final securityNotifications = <String>[];
    final port = InAppWebViewSourceLiveCapturePort(
      browserPort: _BrowserPort(),
      viewBuilder: probe.build,
      onSecurityFailure: (error) => securityNotifications.add(error.code),
    );
    final future = port.capture(_request());

    await tester.pumpWidget(Builder(builder: port.buildView));
    probe.securityFailure!(
      WebCaptureSecurityException('request_blocked', 'safe policy notice'),
    );
    expect(securityNotifications, ['request_blocked']);
    probe.captureFailure!(
      WebCaptureSecurityException('cookie_export_failed', 'safe failure'),
    );

    await expectLater(
      future,
      throwsA(
        isA<InAppWebViewSourceLiveCaptureException>().having(
          (error) => error.code,
          'code',
          'cookie_export_failed',
        ),
      ),
    );
  });

  testWidgets('unexpected finalization errors become safe fatal failures', (
    tester,
  ) async {
    final previousPlatform = InAppWebViewPlatform.instance;
    InAppWebViewPlatform.instance = _FakeInAppWebViewPlatform();
    addTearDown(() {
      if (previousPlatform != null) {
        InAppWebViewPlatform.instance = previousPlatform;
      }
    });

    final securityCodes = <String>[];
    final fatalCodes = <String>[];
    final snapshots = <WebCaptureSnapshot>[];
    await tester.pumpWidget(
      InAppWebViewCaptureView(
        request: _request(allowCookies: true).webCaptureRequest,
        browserPort: _BrowserPort(
          exportFailure: StateError('raw plugin failure must not escape'),
        ),
        onSnapshot: snapshots.add,
        onSecurityFailure: (error) => securityCodes.add(error.code),
        onCaptureFailure: (error) => fatalCodes.add(error.code),
      ),
    );
    await tester.pump();
    await tester.pump();

    final webView = tester.widget<InAppWebView>(find.byType(InAppWebView));
    final onLoadStop = webView.platform.params.onLoadStop;
    expect(onLoadStop, isNotNull);
    final controller = InAppWebViewController.fromPlatformCreationParams(
      params: PlatformInAppWebViewControllerCreationParams(id: 1),
    );
    onLoadStop!(controller, WebUri('https://example.com/watch'));
    await tester.pump();
    await tester.pump();

    expect(securityCodes, ['webview_capture_finalize_failed']);
    expect(fatalCodes, ['webview_capture_finalize_failed']);
    expect(snapshots, isEmpty);
  });

  testWidgets('widget delivers an admitted result once', (tester) async {
    final probe = _CaptureProbe();
    final results = <SourceLiveCaptureResult>[];

    await tester.pumpWidget(
      InAppWebViewSourceLiveCapture(
        request: _request(),
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onResult: results.add,
      ),
    );
    probe.runtimeStatus!(_availableStatus());
    final snapshot = _snapshot(_request().webCaptureRequest);
    probe.snapshot!(snapshot);
    probe.snapshot!(snapshot);
    await tester.pump();

    expect(results, hasLength(1));
    expect(results.single.status, SourceLiveCaptureStatus.captured);
    expect(results.single.snapshot, same(snapshot));
  });

  testWidgets('widget replacement ignores the old WebView completion', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final results = <SourceLiveCaptureResult>[];
    final first = _request(programId: 'watch');
    final second = _request(programId: 'detail');

    await tester.pumpWidget(
      InAppWebViewSourceLiveCapture(
        request: first,
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onResult: results.add,
      ),
    );
    final oldSnapshot = _snapshot(first.webCaptureRequest);
    final oldSnapshotCallback = probe.snapshot!;

    await tester.pumpWidget(
      InAppWebViewSourceLiveCapture(
        request: second,
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onResult: results.add,
      ),
    );
    oldSnapshotCallback(oldSnapshot);
    expect(results, isEmpty);

    probe.runtimeStatus!(_availableStatus());
    probe.snapshot!(_snapshot(second.webCaptureRequest));
    await tester.pump();

    expect(results, hasLength(1));
    expect(results.single.programId, 'detail');
  });

  testWidgets('dispose invalidates the pending WebView completion', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final results = <SourceLiveCaptureResult>[];

    await tester.pumpWidget(
      InAppWebViewSourceLiveCapture(
        request: _request(),
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onResult: results.add,
      ),
    );
    final lateSnapshot = _snapshot(_request().webCaptureRequest);
    final lateCallback = probe.snapshot!;
    await tester.pumpWidget(const SizedBox.shrink());
    lateCallback(lateSnapshot);
    await tester.pump();

    expect(results, isEmpty);
  });

  test('closing the port completes a pending operation exactly once', () async {
    final port = InAppWebViewSourceLiveCapturePort(browserPort: _BrowserPort());
    final future = port.capture(_request());
    port.close();
    port.close();

    await expectLater(
      future,
      throwsA(
        isA<InAppWebViewSourceLiveCaptureException>().having(
          (error) => error.code,
          'code',
          'capture_closed',
        ),
      ),
    );
  });

  test('capture after close returns a safe typed error', () async {
    final port = InAppWebViewSourceLiveCapturePort(browserPort: _BrowserPort());
    port.close();

    await expectLater(
      port.capture(_request()),
      throwsA(
        isA<InAppWebViewSourceLiveCaptureException>().having(
          (error) => error.code,
          'code',
          'capture_closed',
        ),
      ),
    );
  });

  testWidgets('observer failures do not strand an unavailable capture', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final port = InAppWebViewSourceLiveCapturePort(
      browserPort: _BrowserPort(),
      viewBuilder: probe.build,
      onRuntimeStatus: (_) => throw StateError('test-only observer failure'),
    );
    final future = port.capture(_request());

    await tester.pumpWidget(Builder(builder: port.buildView));
    probe.runtimeStatus!(
      WebCaptureRuntimeStatus(
        state: WebCaptureRuntimeState.unavailable,
        platform: WebCapturePlatform.windowsWebView2,
        reasonCode: 'webview2_runtime_missing',
      ),
    );

    await expectLater(
      future,
      throwsA(
        isA<InAppWebViewSourceLiveCaptureException>().having(
          (error) => error.code,
          'code',
          'capture_runtime_unavailable',
        ),
      ),
    );
  });
}

SourceLiveCaptureRequest _request({
  String programId = 'watch',
  bool allowCookies = false,
}) {
  final permissions = {
    SourcePermission.network,
    SourcePermission.webView,
    SourcePermission.mediaRequestInspection,
  };
  if (allowCookies) {
    permissions.add(SourcePermission.cookies);
  }
  return SourceLiveCaptureRequest(
    packageId: 'example.anime',
    packageVersion: Version.parse('1.0.0'),
    programId: programId,
    webCaptureRequest: WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/watch'),
      securityPolicy: testSourcePolicy(permissions: permissions),
      budget: WebCaptureBudget(
        maxEvents: 20,
        maxCandidates: 5,
        maxHeaderBytes: 4096,
        maxCookieBytes: 0,
      ),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.platformDefault,
      ),
      captureMediaRequests: true,
    ),
  );
}

WebCaptureRuntimeStatus _availableStatus() => WebCaptureRuntimeStatus(
  state: WebCaptureRuntimeState.available,
  platform: WebCapturePlatform.androidWebView,
);

WebCaptureSnapshot _snapshot(WebCaptureRequest request) => WebCaptureSnapshot(
  events: [
    WebCaptureEvent(
      sequence: 0,
      kind: WebRequestKind.navigation,
      uri: request.initialUri,
      headers: request.initialHeaders,
      isMainFrame: true,
    ),
  ],
  candidates: const [],
  cookies: const [],
  stopReason: WebCaptureStopReason.completed,
  finalUri: request.initialUri,
);

final class _BrowserPort implements WebSourceBrowserPort {
  _BrowserPort({this.exportFailure});

  final Object? exportFailure;

  @override
  Future<void> clearCookies(WebCaptureRequest request, Uri uri) async {}

  @override
  Future<List<WebCaptureCookie>> exportCookies(
    WebCaptureRequest request,
    Uri uri,
  ) async {
    final failure = exportFailure;
    if (failure != null) {
      throw failure;
    }
    return const [];
  }

  @override
  Future<void> importCookies(WebCaptureRequest request) async {}

  @override
  Future<WebCaptureRuntimeStatus> probeRuntime() async => _availableStatus();
}

final class _CaptureProbe {
  WebCaptureRequest? request;
  ValueChanged<WebCaptureSnapshot>? snapshot;
  ValueChanged<WebCaptureRuntimeStatus>? runtimeStatus;
  ValueChanged<WebCaptureSecurityException>? securityFailure;
  ValueChanged<WebCaptureSecurityException>? captureFailure;

  Widget build(
    BuildContext context, {
    required WebCaptureRequest request,
    required WebSourceBrowserPort browserPort,
    required ValueChanged<WebCaptureSnapshot> onSnapshot,
    required ValueChanged<WebCaptureRuntimeStatus> onRuntimeStatus,
    required ValueChanged<WebCaptureSecurityException> onSecurityFailure,
    required ValueChanged<WebCaptureSecurityException> onCaptureFailure,
    WidgetBuilder? loadingBuilder,
    Widget Function(BuildContext context, WebCaptureRuntimeStatus status)?
    unavailableBuilder,
  }) {
    this.request = request;
    snapshot = onSnapshot;
    runtimeStatus = onRuntimeStatus;
    securityFailure = onSecurityFailure;
    captureFailure = onCaptureFailure;
    return const SizedBox.shrink();
  }
}

final class _FakeInAppWebViewPlatform extends InAppWebViewPlatform {
  @override
  PlatformInAppWebViewController createPlatformInAppWebViewController(
    PlatformInAppWebViewControllerCreationParams params,
  ) => _FakeInAppWebViewController(params);

  @override
  PlatformInAppWebViewWidget createPlatformInAppWebViewWidget(
    PlatformInAppWebViewWidgetCreationParams params,
  ) => _FakeInAppWebViewWidget(params);
}

final class _FakeInAppWebViewController extends PlatformInAppWebViewController {
  // The platform interface requires its protected named implementation
  // constructor, so this test-only adapter cannot use a super parameter.
  // ignore: use_super_parameters
  _FakeInAppWebViewController(
    PlatformInAppWebViewControllerCreationParams params,
  ) : super.implementation(params);

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('test-only fake controller');
}

final class _FakeInAppWebViewWidget extends PlatformInAppWebViewWidget {
  // The platform interface requires its protected named implementation
  // constructor, so this test-only adapter cannot use a super parameter.
  // ignore: use_super_parameters
  _FakeInAppWebViewWidget(PlatformInAppWebViewWidgetCreationParams params)
    : super.implementation(params);

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();

  @override
  T controllerFromPlatform<T>(PlatformInAppWebViewController controller) =>
      controller as T;

  @override
  void dispose() {}
}

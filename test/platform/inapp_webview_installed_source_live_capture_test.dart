import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_package_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/web_source_browser.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_installed_source_live_capture_view.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  testWidgets(
    'non-admitted package reports typed admission and never builds WebView',
    (tester) async {
      final probe = _CaptureProbe();
      final admissions = <SourceLiveCapturePackageResult>[];
      final results = <SourceLiveCaptureResult>[];

      await tester.pumpWidget(
        InAppWebViewInstalledSourceLiveCapture(
          wynimeVersion: Version.parse('1.0.0'),
          plan: _plan(
            installedPackage: _installed(status: SourcePackageStatus.disabled),
          ),
          browserPort: _BrowserPort(),
          captureViewBuilder: probe.build,
          onAdmission: admissions.add,
          onResult: results.add,
        ),
      );

      expect(admissions, hasLength(1));
      expect(admissions.single.status, SourceLiveCapturePackageStatus.disabled);
      expect(admissions.single.reasonCode, 'package_disabled');
      expect(probe.buildCount, 0);
      expect(results, isEmpty);
    },
  );

  testWidgets('ready package forwards exact admission and capture result', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final plan = _plan();
    final admissions = <SourceLiveCapturePackageResult>[];
    final results = <SourceLiveCaptureResult>[];

    await tester.pumpWidget(
      InAppWebViewInstalledSourceLiveCapture(
        wynimeVersion: Version.parse('1.0.0'),
        plan: plan,
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onAdmission: admissions.add,
        onResult: results.add,
      ),
    );

    expect(admissions, hasLength(1));
    final admission = admissions.single;
    expect(admission.status, SourceLiveCapturePackageStatus.ready);
    expect(admission.request, isNotNull);
    expect(admission.request!.webCaptureRequest, same(plan.webCaptureRequest));
    expect(probe.request, same(plan.webCaptureRequest));
    expect(probe.buildCount, 1);

    final snapshot = _snapshot(plan.webCaptureRequest);
    probe.runtimeStatus!(_availableStatus());
    probe.snapshot!(snapshot);
    probe.snapshot!(snapshot);
    await tester.pump();

    expect(results, hasLength(1));
    expect(results.single.status, SourceLiveCaptureStatus.captured);
    expect(results.single.snapshot, same(snapshot));
    expect(results.single.packageId, 'example.anime');
    expect(results.single.programId, 'search');
  });

  testWidgets('consent precedence prevents a disabled package surface', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final admissions = <SourceLiveCapturePackageResult>[];

    await tester.pumpWidget(
      InAppWebViewInstalledSourceLiveCapture(
        wynimeVersion: Version.parse('1.0.0'),
        plan: _plan(
          installedPackage: _installed(
            status: SourcePackageStatus.disabled,
            requiresConsent: true,
          ),
        ),
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onAdmission: admissions.add,
        onResult: (_) {},
      ),
    );

    expect(
      admissions.single.status,
      SourceLiveCapturePackageStatus.consentRequired,
    );
    expect(admissions.single.reasonCode, 'consent_required');
    expect(probe.buildCount, 0);
  });

  testWidgets('replacement ignores an old admitted WebView completion', (
    tester,
  ) async {
    final probe = _CaptureProbe();
    final admissions = <SourceLiveCapturePackageResult>[];
    final results = <SourceLiveCaptureResult>[];
    final first = _plan(programId: 'search');
    final second = _plan(programId: 'detail');

    await tester.pumpWidget(
      InAppWebViewInstalledSourceLiveCapture(
        wynimeVersion: Version.parse('1.0.0'),
        plan: first,
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onAdmission: admissions.add,
        onResult: results.add,
      ),
    );
    final oldSnapshotCallback = probe.snapshot!;

    await tester.pumpWidget(
      InAppWebViewInstalledSourceLiveCapture(
        wynimeVersion: Version.parse('1.0.0'),
        plan: second,
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onAdmission: admissions.add,
        onResult: results.add,
      ),
    );
    oldSnapshotCallback(_snapshot(first.webCaptureRequest));
    expect(results, isEmpty);

    probe.runtimeStatus!(_availableStatus());
    probe.snapshot!(_snapshot(second.webCaptureRequest));
    await tester.pump();

    expect(admissions, hasLength(2));
    expect(results, hasLength(1));
    expect(results.single.programId, 'detail');
  });

  testWidgets('dispose prevents a late admitted result', (tester) async {
    final probe = _CaptureProbe();
    final results = <SourceLiveCaptureResult>[];

    await tester.pumpWidget(
      InAppWebViewInstalledSourceLiveCapture(
        wynimeVersion: Version.parse('1.0.0'),
        plan: _plan(),
        browserPort: _BrowserPort(),
        captureViewBuilder: probe.build,
        onAdmission: (_) {},
        onResult: results.add,
      ),
    );
    final lateSnapshotCallback = probe.snapshot!;
    await tester.pumpWidget(const SizedBox.shrink());
    lateSnapshotCallback(_snapshot(_plan().webCaptureRequest));
    await tester.pump();

    expect(results, isEmpty);
  });
}

SourceLiveCapturePackagePlan _plan({
  String programId = 'search',
  InstalledSourcePackage? installedPackage,
}) {
  final policy = testSourcePolicy(
    permissions: {
      SourcePermission.network,
      SourcePermission.webView,
      SourcePermission.mediaRequestInspection,
    },
  );
  final package = installedPackage ?? _installed(policy: policy);
  return SourceLiveCapturePackagePlan(
    installedPackage: package,
    programId: programId,
    webCaptureRequest: _request(policy: policy),
  );
}

InstalledSourcePackage _installed({
  SourcePackageManifest? package,
  SourceSecurityPolicy? policy,
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) => InstalledSourcePackage(
  package: package ?? _package(policy: policy),
  status: status,
  requiresConsent: requiresConsent,
  requiresReconsent: requiresReconsent,
);

SourcePackageManifest _package({SourceSecurityPolicy? policy}) =>
    SourcePackageManifest(
      schemaVersion: 1,
      packageId: 'example.anime',
      displayName: 'Example Anime',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      securityPolicy: policy ?? testSourcePolicy(),
      programs: [_program('search'), _program('detail')],
    );

SourceRuleProgram _program(String programId) => SourceRuleProgram(
  programId: programId,
  documentKind: SourceDocumentKind.html,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.css,
    expression: '.item',
  ),
  fields: [SourceFieldRule(name: 'title', valueKind: SourceValueKind.raw)],
  resultLimit: 1,
);

WebCaptureRequest _request({required SourceSecurityPolicy policy}) =>
    WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/search'),
      securityPolicy: policy,
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
    );

WebCaptureSnapshot _snapshot(WebCaptureRequest request) => WebCaptureSnapshot(
  events: [
    WebCaptureEvent(
      sequence: 0,
      kind: WebRequestKind.navigation,
      uri: request.initialUri,
      isMainFrame: true,
    ),
  ],
  candidates: const [],
  cookies: const [],
  stopReason: WebCaptureStopReason.completed,
  finalUri: request.initialUri,
);

WebCaptureRuntimeStatus _availableStatus() => WebCaptureRuntimeStatus(
  state: WebCaptureRuntimeState.available,
  platform: WebCapturePlatform.androidWebView,
);

final class _BrowserPort implements WebSourceBrowserPort {
  @override
  Future<void> clearCookies(WebCaptureRequest request, Uri uri) async {}

  @override
  Future<List<WebCaptureCookie>> exportCookies(
    WebCaptureRequest request,
    Uri uri, {
    RuntimeMediaOriginGrant? runtimeOriginGrant,
  }) async => const [];

  @override
  Future<void> importCookies(WebCaptureRequest request) async {}

  @override
  Future<WebCaptureRuntimeStatus> probeRuntime() async => _availableStatus();
}

final class _CaptureProbe {
  WebCaptureRequest? request;
  ValueChanged<WebCaptureSnapshot>? snapshot;
  ValueChanged<WebCaptureRuntimeStatus>? runtimeStatus;
  var buildCount = 0;

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
    ++buildCount;
    this.request = request;
    snapshot = onSnapshot;
    runtimeStatus = onRuntimeStatus;
    return const SizedBox.shrink();
  }
}

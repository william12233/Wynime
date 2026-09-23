import 'package:flutter/widgets.dart';
import 'package:pub_semver/pub_semver.dart';

import '../../application/source_live_capture_package_coordinator.dart';
import '../../application/source_live_document_package_runtime.dart';
import '../../application/source_live_search_coordinator.dart';
import '../../domain/models/source_live_capture_package_models.dart';
import '../../domain/models/source_runtime_models.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/source_package_runtime.dart';
import '../../domain/services/web_source_browser.dart';
import 'inapp_webview_source_live_capture_port.dart';

/// Supplies a bounded browser-rendered document when static source search has
/// returned a typed `notFound` result for a JavaScript-hydrated page.
///
/// The page is evaluated by the same declarative package runtime as static
/// responses. Source packages cannot inject JavaScript; the only document
/// bridge is the fixed native `getHtml()` call made by the capture view.
final class InAppWebViewSourceLiveSearchFallback extends ChangeNotifier
    implements SourceLiveSearchDocumentFallback {
  InAppWebViewSourceLiveSearchFallback({
    required this.wynimeVersion,
    required SourcePackageRuntime fixtureRuntime,
    required WebSourceBrowserPort browserPort,
    InAppWebViewCaptureWidgetBuilder? captureViewBuilder,
  }) : _port = InAppWebViewSourceLiveCapturePort(
         browserPort: browserPort,
         viewBuilder: captureViewBuilder,
       ),
       _documentRuntime = SourceLiveDocumentPackageRuntime(
         fixtureRuntime: fixtureRuntime,
       ) {
    _packageCoordinator = SourceLiveCapturePackageCoordinator(
      wynimeVersion: wynimeVersion,
      port: _port,
    );
  }

  final Version wynimeVersion;
  final InAppWebViewSourceLiveCapturePort _port;
  final SourceLiveDocumentPackageRuntime _documentRuntime;
  late final SourceLiveCapturePackageCoordinator _packageCoordinator;

  var _generation = 0;
  var _closed = false;
  var _pending = false;

  bool get isClosed => _closed;
  bool get hasPendingCapture => _pending;

  @override
  Future<SourceRuntimeResult> capture(SourceLiveSearchPlan plan) async {
    if (_closed) {
      return _failure(plan, 'live_search_capture_closed');
    }

    final generation = ++_generation;
    late final SourceLiveCapturePackagePlan packagePlan;
    late final SourceLiveCapturePackageResult admission;
    try {
      final request = WebCaptureRequest(
        initialUri: plan.requestPlan.request.uri,
        securityPolicy:
            plan.requestPlan.installedPackage.package.securityPolicy,
        budget: WebCaptureBudget(
          maxEvents: 512,
          maxCandidates: 1,
          maxHeaderBytes: _boundedWebBudget(
            plan
                .requestPlan
                .installedPackage
                .package
                .securityPolicy
                .budget
                .maxDocumentBytes,
          ),
          maxCookieBytes: _boundedWebBudget(
            plan
                .requestPlan
                .installedPackage
                .package
                .securityPolicy
                .budget
                .maxDocumentBytes,
          ),
        ),
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: false,
        captureDocument: true,
        completionPolicy: WebCaptureCompletionPolicy.documentAfterLoad,
        postLoadTimeout: const Duration(seconds: 5),
        initialHeaders: plan.requestPlan.request.headers,
      );
      packagePlan = SourceLiveCapturePackagePlan(
        installedPackage: plan.requestPlan.installedPackage,
        programId: plan.requestPlan.programId,
        webCaptureRequest: request,
      );
      admission = _packageCoordinator.prepare(packagePlan);
    } on Object {
      return _failure(plan, 'search_capture_request_invalid');
    }

    final admittedRequest = admission.request;
    if (admittedRequest == null) {
      return _failure(
        plan,
        admission.reasonCode ?? 'search_capture_not_admitted',
        status: _runtimeStatusFor(admission.status),
      );
    }

    _pending = true;
    _notifyListenersSafely();
    try {
      final captureResult = await _packageCoordinator.capture(packagePlan);
      if (!_isCurrent(generation)) {
        return _failure(plan, 'search_capture_superseded');
      }
      return _documentRuntime.execute(
        installedPackage: plan.requestPlan.installedPackage,
        request: admittedRequest,
        captureResult: captureResult,
      );
    } on Object {
      return _failure(plan, 'search_capture_failed');
    } finally {
      if (_isCurrent(generation)) {
        _pending = false;
        _notifyListenersSafely();
      }
    }
  }

  /// Mounts the current generation. The caller keeps this widget in the
  /// search page while [hasPendingCapture] is true; no raw document is exposed
  /// through the widget tree.
  Widget buildView(BuildContext context) => _port.buildView(context);

  void close() {
    if (_closed) return;
    _closed = true;
    ++_generation;
    _pending = false;
    _packageCoordinator.close();
    _port.close();
    _notifyListenersSafely();
  }

  bool _isCurrent(int generation) => !_closed && generation == _generation;

  SourceRuntimeResult _failure(
    SourceLiveSearchPlan plan,
    String code, {
    SourceRuntimeStatus status = SourceRuntimeStatus.failed,
  }) {
    final package = plan.requestPlan.installedPackage.package;
    final safeCode = RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(code)
        ? code
        : 'search_capture_failed';
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.requestPlan.programId,
      status: status,
      records: const [],
      diagnostics: [
        SourceRuntimeDiagnostic(
          code: safeCode,
          message: 'The bounded live search document could not be evaluated.',
        ),
      ],
      consumedSteps: 0,
      selectorMatches: 0,
    );
  }

  static SourceRuntimeStatus _runtimeStatusFor(
    SourceLiveCapturePackageStatus status,
  ) => switch (status) {
    SourceLiveCapturePackageStatus.consentRequired =>
      SourceRuntimeStatus.consentRequired,
    SourceLiveCapturePackageStatus.disabled => SourceRuntimeStatus.disabled,
    SourceLiveCapturePackageStatus.incompatible =>
      SourceRuntimeStatus.incompatible,
    _ => SourceRuntimeStatus.failed,
  };

  static int _boundedWebBudget(int maxDocumentBytes) =>
      maxDocumentBytes.clamp(0, 256 * 1024);

  void _notifyListenersSafely() {
    try {
      notifyListeners();
    } on Object {
      // Listener failures must not alter capture admission or completion.
    }
  }
}

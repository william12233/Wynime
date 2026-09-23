import 'package:flutter/widgets.dart';
import 'package:pub_semver/pub_semver.dart';

import '../../application/source_live_capture_package_coordinator.dart';
import '../../application/source_live_document_package_runtime.dart';
import '../../application/source_live_playable_source_coordinator.dart';
import '../../domain/models/source_live_capture_package_models.dart';
import '../../domain/models/source_runtime_models.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/source_package_runtime.dart';
import '../../domain/services/web_source_browser.dart';
import 'inapp_webview_source_live_capture_port.dart';

/// Supplies one bounded browser-rendered playable document when static HTTP
/// returns a JavaScript shell. The captured document is evaluated by the
/// package's existing declarative program; raw HTML never leaves this class.
final class InAppWebViewSourceLivePlayableDocumentFallback
    extends ChangeNotifier
    implements SourceLivePlayableDocumentFallback {
  InAppWebViewSourceLivePlayableDocumentFallback({
    required this.wynimeVersion,
    required SourcePackageRuntime fixtureRuntime,
    required this.browserPort,
    this.captureViewBuilder,
  }) : _documentRuntime = SourceLiveDocumentPackageRuntime(
         fixtureRuntime: fixtureRuntime,
       ) {
    _createCapturePort();
  }

  final Version wynimeVersion;
  final WebSourceBrowserPort browserPort;
  final InAppWebViewCaptureWidgetBuilder? captureViewBuilder;
  final SourceLiveDocumentPackageRuntime _documentRuntime;
  late InAppWebViewSourceLiveCapturePort _port;
  late SourceLiveCapturePackageCoordinator _packageCoordinator;

  var _generation = 0;
  var _closed = false;
  var _pending = false;

  bool get isClosed => _closed;
  bool get hasPendingCapture => _pending;

  @override
  Future<SourceRuntimeResult> capture(SourceLivePlayableSourcePlan plan) async {
    if (_closed) {
      return _failure(plan, 'playable_capture_closed');
    }

    // A rendered-document acquisition is a transient resource. The previous
    // WebView is closed as soon as its result is delivered, so create a fresh
    // generation before a later playback operation.
    if (_port.isClosed) {
      _createCapturePort();
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
          maxEvents: _boundedWebBudget(
            plan
                    .requestPlan
                    .installedPackage
                    .package
                    .securityPolicy
                    .budget
                    .maxRecords *
                4,
          ),
          maxCandidates: _boundedWebBudget(
            plan
                .requestPlan
                .installedPackage
                .package
                .securityPolicy
                .budget
                .maxRecords,
          ),
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
        // Public player pages may finish their hydration after the initial
        // load stop. Keep this bounded and aligned with the manual capture
        // fallback window while allowing the declared video element to appear.
        postLoadTimeout: const Duration(seconds: 20),
        initialHeaders: plan.requestPlan.request.headers,
      );
      packagePlan = SourceLiveCapturePackagePlan(
        installedPackage: plan.requestPlan.installedPackage,
        programId: plan.requestPlan.programId,
        webCaptureRequest: request,
      );
      admission = _packageCoordinator.prepare(packagePlan);
    } on Object {
      return _failure(plan, 'playable_capture_request_invalid');
    }

    final admittedRequest = admission.request;
    if (admittedRequest == null) {
      return _failure(
        plan,
        admission.reasonCode ?? 'playable_capture_not_admitted',
        status: _runtimeStatusFor(admission.status),
      );
    }

    _pending = true;
    _notifyListenersSafely();
    try {
      final captureResult = await _packageCoordinator.capture(packagePlan);
      if (!_isCurrent(generation)) {
        return _failure(plan, 'playable_capture_superseded');
      }
      return _documentRuntime.execute(
        installedPackage: plan.requestPlan.installedPackage,
        request: admittedRequest,
        captureResult: captureResult,
      );
    } on Object {
      return _failure(plan, 'playable_capture_failed');
    } finally {
      if (_isCurrent(generation)) {
        _pending = false;
        _packageCoordinator.close();
        _port.close();
        _notifyListenersSafely();
      }
    }
  }

  /// Mounts the current capture generation. The owning player page keeps the
  /// surface mounted while [hasPendingCapture] is true.
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

  void _createCapturePort() {
    _port = InAppWebViewSourceLiveCapturePort(
      browserPort: browserPort,
      viewBuilder: captureViewBuilder,
    );
    _packageCoordinator = SourceLiveCapturePackageCoordinator(
      wynimeVersion: wynimeVersion,
      port: _port,
    );
  }

  SourceRuntimeResult _failure(
    SourceLivePlayableSourcePlan plan,
    String code, {
    SourceRuntimeStatus status = SourceRuntimeStatus.failed,
  }) {
    final package = plan.requestPlan.installedPackage.package;
    final safeCode = RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(code)
        ? code
        : 'playable_capture_failed';
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.requestPlan.programId,
      status: status,
      records: const [],
      diagnostics: [
        SourceRuntimeDiagnostic(
          code: safeCode,
          message: 'The bounded live playable document could not be evaluated.',
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

  static int _boundedWebBudget(int value) {
    if (value < 1) return 1;
    if (value > 5000) return 5000;
    return value;
  }

  void _notifyListenersSafely() {
    try {
      notifyListeners();
    } on Object {
      // Listener failures must not alter capture admission or completion.
    }
  }
}

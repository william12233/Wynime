import 'package:flutter/widgets.dart';
import 'package:pub_semver/pub_semver.dart';

import '../../application/source_live_capture_package_coordinator.dart';
import '../../application/source_live_document_package_runtime.dart';
import '../../application/source_live_subject_coordinator.dart';
import '../../domain/models/source_live_capture_models.dart';
import '../../domain/models/source_live_capture_package_models.dart';
import '../../domain/models/source_runtime_models.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/source_package_runtime.dart';
import '../../domain/services/web_source_browser.dart';
import 'inapp_webview_source_live_capture_port.dart';

/// Supplies one bounded browser-rendered subject document when static HTTP
/// returns a JavaScript shell. Metadata and episode links are evaluated from
/// that same captured document by the existing declarative runtime.
final class InAppWebViewSourceLiveSubjectFallback extends ChangeNotifier
    implements SourceLiveSubjectDocumentFallback {
  InAppWebViewSourceLiveSubjectFallback({
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
  Future<SourceLiveSubjectDocumentFallbackResult> capture(
    SourceLiveSubjectPlan plan,
  ) async {
    if (_closed) {
      return _failurePair(plan, 'subject_capture_closed');
    }

    final generation = ++_generation;
    late final SourceLiveCapturePackagePlan metadataPlan;
    late final SourceLiveCapturePackageResult metadataAdmission;
    late final SourceLiveCapturePackageResult episodeAdmission;
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
      metadataPlan = SourceLiveCapturePackagePlan(
        installedPackage: plan.requestPlan.installedPackage,
        programId: plan.requestPlan.programId,
        webCaptureRequest: request,
      );
      final episodePlan = SourceLiveCapturePackagePlan(
        installedPackage: plan.requestPlan.installedPackage,
        programId: plan.mapping.episodeProgramId,
        webCaptureRequest: request,
      );
      metadataAdmission = _packageCoordinator.prepare(metadataPlan);
      episodeAdmission = _packageCoordinator.prepare(episodePlan);
    } on Object {
      return _failurePair(plan, 'subject_capture_request_invalid');
    }

    if (metadataAdmission.request == null) {
      return _failurePair(
        plan,
        metadataAdmission.reasonCode ?? 'subject_capture_not_admitted',
        status: _runtimeStatusFor(metadataAdmission.status),
      );
    }
    if (episodeAdmission.request == null) {
      return _failurePair(
        plan,
        episodeAdmission.reasonCode ?? 'subject_capture_not_admitted',
        status: _runtimeStatusFor(episodeAdmission.status),
      );
    }

    _pending = true;
    _notifyListenersSafely();
    try {
      final captureResult = await _packageCoordinator.capture(metadataPlan);
      if (!_isCurrent(generation)) {
        return _failurePair(plan, 'subject_capture_superseded');
      }
      final metadata = _documentRuntime.execute(
        installedPackage: plan.requestPlan.installedPackage,
        request: metadataAdmission.request!,
        captureResult: captureResult,
      );
      final episodeCaptureResult = SourceLiveCaptureResult(
        packageId: captureResult.packageId,
        packageVersion: captureResult.packageVersion,
        programId: episodeAdmission.request!.programId,
        status: captureResult.status,
        snapshot: captureResult.snapshot,
        reasonCode: captureResult.reasonCode,
      );
      final episodes = _documentRuntime.execute(
        installedPackage: plan.requestPlan.installedPackage,
        request: episodeAdmission.request!,
        captureResult: episodeCaptureResult,
      );
      return SourceLiveSubjectDocumentFallbackResult(
        metadata: metadata,
        episodes: episodes,
      );
    } on Object {
      return _failurePair(plan, 'subject_capture_failed');
    } finally {
      if (_isCurrent(generation)) {
        _pending = false;
        _notifyListenersSafely();
      }
    }
  }

  /// Mounts the current generation. The owning subject page keeps this
  /// surface mounted while [hasPendingCapture] is true; no document body is
  /// exposed through the widget tree.
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

  SourceLiveSubjectDocumentFallbackResult _failurePair(
    SourceLiveSubjectPlan plan,
    String code, {
    SourceRuntimeStatus status = SourceRuntimeStatus.failed,
  }) {
    return SourceLiveSubjectDocumentFallbackResult(
      metadata: _failure(plan, plan.requestPlan.programId, code, status),
      episodes: _failure(plan, plan.mapping.episodeProgramId, code, status),
    );
  }

  SourceRuntimeResult _failure(
    SourceLiveSubjectPlan plan,
    String programId,
    String code,
    SourceRuntimeStatus status,
  ) {
    final package = plan.requestPlan.installedPackage.package;
    final safeCode = RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(code)
        ? code
        : 'subject_capture_failed';
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: programId,
      status: status,
      records: const [],
      diagnostics: [
        SourceRuntimeDiagnostic(
          code: safeCode,
          message: 'The bounded live subject document could not be evaluated.',
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
      maxDocumentBytes.clamp(0, 1024 * 1024);

  void _notifyListenersSafely() {
    try {
      notifyListeners();
    } on Object {
      // Listener failures must not alter capture admission or completion.
    }
  }
}

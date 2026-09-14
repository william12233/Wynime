import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_live_capture_models.dart';
import '../domain/models/source_live_capture_package_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/services/source_live_capture.dart';
import 'source_live_capture_coordinator.dart';

/// Admits a live capture only for an enabled, consent-complete and compatible
/// installed package program.
///
/// Package lifecycle remains owned by [SourcePackageManager]. Capture
/// generation, stale-result handling and snapshot validation remain owned by
/// [SourceLiveCaptureCoordinator]. This class only performs the typed handoff
/// between those authorities and never executes source rules itself.
final class SourceLiveCapturePackageCoordinator {
  SourceLiveCapturePackageCoordinator({
    required this.wynimeVersion,
    required SourceLiveCapturePort port,
  }) : _captureCoordinator = SourceLiveCaptureCoordinator(port: port);

  final Version wynimeVersion;
  final SourceLiveCaptureCoordinator _captureCoordinator;

  bool get isClosed => _captureCoordinator.isClosed;

  /// Performs package/program/policy preflight without contacting the port.
  SourceLiveCapturePackageResult prepare(SourceLiveCapturePackagePlan plan) {
    final installed = plan.installedPackage;
    final package = installed.package;
    if (installed.requiresConsent || installed.requiresReconsent) {
      return _packageResult(
        plan,
        SourceLiveCapturePackageStatus.consentRequired,
        'consent_required',
      );
    }
    if (installed.status != SourcePackageStatus.enabled) {
      return _packageResult(
        plan,
        SourceLiveCapturePackageStatus.disabled,
        'package_disabled',
      );
    }
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _packageResult(
        plan,
        SourceLiveCapturePackageStatus.incompatible,
        'incompatible_wynime_version',
      );
    }

    try {
      package.programById(plan.programId);
    } on StateError {
      return _packageResult(
        plan,
        SourceLiveCapturePackageStatus.programNotFound,
        'program_not_found',
      );
    } on Object {
      return _packageResult(
        plan,
        SourceLiveCapturePackageStatus.failed,
        'package_preflight_failed',
      );
    }

    if (!package.securityPolicy.semanticallyEquals(
      plan.webCaptureRequest.securityPolicy,
    )) {
      return _packageResult(
        plan,
        SourceLiveCapturePackageStatus.invalidRequest,
        'capture_policy_mismatch',
      );
    }
    if (!package.securityPolicy.allowsUri(plan.webCaptureRequest.initialUri)) {
      return _packageResult(
        plan,
        SourceLiveCapturePackageStatus.invalidRequest,
        'initial_uri_not_allowed',
      );
    }

    return SourceLiveCapturePackageResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.programId,
      status: SourceLiveCapturePackageStatus.ready,
      request: SourceLiveCaptureRequest(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: plan.programId,
        webCaptureRequest: plan.webCaptureRequest,
      ),
    );
  }

  /// Runs one admitted capture through the existing generation-scoped
  /// coordinator. Package preflight failures never contact the port.
  Future<SourceLiveCaptureResult> capture(
    SourceLiveCapturePackagePlan plan,
  ) async {
    final admission = prepare(plan);
    final request = admission.request;
    if (request == null) {
      return SourceLiveCaptureResult(
        packageId: plan.installedPackage.package.packageId,
        packageVersion: plan.installedPackage.package.version,
        programId: plan.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: admission.reasonCode,
      );
    }
    try {
      return await _captureCoordinator.capture(request);
    } on Object {
      return SourceLiveCaptureResult(
        packageId: request.packageId,
        packageVersion: request.packageVersion,
        programId: request.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: 'capture_failed',
      );
    }
  }

  /// Invalidates all in-flight work through the existing capture authority.
  void close() => _captureCoordinator.close();

  SourceLiveCapturePackageResult _packageResult(
    SourceLiveCapturePackagePlan plan,
    SourceLiveCapturePackageStatus status,
    String reasonCode,
  ) => SourceLiveCapturePackageResult(
    packageId: plan.installedPackage.package.packageId,
    packageVersion: plan.installedPackage.package.version,
    programId: plan.programId,
    status: status,
    reasonCode: reasonCode,
  );
}

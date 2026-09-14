import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_http_models.dart';
import '../domain/models/source_package_manager_models.dart';

/// One explicit live HTTP request paired with the installed source program
/// that is allowed to receive its response.
final class SourceLiveHttpRequestPlan {
  SourceLiveHttpRequestPlan({
    required this.installedPackage,
    required String programId,
    required this.request,
  }) : programId = _programId(programId);

  final InstalledSourcePackage installedPackage;
  final String programId;
  final SourceHttpRequest request;

  String get identityKey =>
      '${installedPackage.package.packageId}@${installedPackage.package.version}/$programId';

  static String _programId(String value) {
    final normalized = value.trim();
    if (value != normalized ||
        !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        'programId',
        'Must be a lower-case source program identifier.',
      );
    }
    return normalized;
  }
}

enum SourceLiveHttpRequestStatus {
  ready,
  consentRequired,
  disabled,
  incompatible,
  programNotFound,
  invalidRequest,
  failed,
}

/// A bounded package-admission result. Only a ready result retains the exact
/// request; every rejected result retains one safe reason code and no URI or
/// header values.
final class SourceLiveHttpRequestResult {
  SourceLiveHttpRequestResult({
    required this.packageId,
    required this.packageVersion,
    required this.programId,
    required this.status,
    this.request,
    this.reasonCode,
  }) {
    final isReady = status == SourceLiveHttpRequestStatus.ready;
    if (isReady) {
      if (request == null || reasonCode != null) {
        throw ArgumentError(
          'A ready live HTTP result requires one request and no reason.',
        );
      }
      return;
    }
    if (request != null || reasonCode == null || !_safeToken(reasonCode!)) {
      throw ArgumentError(
        'A non-ready live HTTP result requires one safe reason and no request.',
      );
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceLiveHttpRequestStatus status;
  final SourceHttpRequest? request;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageId': packageId,
    'packageVersion': packageVersion.toString(),
    'programId': programId,
    'status': status.name,
    'hasRequest': request != null,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

/// Performs the package/lifecycle/policy handoff before any live HTTP I/O.
///
/// This coordinator does not execute a source program and does not infer a
/// request from a package ID. The caller must supply an explicit request whose
/// security policy is exactly the installed package policy.
final class SourceLiveHttpRequestCoordinator {
  const SourceLiveHttpRequestCoordinator({required this.wynimeVersion});

  final Version wynimeVersion;

  SourceLiveHttpRequestResult prepare(SourceLiveHttpRequestPlan plan) {
    final installed = plan.installedPackage;
    final package = installed.package;
    if (installed.requiresConsent || installed.requiresReconsent) {
      return _rejected(
        plan,
        SourceLiveHttpRequestStatus.consentRequired,
        'consent_required',
      );
    }
    if (installed.status != SourcePackageStatus.enabled) {
      return _rejected(
        plan,
        SourceLiveHttpRequestStatus.disabled,
        'package_disabled',
      );
    }
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _rejected(
        plan,
        SourceLiveHttpRequestStatus.incompatible,
        'incompatible_wynime_version',
      );
    }
    try {
      package.programById(plan.programId);
    } on StateError {
      return _rejected(
        plan,
        SourceLiveHttpRequestStatus.programNotFound,
        'program_not_found',
      );
    } on Object {
      return _rejected(
        plan,
        SourceLiveHttpRequestStatus.failed,
        'package_preflight_failed',
      );
    }

    if (!package.securityPolicy.semanticallyEquals(
      plan.request.securityPolicy,
    )) {
      return _rejected(
        plan,
        SourceLiveHttpRequestStatus.invalidRequest,
        'request_policy_mismatch',
      );
    }
    if (!package.securityPolicy.allowsUri(plan.request.uri)) {
      return _rejected(
        plan,
        SourceLiveHttpRequestStatus.invalidRequest,
        'request_uri_not_allowed',
      );
    }

    return SourceLiveHttpRequestResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.programId,
      status: SourceLiveHttpRequestStatus.ready,
      request: plan.request,
    );
  }

  SourceLiveHttpRequestResult _rejected(
    SourceLiveHttpRequestPlan plan,
    SourceLiveHttpRequestStatus status,
    String reasonCode,
  ) {
    final package = plan.installedPackage.package;
    return SourceLiveHttpRequestResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.programId,
      status: status,
      reasonCode: reasonCode,
    );
  }
}

import 'package:pub_semver/pub_semver.dart';

import 'source_live_capture_models.dart';
import 'source_package_manager_models.dart';
import 'web_capture_models.dart';

/// The package-level preflight state before a live capture can be mounted.
enum SourceLiveCapturePackageStatus {
  ready,
  consentRequired,
  disabled,
  incompatible,
  programNotFound,
  invalidRequest,
  failed,
}

/// One explicit request to capture through an installed source package.
///
/// The caller supplies the already validated WebView request, while the
/// application coordinator verifies that its policy is exactly the policy of
/// the installed package before it is passed to the platform boundary.
final class SourceLiveCapturePackagePlan {
  SourceLiveCapturePackagePlan({
    required this.installedPackage,
    required String programId,
    required this.webCaptureRequest,
  }) : programId = _programId(programId);

  final InstalledSourcePackage installedPackage;
  final String programId;
  final WebCaptureRequest webCaptureRequest;

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

/// A bounded result of package admission. A ready result owns the exact
/// request that may be sent to the existing live-capture coordinator; all
/// other states retain only one safe reason code.
final class SourceLiveCapturePackageResult {
  SourceLiveCapturePackageResult({
    required this.packageId,
    required this.packageVersion,
    required this.programId,
    required this.status,
    this.request,
    this.reasonCode,
  }) {
    final isReady = status == SourceLiveCapturePackageStatus.ready;
    if (isReady) {
      if (request == null || reasonCode != null) {
        throw ArgumentError(
          'A ready package result requires one request and no reason.',
        );
      }
    } else if (request != null ||
        reasonCode == null ||
        !_safeToken(reasonCode!)) {
      throw ArgumentError(
        'A non-ready package result requires one safe reason and no request.',
      );
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceLiveCapturePackageStatus status;
  final SourceLiveCaptureRequest? request;
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
      RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value);
}

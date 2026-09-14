import 'source_package_manifest.dart';

enum SourcePackageStatus { disabled, enabled }

final class SourcePackageValidationResult {
  const SourcePackageValidationResult({
    required this.isValid,
    required this.code,
    this.requiresReconsent = false,
  });

  final bool isValid;
  final String code;
  final bool requiresReconsent;
}

final class InstalledSourcePackage {
  const InstalledSourcePackage({
    required this.package,
    required this.status,
    required this.requiresConsent,
    required this.requiresReconsent,
  });

  final SourcePackageManifest package;
  final SourcePackageStatus status;
  final bool requiresConsent;
  final bool requiresReconsent;

  InstalledSourcePackage copyWith({
    SourcePackageStatus? status,
    bool? requiresConsent,
    bool? requiresReconsent,
  }) {
    return InstalledSourcePackage(
      package: package,
      status: status ?? this.status,
      requiresConsent: requiresConsent ?? this.requiresConsent,
      requiresReconsent: requiresReconsent ?? this.requiresReconsent,
    );
  }
}

final class SourcePackageManagerException implements Exception {
  const SourcePackageManagerException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'SourcePackageManagerException($code): $message';
}

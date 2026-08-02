import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_rule_program.dart';
import 'source_security_policy.dart';

final class SourcePackageSignatureMetadata {
  SourcePackageSignatureMetadata({
    required String declaredSignerId,
    required String keyId,
    required String algorithm,
    required String signatureBase64,
  }) : declaredSignerId = _requireNonEmpty(declaredSignerId, 'declaredSignerId', 128),
       keyId = _requireNonEmpty(keyId, 'keyId', 128),
       algorithm = _requireNonEmpty(algorithm, 'algorithm', 32),
       signatureBase64 = _requireNonEmpty(signatureBase64, 'signatureBase64', 4096);

  final String declaredSignerId;
  final String keyId;
  final String algorithm;
  final String signatureBase64;

  static String _requireNonEmpty(String value, String name, int maxLength) {
    final trimmed = value.trim();
    if (trimmed.isEmpty || trimmed.length > maxLength) {
      throw ArgumentError.value(
        value,
        name,
        'Must contain between 1 and $maxLength characters.',
      );
    }
    return trimmed;
  }
}

final class SourcePackageManifest {
  SourcePackageManifest({
    required this.schemaVersion,
    required String packageId,
    required String displayName,
    required this.version,
    required this.wynimeVersionConstraint,
    required this.securityPolicy,
    required Iterable<SourceRuleProgram> programs,
    this.signatureMetadata,
  }) : packageId = packageId.trim(),
       displayName = displayName.trim(),
       programs = UnmodifiableListView(List<SourceRuleProgram>.unmodifiable(programs)) {
    if (schemaVersion != 1) {
      throw ArgumentError.value(
        schemaVersion,
        'schemaVersion',
        'Only source package schema version 1 is supported.',
      );
    }
    if (!RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(this.packageId) ||
        this.packageId.length > 128) {
      throw ArgumentError.value(
        packageId,
        'packageId',
        'Must be a lower-case package identifier.',
      );
    }
    if (this.displayName.isEmpty || this.displayName.length > 80) {
      throw ArgumentError.value(
        displayName,
        'displayName',
        'Must contain between 1 and 80 characters.',
      );
    }
    if (this.programs.isEmpty) {
      throw ArgumentError.value(programs, 'programs', 'Must not be empty.');
    }
    final ids = <String>{};
    for (final program in this.programs) {
      if (!ids.add(program.programId)) {
        throw ArgumentError.value(
          program.programId,
          'programs',
          'Program IDs must be unique.',
        );
      }
    }
  }

  final int schemaVersion;
  final String packageId;
  final String displayName;
  final Version version;
  final VersionConstraint wynimeVersionConstraint;
  final SourceSecurityPolicy securityPolicy;
  final UnmodifiableListView<SourceRuleProgram> programs;
  final SourcePackageSignatureMetadata? signatureMetadata;

  bool isCompatibleWith(Version wynimeVersion) {
    return wynimeVersionConstraint.allows(wynimeVersion);
  }

  SourceRuleProgram programById(String programId) {
    return programs.firstWhere(
      (program) => program.programId == programId,
      orElse: () => throw StateError('Source program not found: $programId'),
    );
  }
}

final class SourcePackageFormatException implements Exception {
  SourcePackageFormatException(this.path, this.message);

  final String path;
  final String message;

  @override
  String toString() => 'SourcePackageFormatException($path): $message';
}

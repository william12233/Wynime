import 'dart:collection';
import 'dart:convert';

import 'package:pub_semver/pub_semver.dart';

import 'source_rule_program.dart';
import 'source_package_live_operations.dart';
import 'source_security_policy.dart';

final class SourcePackageSignatureMetadata {
  SourcePackageSignatureMetadata({
    required String declaredSignerId,
    required String keyId,
    required String algorithm,
    required String signatureBase64,
  }) : declaredSignerId = _requireNonEmpty(
         declaredSignerId,
         'declaredSignerId',
         128,
       ),
       keyId = _requireNonEmpty(keyId, 'keyId', 128),
       algorithm = _validateAlgorithm(algorithm),
       signatureBase64 = _validateSignature(signatureBase64);

  final String declaredSignerId;
  final String keyId;
  final String algorithm;
  final String signatureBase64;

  static String _validateAlgorithm(String value) {
    final algorithm = _requireNonEmpty(value, 'algorithm', 32);
    if (algorithm != 'ed25519') {
      throw ArgumentError.value(
        value,
        'algorithm',
        'Only ed25519 signature metadata is supported.',
      );
    }
    return algorithm;
  }

  static String _validateSignature(String value) {
    final signature = _requireNonEmpty(value, 'signatureBase64', 4096);
    try {
      if (base64Decode(signature).length != 64) {
        throw const FormatException(
          'Ed25519 signatures must contain 64 bytes.',
        );
      }
    } on FormatException catch (error) {
      throw ArgumentError.value(
        value,
        'signatureBase64',
        'Invalid Ed25519 signature metadata: ${error.message}',
      );
    }
    return signature;
  }

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
    Iterable<SourcePackageLiveOperation> liveOperations = const [],
    this.signatureMetadata,
  }) : packageId = packageId,
       displayName = displayName.trim(),
       programs = UnmodifiableListView(
         List<SourceRuleProgram>.unmodifiable(programs),
       ),
       liveOperations = UnmodifiableListView(
         List<SourcePackageLiveOperation>.unmodifiable(liveOperations),
       ) {
    if (schemaVersion != 1 && schemaVersion != 2) {
      throw ArgumentError.value(
        schemaVersion,
        'schemaVersion',
        'Only source package schema versions 1 and 2 are supported.',
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
    if (this.programs.isEmpty || this.programs.length > 32) {
      throw ArgumentError.value(
        programs,
        'programs',
        'Must contain between 1 and 32 programs.',
      );
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
      if (program.resultLimit > securityPolicy.budget.maxRecords) {
        throw ArgumentError.value(
          program.resultLimit,
          'programs',
          'Program ${program.programId} exceeds maxRecords.',
        );
      }
      for (final field in program.fields) {
        final capture = field.regexCapture;
        if (capture != null &&
            capture.pattern.length >
                securityPolicy.budget.maxRegexPatternChars) {
          throw ArgumentError.value(
            capture.pattern.length,
            'programs',
            'Regex in ${program.programId}.${field.name} exceeds the pattern budget.',
          );
        }
      }
    }

    if (this.liveOperations.length > 3 ||
        (schemaVersion == 2 && this.liveOperations.isEmpty)) {
      throw ArgumentError.value(
        liveOperations,
        'liveOperations',
        'Schema version 2 requires between 1 and 3 live operations.',
      );
    }
    if (schemaVersion == 1 && this.liveOperations.isNotEmpty) {
      throw ArgumentError(
        'Schema version 1 packages must not declare live operations.',
      );
    }

    final operationKinds = <SourcePackageLiveOperationKind>{};
    for (final operation in this.liveOperations) {
      if (!operationKinds.add(operation.kind)) {
        throw ArgumentError.value(
          operation.kind,
          'liveOperations',
          'Each live operation kind may be declared only once.',
        );
      }
      final SourceRuleProgram program;
      try {
        program = programById(operation.programId);
      } on StateError {
        throw ArgumentError.value(
          operation.programId,
          'liveOperations',
          'Every live operation must reference an existing program.',
        );
      }
      final fieldNames = program.fields.map((field) => field.name).toSet();
      if (!fieldNames.containsAll(operation.mappingFieldNames)) {
        throw ArgumentError.value(
          operation.mappingFieldNames,
          'liveOperations',
          'Every operation mapping field must exist in its program.',
        );
      }
      final allowedPlaceholders = switch (operation.kind) {
        SourcePackageLiveOperationKind.search => const {'query'},
        SourcePackageLiveOperationKind.episode => const {
          'sourceId',
          'lineId',
          'subjectId',
          'episodeId',
        },
        SourcePackageLiveOperationKind.playableSource => const {
          'sourceId',
          'lineId',
          'subjectId',
          'episodeId',
        },
      };
      if (!allowedPlaceholders.containsAll(
        operation.requestTemplate.placeholders,
      )) {
        throw ArgumentError.value(
          operation.requestTemplate.placeholders,
          'liveOperations',
          'The operation contains an unsupported input placeholder.',
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
  final UnmodifiableListView<SourcePackageLiveOperation> liveOperations;
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

  SourcePackageLiveOperation? liveOperationByKind(
    SourcePackageLiveOperationKind kind,
  ) {
    for (final operation in liveOperations) {
      if (operation.kind == kind) return operation;
    }
    return null;
  }

  /// Live operation metadata is package authority, not cosmetic metadata.
  /// Any change therefore requires the same explicit re-consent boundary as a
  /// security-policy change, including a schema-v1 to schema-v2 transition.
  bool requiresReconsentComparedTo(SourcePackageManifest previous) {
    final current = liveOperations.map(_liveOperationKey).toSet();
    final prior = previous.liveOperations.map(_liveOperationKey).toSet();
    return schemaVersion != previous.schemaVersion ||
        current.length != prior.length ||
        !current.containsAll(prior);
  }

  static String _liveOperationKey(SourcePackageLiveOperation operation) {
    return jsonEncode([
      operation.kind.name,
      operation.programId,
      operation.requestTemplate.template,
      operation.mappingFieldNames,
    ]);
  }
}

final class SourcePackageFormatException implements Exception {
  SourcePackageFormatException(this.path, this.message);

  final String path;
  final String message;

  @override
  String toString() => 'SourcePackageFormatException($path): $message';
}

import 'package:pub_semver/pub_semver.dart';

import '../../domain/models/source_package_manager_models.dart';
import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_runtime_models.dart';
import '../../domain/services/source_package_runtime.dart';
import 'source_fixture_rule_engine.dart';

final class DeclarativeSourcePackageRuntime implements SourcePackageRuntime {
  const DeclarativeSourcePackageRuntime({required this.wynimeVersion});

  final Version wynimeVersion;
  static const _evaluator = SourceFixtureRuleEngine();

  @override
  SourceRuntimeResult executeFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
  }) {
    final package = installedPackage.package;
    final normalizedProgramId = programId.trim();
    if (installedPackage.status != SourcePackageStatus.enabled) {
      return _preflightResult(
        package: package,
        programId: normalizedProgramId,
        status: SourceRuntimeStatus.disabled,
        code: 'package_disabled',
        message: 'The source package is not enabled.',
      );
    }
    if (installedPackage.requiresConsent ||
        installedPackage.requiresReconsent) {
      return _preflightResult(
        package: package,
        programId: normalizedProgramId,
        status: SourceRuntimeStatus.consentRequired,
        code: 'consent_required',
        message: 'The source package requires explicit consent.',
      );
    }
    if (!package.isCompatibleWith(wynimeVersion)) {
      return _preflightResult(
        package: package,
        programId: normalizedProgramId,
        status: SourceRuntimeStatus.incompatible,
        code: 'incompatible_wynime_version',
        message: 'The source package is incompatible with this Wynime version.',
      );
    }

    if (!_programIdPattern.hasMatch(normalizedProgramId)) {
      return _failureResult(
        package: package,
        programId: normalizedProgramId,
        code: 'program_not_found',
      );
    }

    final program = _findProgram(package, normalizedProgramId);
    if (program == null) {
      return _preflightResult(
        package: package,
        programId: normalizedProgramId,
        status: SourceRuntimeStatus.failed,
        code: 'program_not_found',
        message: 'The requested source program was not found.',
      );
    }

    try {
      final evaluation = _evaluator.evaluate(
        package: package,
        program: program,
        fixture: fixture,
      );
      final diagnostics = evaluation.diagnostics
          .map(
            (diagnostic) => SourceRuntimeDiagnostic(
              code: _safeCode(diagnostic.code),
              message: _messageFor(diagnostic.code),
              recordIndex: diagnostic.recordIndex,
              fieldName: diagnostic.fieldName,
            ),
          )
          .toList(growable: false);
      return SourceRuntimeResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: normalizedProgramId,
        status: evaluation.records.isEmpty
            ? SourceRuntimeStatus.notFound
            : SourceRuntimeStatus.available,
        records: evaluation.records.map(
          (record) => SourceRuntimeRecord(record.values),
        ),
        diagnostics: diagnostics,
        consumedSteps: evaluation.consumedSteps,
        selectorMatches: evaluation.selectorMatches,
      );
    } on SourceRuleSecurityException catch (error) {
      return _failureResult(
        package: package,
        programId: normalizedProgramId,
        code: error.code,
      );
    } on SourceRuleEvaluationException catch (error) {
      return _failureResult(
        package: package,
        programId: normalizedProgramId,
        code: error.code,
      );
    } on Object {
      return _failureResult(
        package: package,
        programId: normalizedProgramId,
        code: 'runtime_failed',
      );
    }
  }

  SourceRuleProgram? _findProgram(
    SourcePackageManifest package,
    String programId,
  ) {
    try {
      return package.programById(programId);
    } on StateError {
      return null;
    }
  }

  SourceRuntimeResult _preflightResult({
    required SourcePackageManifest package,
    required String programId,
    required SourceRuntimeStatus status,
    required String code,
    required String message,
  }) {
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: _safeProgramId(programId),
      status: status,
      records: const [],
      diagnostics: [SourceRuntimeDiagnostic(code: code, message: message)],
      consumedSteps: 0,
      selectorMatches: 0,
    );
  }

  SourceRuntimeResult _failureResult({
    required SourcePackageManifest package,
    required String programId,
    required String code,
  }) {
    return _preflightResult(
      package: package,
      programId: programId,
      status: SourceRuntimeStatus.failed,
      code: _safeCode(code),
      message: _messageFor(code),
    );
  }

  static String _safeCode(String code) {
    final normalized = code.trim();
    if (normalized.length <= 64 &&
        RegExp(r'^[a-z][a-z0-9_]*$').hasMatch(normalized)) {
      return normalized;
    }
    return 'runtime_failed';
  }

  static String _safeProgramId(String programId) {
    final normalized = programId.trim();
    if (_programIdPattern.hasMatch(normalized)) {
      return normalized;
    }
    return 'unknown';
  }

  static final _programIdPattern = RegExp(r'^[a-z][a-z0-9_-]{0,63}$');

  static String _messageFor(String code) {
    return switch (code) {
      'uri_not_allowed' => 'The fixture URI is outside the package allowlist.',
      'redirect_budget_exceeded' => 'The fixture redirect budget was exceeded.',
      'redirect_uri_not_allowed' =>
        'A fixture redirect is outside the package allowlist.',
      'document_budget_exceeded' => 'The fixture document exceeds its budget.',
      'record_budget_exceeded' => 'The source record budget was exceeded.',
      'html_parse_failed' ||
      'json_parse_failed' => 'The fixture document could not be parsed.',
      'invalid_css_selector' ||
      'invalid_json_path' => 'The source selector could not be evaluated.',
      'multiple_field_values' =>
        'A source field returned multiple values unexpectedly.',
      'invalid_regex' =>
        'The source regular expression could not be evaluated.',
      'required_field_missing' => 'A required source field was not found.',
      'selector_match_budget_exceeded' =>
        'The source selector match budget was exceeded.',
      'evaluation_step_budget_exceeded' =>
        'The source evaluation step budget was exceeded.',
      _ => 'Source fixture evaluation failed.',
    };
  }
}

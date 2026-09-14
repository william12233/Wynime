import '../../domain/models/source_episode_normalization_models.dart';
import '../../domain/models/source_identity.dart';
import '../../domain/models/source_models.dart';
import '../../domain/models/source_runtime_models.dart';
import '../../domain/services/source_episode_normalizer.dart';

final class DeclarativeSourceEpisodeNormalizer
    implements SourceEpisodeNormalizer {
  const DeclarativeSourceEpisodeNormalizer();

  static const _maxIdentityPartLength = 128;
  static const _maxTitleLength = 256;
  static const _maxDiagnostics = 1000;
  static final _packageIdPattern = RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$');
  static final _programIdPattern = RegExp(r'^[a-z][a-z0-9_-]{0,63}$');
  static final _fieldNamePattern = RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$');

  @override
  SourceEpisodeNormalizationResult normalizeEpisodes({
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeFieldMapping mapping,
  }) {
    final packageId = _safePackageId(runtimeResult.packageId);
    final programId = _safeProgramId(runtimeResult.programId);
    if (packageId == null) {
      return _failureResult(
        runtimeResult: runtimeResult,
        packageId: 'unknown',
        programId: programId ?? 'unknown',
        diagnostics: [
          SourceEpisodeNormalizationDiagnostic(
            code: 'invalid_source_identity',
            message: 'The source package identity is invalid.',
          ),
        ],
      );
    }
    if (programId == null) {
      return _failureResult(
        runtimeResult: runtimeResult,
        packageId: packageId,
        programId: 'unknown',
        diagnostics: [
          SourceEpisodeNormalizationDiagnostic(
            code: 'invalid_program_identity',
            message: 'The source program identity is invalid.',
          ),
        ],
      );
    }

    final status = _statusFor(runtimeResult.status);
    final runtimeDiagnostics = _runtimeDiagnostics(runtimeResult);
    if (status != SourceEpisodeNormalizationStatus.available) {
      return SourceEpisodeNormalizationResult(
        packageId: packageId,
        packageVersion: runtimeResult.packageVersion,
        programId: programId,
        status: status,
        results: const [],
        diagnostics: runtimeDiagnostics,
      );
    }

    if (runtimeResult.records.isEmpty) {
      return SourceEpisodeNormalizationResult(
        packageId: packageId,
        packageVersion: runtimeResult.packageVersion,
        programId: programId,
        status: SourceEpisodeNormalizationStatus.notFound,
        results: const [],
        diagnostics: runtimeDiagnostics,
      );
    }

    final results = <SourceEpisode>[];
    final diagnostics = <SourceEpisodeNormalizationDiagnostic>[];
    for (final diagnostic in runtimeDiagnostics) {
      _addDiagnostic(diagnostics, diagnostic);
    }
    final seenIdentities = <SourceEpisodeIdentity>{};

    for (var index = 0; index < runtimeResult.records.length; index++) {
      final values = runtimeResult.records[index].values;
      final lineId = _normalizeValue(values[mapping.lineIdField]);
      final subjectId = _normalizeValue(values[mapping.subjectIdField]);
      final episodeId = _normalizeValue(values[mapping.episodeIdField]);
      final title = _normalizeTitle(values[mapping.titleField]);
      if (lineId == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.lineIdField),
        );
      }
      if (subjectId == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.subjectIdField),
        );
      }
      if (episodeId == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.episodeIdField),
        );
      }
      if (title == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.titleField),
        );
      }
      if (lineId == null ||
          subjectId == null ||
          episodeId == null ||
          title == null) {
        continue;
      }

      final identity = SourceEpisodeIdentity(
        sourceId: packageId,
        lineId: lineId,
        subjectId: subjectId,
        episodeId: episodeId,
      );
      if (!seenIdentities.add(identity)) {
        _addDiagnostic(
          diagnostics,
          SourceEpisodeNormalizationDiagnostic(
            code: 'duplicate_episode_identity',
            message: 'A duplicate normalized episode was ignored.',
            recordIndex: index,
            fieldName: mapping.episodeIdField,
          ),
        );
        continue;
      }
      results.add(SourceEpisode(identity: identity, title: title));
    }

    if (results.isEmpty && runtimeResult.records.isNotEmpty) {
      _addDiagnostic(
        diagnostics,
        SourceEpisodeNormalizationDiagnostic(
          code: 'normalization_failed',
          message: 'No source episode passed normalization.',
        ),
      );
      return SourceEpisodeNormalizationResult(
        packageId: packageId,
        packageVersion: runtimeResult.packageVersion,
        programId: programId,
        status: SourceEpisodeNormalizationStatus.failed,
        results: const [],
        diagnostics: diagnostics,
      );
    }

    return SourceEpisodeNormalizationResult(
      packageId: packageId,
      packageVersion: runtimeResult.packageVersion,
      programId: programId,
      status: results.isEmpty
          ? SourceEpisodeNormalizationStatus.notFound
          : SourceEpisodeNormalizationStatus.available,
      results: results,
      diagnostics: diagnostics,
    );
  }

  SourceEpisodeNormalizationResult _failureResult({
    required SourceRuntimeResult runtimeResult,
    required String packageId,
    required String programId,
    required Iterable<SourceEpisodeNormalizationDiagnostic> diagnostics,
  }) {
    return SourceEpisodeNormalizationResult(
      packageId: packageId,
      packageVersion: runtimeResult.packageVersion,
      programId: programId,
      status: SourceEpisodeNormalizationStatus.failed,
      results: const [],
      diagnostics: diagnostics,
    );
  }

  static SourceEpisodeNormalizationDiagnostic _invalidFieldDiagnostic(
    int recordIndex,
    String fieldName,
  ) {
    return SourceEpisodeNormalizationDiagnostic(
      code: 'normalized_field_invalid',
      message: 'A normalized source field is missing or outside its limit.',
      recordIndex: recordIndex,
      fieldName: fieldName,
    );
  }

  static void _addDiagnostic(
    List<SourceEpisodeNormalizationDiagnostic> diagnostics,
    SourceEpisodeNormalizationDiagnostic diagnostic,
  ) {
    if (diagnostics.length < _maxDiagnostics) {
      diagnostics.add(diagnostic);
    }
  }

  static SourceEpisodeNormalizationStatus _statusFor(
    SourceRuntimeStatus status,
  ) {
    return switch (status) {
      SourceRuntimeStatus.available =>
        SourceEpisodeNormalizationStatus.available,
      SourceRuntimeStatus.notFound => SourceEpisodeNormalizationStatus.notFound,
      SourceRuntimeStatus.disabled => SourceEpisodeNormalizationStatus.disabled,
      SourceRuntimeStatus.consentRequired =>
        SourceEpisodeNormalizationStatus.consentRequired,
      SourceRuntimeStatus.incompatible =>
        SourceEpisodeNormalizationStatus.incompatible,
      SourceRuntimeStatus.failed => SourceEpisodeNormalizationStatus.failed,
    };
  }

  static List<SourceEpisodeNormalizationDiagnostic> _runtimeDiagnostics(
    SourceRuntimeResult runtimeResult,
  ) {
    return runtimeResult.diagnostics
        .map(
          (diagnostic) => SourceEpisodeNormalizationDiagnostic(
            code: _safeDiagnosticCode(diagnostic.code),
            message: _safeDiagnosticMessage(diagnostic.code),
            recordIndex: _safeRecordIndex(diagnostic.recordIndex),
            fieldName: _safeFieldName(diagnostic.fieldName),
          ),
        )
        .toList(growable: false);
  }

  static String? _normalizeValue(String? value) {
    if (value == null) {
      return null;
    }
    final normalized = value.trim();
    if (normalized.isEmpty ||
        normalized.length > _maxIdentityPartLength ||
        _hasControlCharacter(normalized)) {
      return null;
    }
    return normalized;
  }

  static String? _normalizeTitle(String? value) {
    if (value == null) {
      return null;
    }
    final normalized = value.trim();
    if (normalized.isEmpty ||
        normalized.length > _maxTitleLength ||
        _hasControlCharacter(normalized)) {
      return null;
    }
    final compact = normalized.replaceAll(RegExp(r'\s+'), ' ').trim();
    return compact.isEmpty || compact.length > _maxTitleLength ? null : compact;
  }

  static bool _hasControlCharacter(String value) {
    return value.codeUnits.any(
      (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
    );
  }

  static String? _safePackageId(String value) {
    final normalized = value.trim();
    return normalized.length <= 128 && _packageIdPattern.hasMatch(normalized)
        ? normalized
        : null;
  }

  static String? _safeProgramId(String value) {
    final normalized = value.trim();
    return normalized.length <= 64 && _programIdPattern.hasMatch(normalized)
        ? normalized
        : null;
  }

  static String _safeDiagnosticCode(String value) {
    final normalized = value.trim();
    const knownCodes = {
      'required_field_missing',
      'uri_not_allowed',
      'redirect_budget_exceeded',
      'redirect_uri_not_allowed',
      'document_budget_exceeded',
      'record_budget_exceeded',
      'incompatible_wynime_version',
      'program_not_found',
      'json_parse_failed',
      'html_parse_failed',
      'invalid_css_selector',
      'multiple_field_values',
      'runtime_failed',
    };
    return knownCodes.contains(normalized)
        ? normalized
        : 'source_runtime_diagnostic';
  }

  static String _safeDiagnosticMessage(String code) {
    return switch (_safeDiagnosticCode(code)) {
      'required_field_missing' => 'A source field was not available.',
      'uri_not_allowed' ||
      'redirect_budget_exceeded' ||
      'redirect_uri_not_allowed' ||
      'document_budget_exceeded' ||
      'record_budget_exceeded' =>
        'The source fixture did not pass its declared security limits.',
      'incompatible_wynime_version' =>
        'The source package is incompatible with this Wynime version.',
      'program_not_found' => 'The requested source program was not found.',
      'json_parse_failed' ||
      'html_parse_failed' ||
      'invalid_css_selector' ||
      'multiple_field_values' => 'The source fixture could not be evaluated.',
      'runtime_failed' || 'source_runtime_diagnostic' =>
        'The source runtime reported a diagnostic.',
      _ => 'The source runtime reported a diagnostic.',
    };
  }

  static int? _safeRecordIndex(int? value) {
    return value != null && value < 1000 ? value : null;
  }

  static String? _safeFieldName(String? value) {
    if (value == null) {
      return null;
    }
    final normalized = value.trim();
    return _fieldNamePattern.hasMatch(normalized) ? normalized : null;
  }
}

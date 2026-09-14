import '../../domain/models/source_runtime_models.dart';
import '../../domain/models/source_models.dart';
import '../../domain/models/source_search_normalization_models.dart';
import '../../domain/services/source_search_normalizer.dart';

final class DeclarativeSourceSearchNormalizer
    implements SourceSearchNormalizer {
  const DeclarativeSourceSearchNormalizer();

  static const _maxSubjectIdLength = 128;
  static const _maxTitleLength = 256;
  static const _maxDiagnostics = 1000;
  static final _packageIdPattern = RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$');
  static final _programIdPattern = RegExp(r'^[a-z][a-z0-9_-]{0,63}$');
  static final _fieldNamePattern = RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$');

  @override
  SourceSearchNormalizationResult normalizeSearch({
    required SourceRuntimeResult runtimeResult,
    required SourceSearchFieldMapping mapping,
  }) {
    final packageId = _safePackageId(runtimeResult.packageId);
    final programId = _safeProgramId(runtimeResult.programId);
    if (packageId == null) {
      return _failureResult(
        runtimeResult: runtimeResult,
        packageId: 'unknown',
        programId: programId,
        diagnostics: [
          SourceSearchNormalizationDiagnostic(
            code: 'invalid_source_identity',
            message: 'The source package identity is invalid.',
          ),
        ],
      );
    }

    final status = _statusFor(runtimeResult.status);
    final runtimeDiagnostics = _runtimeDiagnostics(runtimeResult);
    if (status != SourceSearchNormalizationStatus.available) {
      return SourceSearchNormalizationResult(
        packageId: packageId,
        packageVersion: runtimeResult.packageVersion,
        programId: programId,
        status: status,
        results: const [],
        diagnostics: runtimeDiagnostics,
      );
    }

    if (runtimeResult.records.isEmpty) {
      return SourceSearchNormalizationResult(
        packageId: packageId,
        packageVersion: runtimeResult.packageVersion,
        programId: programId,
        status: SourceSearchNormalizationStatus.notFound,
        results: const [],
        diagnostics: runtimeDiagnostics,
      );
    }

    final results = <SourceSearchResult>[];
    final diagnostics = <SourceSearchNormalizationDiagnostic>[];
    for (final diagnostic in runtimeDiagnostics) {
      _addDiagnostic(diagnostics, diagnostic);
    }
    final seenSubjectIds = <String>{};

    for (var index = 0; index < runtimeResult.records.length; index++) {
      final values = runtimeResult.records[index].values;
      final subjectId = _normalizeValue(
        values[mapping.subjectIdField],
        maxLength: _maxSubjectIdLength,
      );
      final title = _normalizeTitle(values[mapping.titleField]);
      if (subjectId == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.subjectIdField),
        );
      }
      if (title == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.titleField),
        );
      }
      if (subjectId == null || title == null) {
        continue;
      }
      if (!seenSubjectIds.add(subjectId)) {
        _addDiagnostic(
          diagnostics,
          SourceSearchNormalizationDiagnostic(
            code: 'duplicate_subject_id',
            message: 'A duplicate normalized search result was ignored.',
            recordIndex: index,
            fieldName: mapping.subjectIdField,
          ),
        );
        continue;
      }
      results.add(
        SourceSearchResult(
          sourceId: packageId,
          subjectId: subjectId,
          title: title,
        ),
      );
    }

    if (results.isEmpty) {
      final hasInputRecords = runtimeResult.records.isNotEmpty;
      if (hasInputRecords) {
        _addDiagnostic(
          diagnostics,
          SourceSearchNormalizationDiagnostic(
            code: 'normalization_failed',
            message: 'No source search result passed normalization.',
          ),
        );
        return SourceSearchNormalizationResult(
          packageId: packageId,
          packageVersion: runtimeResult.packageVersion,
          programId: programId,
          status: SourceSearchNormalizationStatus.failed,
          results: const [],
          diagnostics: diagnostics,
        );
      }
    }

    return SourceSearchNormalizationResult(
      packageId: packageId,
      packageVersion: runtimeResult.packageVersion,
      programId: programId,
      status: results.isEmpty
          ? SourceSearchNormalizationStatus.notFound
          : SourceSearchNormalizationStatus.available,
      results: results,
      diagnostics: diagnostics,
    );
  }

  SourceSearchNormalizationResult _failureResult({
    required SourceRuntimeResult runtimeResult,
    required String packageId,
    required String programId,
    required Iterable<SourceSearchNormalizationDiagnostic> diagnostics,
  }) {
    return SourceSearchNormalizationResult(
      packageId: packageId,
      packageVersion: runtimeResult.packageVersion,
      programId: programId,
      status: SourceSearchNormalizationStatus.failed,
      results: const [],
      diagnostics: diagnostics,
    );
  }

  static SourceSearchNormalizationDiagnostic _invalidFieldDiagnostic(
    int recordIndex,
    String fieldName,
  ) {
    return SourceSearchNormalizationDiagnostic(
      code: 'normalized_field_invalid',
      message: 'A normalized source field is missing or outside its limit.',
      recordIndex: recordIndex,
      fieldName: fieldName,
    );
  }

  static void _addDiagnostic(
    List<SourceSearchNormalizationDiagnostic> diagnostics,
    SourceSearchNormalizationDiagnostic diagnostic,
  ) {
    if (diagnostics.length < _maxDiagnostics) {
      diagnostics.add(diagnostic);
    }
  }

  static SourceSearchNormalizationStatus _statusFor(
    SourceRuntimeStatus status,
  ) {
    return switch (status) {
      SourceRuntimeStatus.available =>
        SourceSearchNormalizationStatus.available,
      SourceRuntimeStatus.notFound => SourceSearchNormalizationStatus.notFound,
      SourceRuntimeStatus.disabled => SourceSearchNormalizationStatus.disabled,
      SourceRuntimeStatus.consentRequired =>
        SourceSearchNormalizationStatus.consentRequired,
      SourceRuntimeStatus.incompatible =>
        SourceSearchNormalizationStatus.incompatible,
      SourceRuntimeStatus.failed => SourceSearchNormalizationStatus.failed,
    };
  }

  static List<SourceSearchNormalizationDiagnostic> _runtimeDiagnostics(
    SourceRuntimeResult runtimeResult,
  ) {
    return runtimeResult.diagnostics
        .map(
          (diagnostic) => SourceSearchNormalizationDiagnostic(
            code: _safeDiagnosticCode(diagnostic.code),
            message: _safeDiagnosticMessage(diagnostic.code),
            recordIndex: _safeRecordIndex(diagnostic.recordIndex),
            fieldName: _safeFieldName(diagnostic.fieldName),
          ),
        )
        .toList(growable: false);
  }

  static String? _normalizeValue(String? value, {required int maxLength}) {
    if (value == null) {
      return null;
    }
    final normalized = value.trim();
    if (normalized.isEmpty ||
        normalized.length > maxLength ||
        _hasControlCharacter(normalized)) {
      return null;
    }
    return normalized;
  }

  static String? _normalizeTitle(String? value) {
    final normalized = _normalizeValue(value, maxLength: _maxTitleLength);
    if (normalized == null) {
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

  static String _safeProgramId(String value) {
    final normalized = value.trim();
    return normalized.length <= 64 && _programIdPattern.hasMatch(normalized)
        ? normalized
        : 'unknown';
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

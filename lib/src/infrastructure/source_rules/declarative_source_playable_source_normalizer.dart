import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/source_playable_source_normalizer.dart';

final class DeclarativeSourcePlayableSourceNormalizer
    implements SourcePlayableSourceNormalizer {
  const DeclarativeSourcePlayableSourceNormalizer();

  static const _maxSourceKeyLength = 128;
  static const _maxLabelLength = 256;
  static const _maxUriLength = 4096;
  static const _maxDiagnostics = 1000;
  static final _programIdPattern = RegExp(r'^[a-z][a-z0-9_-]{0,63}$');
  static final _fieldNamePattern = RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$');

  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) {
    final programId = _safeProgramId(runtimeResult.programId);
    if (runtimeResult.packageId != package.packageId ||
        runtimeResult.packageVersion != package.version) {
      return _failureResult(
        package: package,
        programId: programId ?? 'unknown',
        diagnostics: [
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'runtime_package_mismatch',
            message:
                'The runtime result does not belong to this source package.',
          ),
        ],
      );
    }
    if (programId == null) {
      return _failureResult(
        package: package,
        programId: 'unknown',
        diagnostics: [
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'invalid_program_identity',
            message: 'The source program identity is invalid.',
          ),
        ],
      );
    }
    final program = _findProgram(package, programId);
    if (program == null) {
      return _failureResult(
        package: package,
        programId: programId,
        diagnostics: [
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'program_not_found',
            message: 'The requested source program was not declared.',
          ),
        ],
      );
    }
    final undeclaredField = _firstUndeclaredField(program, mapping);
    if (undeclaredField != null) {
      return _failureResult(
        package: package,
        programId: programId,
        diagnostics: [
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'mapping_field_not_declared',
            message: 'A mapped field is not declared by the source program.',
            fieldName: undeclaredField,
          ),
        ],
      );
    }
    if (episode.sourceId != package.packageId) {
      return _failureResult(
        package: package,
        programId: programId,
        diagnostics: [
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'episode_source_mismatch',
            message:
                'The episode identity does not belong to this source package.',
          ),
        ],
      );
    }

    final status = _statusFor(runtimeResult.status);
    final runtimeDiagnostics = _runtimeDiagnostics(runtimeResult);
    if (status != SourcePlayableSourceNormalizationStatus.available) {
      return SourcePlayableSourceNormalizationResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: programId,
        status: status,
        results: const [],
        diagnostics: runtimeDiagnostics,
      );
    }
    if (runtimeResult.records.isEmpty) {
      return SourcePlayableSourceNormalizationResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: programId,
        status: SourcePlayableSourceNormalizationStatus.notFound,
        results: const [],
        diagnostics: runtimeDiagnostics,
      );
    }

    final results = <SourcePlayableSource>[];
    final diagnostics = <SourcePlayableSourceNormalizationDiagnostic>[];
    for (final diagnostic in runtimeDiagnostics) {
      _addDiagnostic(diagnostics, diagnostic);
    }
    final seenSourceKeys = <String>{};

    for (var index = 0; index < runtimeResult.records.length; index++) {
      final values = runtimeResult.records[index].values;
      final sourceKey = _normalizeText(
        values[mapping.sourceKeyField],
        _maxSourceKeyLength,
      );
      final label = _normalizeText(values[mapping.labelField], _maxLabelLength);
      final kind = _normalizeKind(values[mapping.kindField]);
      final mediaUri = _normalizeUri(values[mapping.mediaUriField], package);
      final pageUri = _normalizeUri(values[mapping.pageUriField], package);

      if (sourceKey == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.sourceKeyField),
        );
      }
      if (label == null) {
        _addDiagnostic(
          diagnostics,
          _invalidFieldDiagnostic(index, mapping.labelField),
        );
      }
      if (kind == null) {
        _addDiagnostic(
          diagnostics,
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'playable_kind_unsupported',
            message: 'The source playback kind is unsupported.',
            recordIndex: index,
            fieldName: mapping.kindField,
          ),
        );
      }
      if (mediaUri == null) {
        _addDiagnostic(
          diagnostics,
          _uriDiagnostic(
            index,
            mapping.mediaUriField,
            values[mapping.mediaUriField],
          ),
        );
      }
      if (pageUri == null) {
        _addDiagnostic(
          diagnostics,
          _uriDiagnostic(
            index,
            mapping.pageUriField,
            values[mapping.pageUriField],
          ),
        );
      }
      if (sourceKey == null ||
          label == null ||
          kind == null ||
          mediaUri == null ||
          pageUri == null) {
        continue;
      }
      if (!seenSourceKeys.add(sourceKey)) {
        _addDiagnostic(
          diagnostics,
          SourcePlayableSourceNormalizationDiagnostic(
            code: 'duplicate_playable_source_key',
            message: 'A duplicate playable source was ignored.',
            recordIndex: index,
            fieldName: mapping.sourceKeyField,
          ),
        );
        continue;
      }

      results.add(
        SourcePlayableSource(
          episode: episode,
          sourceKey: sourceKey,
          label: label,
          kind: kind,
          mediaUri: mediaUri,
          pageUri: pageUri,
        ),
      );
    }

    if (results.isEmpty) {
      _addDiagnostic(
        diagnostics,
        SourcePlayableSourceNormalizationDiagnostic(
          code: 'normalization_failed',
          message: 'No playable source passed normalization.',
        ),
      );
      return SourcePlayableSourceNormalizationResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: programId,
        status: SourcePlayableSourceNormalizationStatus.failed,
        results: const [],
        diagnostics: diagnostics,
      );
    }

    return SourcePlayableSourceNormalizationResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: programId,
      status: SourcePlayableSourceNormalizationStatus.available,
      results: results,
      diagnostics: diagnostics,
    );
  }

  SourcePlayableSourceNormalizationResult _failureResult({
    required SourcePackageManifest package,
    required String programId,
    required Iterable<SourcePlayableSourceNormalizationDiagnostic> diagnostics,
  }) {
    return SourcePlayableSourceNormalizationResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: programId,
      status: SourcePlayableSourceNormalizationStatus.failed,
      results: const [],
      diagnostics: diagnostics,
    );
  }

  static SourceRuleProgram? _findProgram(
    SourcePackageManifest package,
    String programId,
  ) {
    for (final program in package.programs) {
      if (program.programId == programId) {
        return program;
      }
    }
    return null;
  }

  static String? _firstUndeclaredField(
    SourceRuleProgram program,
    SourcePlayableSourceFieldMapping mapping,
  ) {
    final declaredFields = program.fields.map((field) => field.name).toSet();
    for (final field in [
      mapping.sourceKeyField,
      mapping.labelField,
      mapping.kindField,
      mapping.mediaUriField,
      mapping.pageUriField,
    ]) {
      if (!declaredFields.contains(field)) {
        return field;
      }
    }
    return null;
  }

  static SourcePlayableSourceNormalizationDiagnostic _invalidFieldDiagnostic(
    int recordIndex,
    String fieldName,
  ) {
    return SourcePlayableSourceNormalizationDiagnostic(
      code: 'playable_field_invalid',
      message: 'A playable source field is missing or outside its limit.',
      recordIndex: recordIndex,
      fieldName: fieldName,
    );
  }

  static SourcePlayableSourceNormalizationDiagnostic _uriDiagnostic(
    int recordIndex,
    String fieldName,
    String? raw,
  ) {
    final code = _parseUri(raw) == null
        ? 'playable_uri_invalid'
        : 'playable_uri_not_allowed';
    return SourcePlayableSourceNormalizationDiagnostic(
      code: code,
      message: code == 'playable_uri_invalid'
          ? 'A playable source URI is invalid.'
          : 'A playable source URI is outside the source allowlist.',
      recordIndex: recordIndex,
      fieldName: fieldName,
    );
  }

  static void _addDiagnostic(
    List<SourcePlayableSourceNormalizationDiagnostic> diagnostics,
    SourcePlayableSourceNormalizationDiagnostic diagnostic,
  ) {
    if (diagnostics.length < _maxDiagnostics) {
      diagnostics.add(diagnostic);
    }
  }

  static SourcePlayableSourceNormalizationStatus _statusFor(
    SourceRuntimeStatus status,
  ) {
    return switch (status) {
      SourceRuntimeStatus.available =>
        SourcePlayableSourceNormalizationStatus.available,
      SourceRuntimeStatus.notFound =>
        SourcePlayableSourceNormalizationStatus.notFound,
      SourceRuntimeStatus.disabled =>
        SourcePlayableSourceNormalizationStatus.disabled,
      SourceRuntimeStatus.consentRequired =>
        SourcePlayableSourceNormalizationStatus.consentRequired,
      SourceRuntimeStatus.incompatible =>
        SourcePlayableSourceNormalizationStatus.incompatible,
      SourceRuntimeStatus.failed =>
        SourcePlayableSourceNormalizationStatus.failed,
    };
  }

  static List<SourcePlayableSourceNormalizationDiagnostic> _runtimeDiagnostics(
    SourceRuntimeResult runtimeResult,
  ) {
    return runtimeResult.diagnostics
        .map(
          (diagnostic) => SourcePlayableSourceNormalizationDiagnostic(
            code: _safeDiagnosticCode(diagnostic.code),
            message: _safeDiagnosticMessage(diagnostic.code),
            recordIndex: _safeRecordIndex(diagnostic.recordIndex),
            fieldName: _safeFieldName(diagnostic.fieldName),
          ),
        )
        .toList(growable: false);
  }

  static String? _normalizeText(String? value, int maxLength) {
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

  static WebCandidateKind? _normalizeKind(String? value) {
    return switch (value?.trim().toLowerCase()) {
      'hls' => WebCandidateKind.hls,
      'video' => WebCandidateKind.video,
      'audio' => WebCandidateKind.audio,
      _ => null,
    };
  }

  static Uri? _normalizeUri(String? value, SourcePackageManifest package) {
    final uri = _parseUri(value);
    return uri != null && package.securityPolicy.allowsUri(uri) ? uri : null;
  }

  static Uri? _parseUri(String? value) {
    final normalized = value?.trim();
    if (normalized == null ||
        normalized.isEmpty ||
        normalized.length > _maxUriLength ||
        _hasControlCharacter(normalized)) {
      return null;
    }
    final uri = Uri.tryParse(normalized);
    if (uri == null ||
        (uri.scheme != 'https' && uri.scheme != 'http') ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty) {
      return null;
    }
    return uri;
  }

  static bool _hasControlCharacter(String value) {
    return value.codeUnits.any(
      (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
    );
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

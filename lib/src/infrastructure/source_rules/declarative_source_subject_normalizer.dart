import '../../domain/models/source_identity.dart';
import '../../domain/models/source_package_live_operations.dart';
import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_runtime_models.dart';
import '../../domain/models/source_subject_details_models.dart';
import '../../domain/models/source_rule_program.dart';
import '../../domain/services/source_subject_details_normalizer.dart';
import '../../domain/models/source_models.dart';

final class DeclarativeSourceSubjectNormalizer
    implements SourceSubjectDetailsNormalizer {
  const DeclarativeSourceSubjectNormalizer();

  @override
  SourceSubjectDetailsResult normalizeSubjectDetails({
    required SourcePackageManifest package,
    required SourceRuntimeResult metadataRuntimeResult,
    required SourceRuntimeResult episodeRuntimeResult,
    required SourceSubjectIdentity subject,
    required SourceSubjectDetailsFieldMapping mapping,
  }) {
    final diagnostics = <SourceSubjectDetailsDiagnostic>[];
    if (metadataRuntimeResult.packageId != package.packageId ||
        episodeRuntimeResult.packageId != package.packageId ||
        metadataRuntimeResult.packageVersion != package.version ||
        episodeRuntimeResult.packageVersion != package.version) {
      return _failed(
        package,
        metadataRuntimeResult.programId,
        'runtime_identity_mismatch',
      );
    }
    if (subject.sourceId != package.packageId) {
      return _failed(
        package,
        metadataRuntimeResult.programId,
        'subject_source_mismatch',
      );
    }

    final blocked = _isChallenge(metadataRuntimeResult)
        ? SourceSubjectDetailsStatus.challengeRequired
        : _blockedStatus(metadataRuntimeResult.status);
    if (blocked != null) {
      return _resultForStatus(
        package,
        metadataRuntimeResult.programId,
        blocked,
        _runtimeDiagnostics(metadataRuntimeResult),
      );
    }
    final episodeBlocked = _isChallenge(episodeRuntimeResult)
        ? SourceSubjectDetailsStatus.challengeRequired
        : _blockedStatus(episodeRuntimeResult.status);
    if (episodeBlocked != null) {
      return _resultForStatus(
        package,
        metadataRuntimeResult.programId,
        episodeBlocked,
        _runtimeDiagnostics(episodeRuntimeResult),
      );
    }
    if (metadataRuntimeResult.status != SourceRuntimeStatus.available ||
        episodeRuntimeResult.status != SourceRuntimeStatus.available) {
      return _resultForStatus(
        package,
        metadataRuntimeResult.programId,
        SourceSubjectDetailsStatus.notFound,
        [
          ..._runtimeDiagnostics(metadataRuntimeResult),
          ..._runtimeDiagnostics(episodeRuntimeResult),
        ],
      );
    }

    final metadataProgram = _program(package, metadataRuntimeResult.programId);
    final episodeProgram = _program(package, mapping.episodeProgramId);
    if (metadataProgram == null || episodeProgram == null) {
      return _failed(
        package,
        metadataRuntimeResult.programId,
        'program_not_found',
      );
    }
    if (!_hasField(metadataProgram, mapping.metadataTitleField) ||
        !_hasFields(episodeProgram, [
          mapping.lineIdField,
          mapping.subjectIdField,
          mapping.episodeIdField,
          mapping.episodeTitleField,
        ])) {
      return _failed(
        package,
        metadataRuntimeResult.programId,
        'mapping_field_not_declared',
      );
    }
    if (metadataRuntimeResult.records.isEmpty ||
        episodeRuntimeResult.records.isEmpty) {
      return _resultForStatus(
        package,
        metadataRuntimeResult.programId,
        SourceSubjectDetailsStatus.notFound,
        [
          ..._runtimeDiagnostics(metadataRuntimeResult),
          ..._runtimeDiagnostics(episodeRuntimeResult),
        ],
      );
    }

    final title = _text(
      metadataRuntimeResult.records.first.values[mapping.metadataTitleField],
      256,
    );
    if (title == null) {
      return _failed(
        package,
        metadataRuntimeResult.programId,
        'subject_title_invalid',
      );
    }

    final episodes = <SourceEpisode>[];
    final seen = <String>{};
    for (var index = 0; index < episodeRuntimeResult.records.length; index++) {
      final values = episodeRuntimeResult.records[index].values;
      final lineId = _text(values[mapping.lineIdField], 128);
      final subjectId = _text(values[mapping.subjectIdField], 128);
      final episodeId = _text(values[mapping.episodeIdField], 128);
      final episodeTitle = _text(values[mapping.episodeTitleField], 256);
      if (lineId == null ||
          subjectId == null ||
          episodeId == null ||
          episodeTitle == null) {
        _addDiagnostic(
          diagnostics,
          SourceSubjectDetailsDiagnostic(
            code: 'subject_episode_field_invalid',
            message: 'A subject episode field was missing or invalid.',
          ),
        );
        continue;
      }
      if (subjectId != subject.subjectId) {
        _addDiagnostic(
          diagnostics,
          SourceSubjectDetailsDiagnostic(
            code: 'subject_episode_identity_mismatch',
            message: 'A subject episode belonged to a different subject.',
          ),
        );
        continue;
      }
      final identity = SourceEpisodeIdentity(
        sourceId: package.packageId,
        lineId: lineId,
        subjectId: subject.subjectId,
        episodeId: episodeId,
      );
      final key = [lineId, episodeId].join('\u0000');
      if (seen.add(key)) {
        episodes.add(SourceEpisode(identity: identity, title: episodeTitle));
      }
      if (index == 511) break;
    }

    if (episodes.isEmpty) {
      return _resultForStatus(
        package,
        metadataRuntimeResult.programId,
        SourceSubjectDetailsStatus.notFound,
        [
          ..._runtimeDiagnostics(metadataRuntimeResult),
          ..._runtimeDiagnostics(episodeRuntimeResult),
          ...diagnostics,
        ],
      );
    }

    final grouped = <String, List<SourceEpisode>>{};
    for (final episode in episodes) {
      grouped.putIfAbsent(episode.identity.lineId, () => []).add(episode);
    }
    final lines = grouped.entries
        .map(
          (entry) => SourceSubjectLine(
            identity: SourceSubjectIdentity(
              sourceId: package.packageId,
              subjectId: subject.subjectId,
            ),
            lineId: entry.key,
            title: 'Line ${entry.key}',
            episodes: entry.value,
          ),
        )
        .toList(growable: false);

    try {
      return SourceSubjectDetailsResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: metadataRuntimeResult.programId,
        status: SourceSubjectDetailsStatus.available,
        details: SourceSubjectDetails(
          identity: subject,
          title: title,
          lines: lines,
        ),
        diagnostics: [
          ..._runtimeDiagnostics(metadataRuntimeResult),
          ..._runtimeDiagnostics(episodeRuntimeResult),
          ...diagnostics,
        ],
      );
    } on Object {
      return _failed(
        package,
        metadataRuntimeResult.programId,
        'subject_result_invalid',
      );
    }
  }

  SourceSubjectDetailsResult _resultForStatus(
    SourcePackageManifest package,
    String programId,
    SourceSubjectDetailsStatus status,
    Iterable<SourceSubjectDetailsDiagnostic> diagnostics,
  ) {
    return SourceSubjectDetailsResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: programId,
      status: status,
      diagnostics: diagnostics,
    );
  }

  SourceSubjectDetailsResult _failed(
    SourcePackageManifest package,
    String programId,
    String code,
  ) {
    return _resultForStatus(
      package,
      programId,
      SourceSubjectDetailsStatus.failed,
      [SourceSubjectDetailsDiagnostic(code: code, message: _message(code))],
    );
  }

  static SourceSubjectDetailsStatus? _blockedStatus(
    SourceRuntimeStatus status,
  ) {
    return switch (status) {
      SourceRuntimeStatus.disabled => SourceSubjectDetailsStatus.disabled,
      SourceRuntimeStatus.consentRequired =>
        SourceSubjectDetailsStatus.consentRequired,
      SourceRuntimeStatus.incompatible =>
        SourceSubjectDetailsStatus.incompatible,
      _ => null,
    };
  }

  static bool _isChallenge(SourceRuntimeResult result) => result.diagnostics
      .any((diagnostic) => diagnostic.code == 'challenge_required');

  static List<SourceSubjectDetailsDiagnostic> _runtimeDiagnostics(
    SourceRuntimeResult result,
  ) {
    return result.diagnostics
        .map(
          (diagnostic) => SourceSubjectDetailsDiagnostic(
            code: _safeCode(diagnostic.code),
            message: _message(diagnostic.code),
          ),
        )
        .toList(growable: false);
  }

  static SourceRuleProgram? _program(
    SourcePackageManifest package,
    String programId,
  ) {
    try {
      return package.programById(programId);
    } on StateError {
      return null;
    }
  }

  static bool _hasField(SourceRuleProgram program, String field) =>
      program.fields.any((candidate) => candidate.name == field);

  static bool _hasFields(SourceRuleProgram program, Iterable<String> fields) =>
      fields.every((field) => _hasField(program, field));

  static String? _text(String? value, int maxLength) {
    if (value == null) return null;
    final normalized = value.trim();
    if (normalized.isEmpty ||
        normalized.length > maxLength ||
        normalized != value) {
      return null;
    }
    if (normalized.codeUnits.any(
      (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
    )) {
      return null;
    }
    return normalized;
  }

  static void _addDiagnostic(
    List<SourceSubjectDetailsDiagnostic> diagnostics,
    SourceSubjectDetailsDiagnostic diagnostic,
  ) {
    if (diagnostics.length < 64) diagnostics.add(diagnostic);
  }

  static String _safeCode(String code) {
    if (RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(code)) return code;
    return 'subject_runtime_failed';
  }

  static String _message(String code) {
    return switch (code) {
      'challenge_required' => 'The source requires an interactive challenge.',
      'consent_required' => 'The source package requires explicit consent.',
      'package_disabled' => 'The source package is disabled.',
      'incompatible_wynime_version' => 'The source package is incompatible.',
      _ => 'The source subject details could not be normalized.',
    };
  }
}

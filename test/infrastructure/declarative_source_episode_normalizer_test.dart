import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_episode_normalization_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_episode_normalizer.dart';

void main() {
  const normalizer = DeclarativeSourceEpisodeNormalizer();
  final mapping = SourceEpisodeFieldMapping(
    lineIdField: 'line',
    subjectIdField: 'subject',
    episodeIdField: 'episode',
    titleField: 'title',
  );

  test('normalizes ordered immutable episodes with package-owned identity', () {
    final result = normalizer.normalizeEpisodes(
      runtimeResult: _runtime(
        records: [
          SourceRuntimeRecord({
            'line': ' line-1 ',
            'subject': ' subject-1 ',
            'episode': ' ep-1 ',
            'title': '  Episode   One ',
            'sourceId': 'attacker.example',
          }),
          SourceRuntimeRecord({
            'line': 'line-1',
            'subject': 'subject-1',
            'episode': 'ep-2',
            'title': 'Episode Two',
          }),
        ],
      ),
      mapping: mapping,
    );

    expect(result.status, SourceEpisodeNormalizationStatus.available);
    expect(result.results, hasLength(2));
    expect(result.results.first.identity.sourceId, 'example.anime');
    expect(result.results.first.identity.lineId, 'line-1');
    expect(result.results.first.identity.subjectId, 'subject-1');
    expect(result.results.first.identity.episodeId, 'ep-1');
    expect(result.results.first.title, 'Episode One');
    expect(result.results.last.identity.episodeId, 'ep-2');
    expect(
      () => result.results.add(result.results.first),
      throwsUnsupportedError,
    );
  });

  test('propagates non-available runtime states without exposing records', () {
    final cases = <SourceRuntimeStatus, SourceEpisodeNormalizationStatus>{
      SourceRuntimeStatus.notFound: SourceEpisodeNormalizationStatus.notFound,
      SourceRuntimeStatus.disabled: SourceEpisodeNormalizationStatus.disabled,
      SourceRuntimeStatus.consentRequired:
          SourceEpisodeNormalizationStatus.consentRequired,
      SourceRuntimeStatus.incompatible:
          SourceEpisodeNormalizationStatus.incompatible,
      SourceRuntimeStatus.failed: SourceEpisodeNormalizationStatus.failed,
    };

    for (final entry in cases.entries) {
      final result = normalizer.normalizeEpisodes(
        runtimeResult: _runtime(
          status: entry.key,
          records: [
            SourceRuntimeRecord({
              'line': 'hidden',
              'subject': 'hidden',
              'episode': 'hidden',
              'title': 'Must not escape',
            }),
          ],
          diagnostics: [
            SourceRuntimeDiagnostic(
              code: 'runtime_failed',
              message: 'secret fixture value must not be returned',
            ),
          ],
        ),
        mapping: mapping,
      );

      expect(result.status, entry.value);
      expect(result.results, isEmpty);
      expect(result.diagnostics.single.message, isNot(contains('secret')));
    }
  });

  test('keeps valid rows, drops invalid rows and de-duplicates identities', () {
    final result = normalizer.normalizeEpisodes(
      runtimeResult: _runtime(
        records: [
          SourceRuntimeRecord({
            'line': 'line-1',
            'subject': 'subject-1',
            'episode': 'ep-1',
            'title': 'First',
          }),
          SourceRuntimeRecord({
            'line': 'line-1',
            'subject': 'subject-1',
            'episode': 'ep-2',
          }),
          SourceRuntimeRecord({
            'line': 'line-1',
            'subject': 'subject-1',
            'episode': 'ep-1',
            'title': 'Duplicate',
          }),
          SourceRuntimeRecord({
            'line': 'line-2',
            'subject': 'subject-1',
            'episode': 'ep-1',
            'title': 'Different Line',
          }),
        ],
      ),
      mapping: mapping,
    );

    expect(result.status, SourceEpisodeNormalizationStatus.available);
    expect(result.results.map((item) => item.title), [
      'First',
      'Different Line',
    ]);
    expect(result.diagnostics.map((item) => item.code), [
      'normalized_field_invalid',
      'duplicate_episode_identity',
    ]);
    expect(result.diagnostics.first.recordIndex, 1);
    expect(result.diagnostics.last.recordIndex, 2);
  });

  test(
    'returns not found for empty input and keeps invalid diagnostics bounded',
    () {
      final empty = normalizer.normalizeEpisodes(
        runtimeResult: _runtime(),
        mapping: mapping,
      );
      expect(empty.status, SourceEpisodeNormalizationStatus.notFound);
      expect(empty.results, isEmpty);
      expect(empty.diagnostics, isEmpty);

      final invalid = normalizer.normalizeEpisodes(
        runtimeResult: _runtime(
          records: List<SourceRuntimeRecord>.generate(
            500,
            (_) => SourceRuntimeRecord(const <String, String>{}),
          ),
        ),
        mapping: mapping,
      );
      expect(invalid.status, SourceEpisodeNormalizationStatus.failed);
      expect(invalid.results, isEmpty);
      expect(invalid.diagnostics, hasLength(1000));
    },
  );

  test('rejects C1 controls and overlong episode fields', () {
    final result = normalizer.normalizeEpisodes(
      runtimeResult: _runtime(
        records: [
          SourceRuntimeRecord({
            'line': 'line-1',
            'subject': 'subject\u0080id',
            'episode': 'ep-1',
            'title': 'Title',
          }),
          SourceRuntimeRecord({
            'line': List<String>.filled(129, 'x').join(),
            'subject': 'subject-2',
            'episode': 'ep-2',
            'title': 'Title',
          }),
        ],
      ),
      mapping: mapping,
    );

    expect(result.status, SourceEpisodeNormalizationStatus.failed);
    expect(result.results, isEmpty);
    expect(result.diagnostics, hasLength(3));
    expect(result.diagnostics.map((item) => item.fieldName), [
      'subject',
      'line',
      isNull,
    ]);
  });

  test('rejects forged package identities and sanitizes diagnostics', () {
    final result = normalizer.normalizeEpisodes(
      runtimeResult: _runtime(
        packageId: 'not a package\nwith-secret',
        programId: 'INVALID PROGRAM',
        diagnostics: [
          SourceRuntimeDiagnostic(
            code: 'unknown secret https://private.example/token',
            message: 'https://private.example/token',
            fieldName: 'not a safe field/name',
          ),
        ],
      ),
      mapping: mapping,
    );

    expect(result.status, SourceEpisodeNormalizationStatus.failed);
    expect(result.packageId, 'unknown');
    expect(result.programId, 'unknown');
    expect(result.results, isEmpty);
    expect(result.diagnostics.single.code, 'invalid_source_identity');
    expect(result.diagnostics.single.message, isNot(contains('private')));
  });

  test('rejects an invalid program identity before normalizing records', () {
    final result = normalizer.normalizeEpisodes(
      runtimeResult: _runtime(
        programId: 'INVALID PROGRAM',
        records: [
          SourceRuntimeRecord({
            'line': 'line-1',
            'subject': 'subject-1',
            'episode': 'ep-1',
            'title': 'Title',
          }),
        ],
      ),
      mapping: mapping,
    );

    expect(result.status, SourceEpisodeNormalizationStatus.failed);
    expect(result.programId, 'unknown');
    expect(result.results, isEmpty);
    expect(result.diagnostics.single.code, 'invalid_program_identity');
  });

  test('requires four distinct bounded declarative field names', () {
    expect(
      () => SourceEpisodeFieldMapping(
        lineIdField: 'same',
        subjectIdField: 'subject',
        episodeIdField: 'episode',
        titleField: 'same',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceEpisodeFieldMapping(
        lineIdField: 'line',
        subjectIdField: '1bad',
        episodeIdField: 'episode',
        titleField: 'title',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceEpisodeFieldMapping(
        lineIdField: 'line',
        subjectIdField: 'subject',
        episodeIdField: 'episode',
        titleField: List<String>.filled(65, 'a').join(),
      ),
      throwsArgumentError,
    );
  });
}

SourceRuntimeResult _runtime({
  String packageId = 'example.anime',
  String programId = 'episodes',
  SourceRuntimeStatus status = SourceRuntimeStatus.available,
  Iterable<SourceRuntimeRecord> records = const [],
  Iterable<SourceRuntimeDiagnostic> diagnostics = const [],
}) {
  return SourceRuntimeResult(
    packageId: packageId,
    packageVersion: Version.parse('1.0.0'),
    programId: programId,
    status: status,
    records: records,
    diagnostics: diagnostics,
    consumedSteps: 1,
    selectorMatches: 1,
  );
}

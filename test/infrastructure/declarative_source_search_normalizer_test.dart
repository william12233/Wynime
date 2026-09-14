import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';

void main() {
  const normalizer = DeclarativeSourceSearchNormalizer();
  final mapping = SourceSearchFieldMapping(
    subjectIdField: 'id',
    titleField: 'name',
  );

  test('normalizes immutable records with package-owned source identity', () {
    final result = normalizer.normalizeSearch(
      runtimeResult: _runtime(
        records: [
          SourceRuntimeRecord({'id': ' subject-1 ', 'name': '  Alpha   One '}),
          SourceRuntimeRecord({
            'sourceId': 'attacker.example',
            'id': 'subject-2',
            'name': 'Beta',
          }),
        ],
      ),
      mapping: mapping,
    );

    expect(result.status, SourceSearchNormalizationStatus.available);
    expect(result.packageId, 'example.anime');
    expect(result.results, hasLength(2));
    expect(result.results.first.sourceId, 'example.anime');
    expect(result.results.first.subjectId, 'subject-1');
    expect(result.results.first.title, 'Alpha One');
    expect(result.results.last.subjectId, 'subject-2');
    expect(
      () => result.results.add(
        SourceSearchResult(
          sourceId: 'example.anime',
          subjectId: 'subject-3',
          title: 'Gamma',
        ),
      ),
      throwsUnsupportedError,
    );
    expect(
      () => result.diagnostics.add(
        SourceSearchNormalizationDiagnostic(code: 'test', message: 'test'),
      ),
      throwsUnsupportedError,
    );
  });

  test('propagates non-available runtime states without exposing records', () {
    final cases = <SourceRuntimeStatus, SourceSearchNormalizationStatus>{
      SourceRuntimeStatus.notFound: SourceSearchNormalizationStatus.notFound,
      SourceRuntimeStatus.disabled: SourceSearchNormalizationStatus.disabled,
      SourceRuntimeStatus.consentRequired:
          SourceSearchNormalizationStatus.consentRequired,
      SourceRuntimeStatus.incompatible:
          SourceSearchNormalizationStatus.incompatible,
      SourceRuntimeStatus.failed: SourceSearchNormalizationStatus.failed,
    };

    for (final entry in cases.entries) {
      final result = normalizer.normalizeSearch(
        runtimeResult: _runtime(
          status: entry.key,
          records: [
            SourceRuntimeRecord({'id': 'hidden', 'name': 'Must not escape'}),
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

  test(
    'keeps valid rows, drops invalid rows and de-duplicates subject IDs',
    () {
      final result = normalizer.normalizeSearch(
        runtimeResult: _runtime(
          records: [
            SourceRuntimeRecord({'id': 'one', 'name': 'First'}),
            SourceRuntimeRecord({'id': 'two'}),
            SourceRuntimeRecord({'id': 'one', 'name': 'Duplicate'}),
            SourceRuntimeRecord({'id': 'three', 'name': 'Third'}),
          ],
        ),
        mapping: mapping,
      );

      expect(result.status, SourceSearchNormalizationStatus.available);
      expect(result.results.map((item) => item.subjectId), ['one', 'three']);
      expect(result.diagnostics.map((item) => item.code), [
        'normalized_field_invalid',
        'duplicate_subject_id',
      ]);
      expect(result.diagnostics.first.recordIndex, 1);
      expect(result.diagnostics.last.recordIndex, 2);
    },
  );

  test(
    'returns not found for an empty available result and failed for all bad rows',
    () {
      final empty = normalizer.normalizeSearch(
        runtimeResult: _runtime(),
        mapping: mapping,
      );
      expect(empty.status, SourceSearchNormalizationStatus.notFound);
      expect(empty.results, isEmpty);
      expect(empty.diagnostics, isEmpty);

      final tooLongTitle = List<String>.filled(257, 'x').join();
      final invalid = normalizer.normalizeSearch(
        runtimeResult: _runtime(
          records: [
            SourceRuntimeRecord({'id': 'valid-id', 'name': tooLongTitle}),
            SourceRuntimeRecord({'id': 'bad\n-id', 'name': 'Title'}),
          ],
        ),
        mapping: mapping,
      );
      expect(invalid.status, SourceSearchNormalizationStatus.failed);
      expect(invalid.results, isEmpty);
      expect(
        invalid.diagnostics.map((item) => item.code),
        containsAll(<String>[
          'normalized_field_invalid',
          'normalization_failed',
        ]),
      );
      expect(
        invalid.diagnostics,
        everyElement(
          predicate<SourceSearchNormalizationDiagnostic>(
            (item) => !item.message.contains(tooLongTitle),
          ),
        ),
      );
    },
  );

  test('keeps diagnostics bounded for the maximum number of invalid rows', () {
    final result = normalizer.normalizeSearch(
      runtimeResult: _runtime(
        records: List<SourceRuntimeRecord>.generate(
          500,
          (_) => SourceRuntimeRecord(const {}),
        ),
      ),
      mapping: mapping,
    );

    expect(result.status, SourceSearchNormalizationStatus.failed);
    expect(result.results, isEmpty);
    expect(result.diagnostics, hasLength(1000));
  });

  test('rejects C1 control characters in normalized fields', () {
    final result = normalizer.normalizeSearch(
      runtimeResult: _runtime(
        records: [
          SourceRuntimeRecord({'id': 'subject\u0080id', 'name': 'Title'}),
        ],
      ),
      mapping: mapping,
    );

    expect(result.status, SourceSearchNormalizationStatus.failed);
    expect(result.results, isEmpty);
    expect(result.diagnostics.first.code, 'normalized_field_invalid');
    expect(result.diagnostics.first.fieldName, 'id');
  });

  test(
    'rejects forged package identities and sanitizes runtime diagnostics',
    () {
      final result = normalizer.normalizeSearch(
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

      expect(result.status, SourceSearchNormalizationStatus.failed);
      expect(result.packageId, 'unknown');
      expect(result.programId, 'unknown');
      expect(result.results, isEmpty);
      expect(result.diagnostics.single.code, 'invalid_source_identity');
      expect(result.diagnostics.single.message, isNot(contains('private')));
    },
  );

  test('requires distinct bounded declarative field names', () {
    expect(
      () =>
          SourceSearchFieldMapping(subjectIdField: 'same', titleField: 'same'),
      throwsArgumentError,
    );
    expect(
      () =>
          SourceSearchFieldMapping(subjectIdField: '1bad', titleField: 'title'),
      throwsArgumentError,
    );
    expect(
      () => SourceSearchFieldMapping(
        subjectIdField: 'id',
        titleField: List<String>.filled(65, 'a').join(),
      ),
      throwsArgumentError,
    );
  });
}

SourceRuntimeResult _runtime({
  String packageId = 'example.anime',
  String programId = 'search',
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

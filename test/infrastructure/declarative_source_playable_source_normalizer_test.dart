import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  const normalizer = DeclarativeSourcePlayableSourceNormalizer();
  final mapping = SourcePlayableSourceFieldMapping(
    sourceKeyField: 'key',
    labelField: 'label',
    kindField: 'kind',
    mediaUriField: 'media',
    pageUriField: 'page',
  );
  final package = _package();
  final episode = SourceEpisodeIdentity(
    sourceId: package.packageId,
    lineId: 'line-1',
    subjectId: 'subject-1',
    episodeId: 'episode-1',
  );

  test('normalizes ordered playable sources with package-owned episode', () {
    final result = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        records: [
          SourceRuntimeRecord({
            'key': '  primary ',
            'label': '  1080p  ',
            'kind': 'hls',
            'media': 'https://cdn.example.com/video/master.m3u8?token=secret',
            'page': 'https://example.com/watch/episode-1',
            'sourceId': 'attacker.example',
          }),
          SourceRuntimeRecord({
            'key': 'secondary',
            'label': 'Direct MP4',
            'kind': 'video',
            'media': 'https://example.com/video/episode-1.mp4',
            'page': 'https://example.com/watch/episode-1',
          }),
        ],
      ),
      episode: episode,
      mapping: mapping,
    );

    expect(result.status, SourcePlayableSourceNormalizationStatus.available);
    expect(result.results, hasLength(2));
    expect(result.results.first.sourceKey, 'primary');
    expect(result.results.first.label, '1080p');
    expect(result.results.first.kind, WebCandidateKind.hls);
    expect(result.results.first.episode, episode);
    expect(result.results.first.mediaUri.query, 'token=secret');
    expect(result.results.first.toString(), isNot(contains('token=secret')));
    expect(result.results.first.toString(), isNot(contains('master.m3u8')));
    expect(
      () => result.results.add(result.results.first),
      throwsUnsupportedError,
    );
  });

  test('validates both URIs against the package allowlist', () {
    final result = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        records: [
          SourceRuntimeRecord({
            'key': 'outside-media',
            'label': 'Outside media',
            'kind': 'hls',
            'media': 'https://evil.example/master.m3u8',
            'page': 'https://example.com/watch/episode-1',
          }),
          SourceRuntimeRecord({
            'key': 'outside-page',
            'label': 'Outside page',
            'kind': 'video',
            'media': 'https://example.com/video/episode-1.mp4',
            'page': 'https://evil.example/watch/episode-1',
          }),
          SourceRuntimeRecord({
            'key': 'malformed',
            'label': 'Malformed',
            'kind': 'audio',
            'media': 'file:///private/token',
            'page': 'not a uri',
          }),
        ],
      ),
      episode: episode,
      mapping: mapping,
    );

    expect(result.status, SourcePlayableSourceNormalizationStatus.failed);
    expect(result.results, isEmpty);
    expect(result.diagnostics.map((item) => item.code), [
      'playable_uri_not_allowed',
      'playable_uri_not_allowed',
      'playable_uri_invalid',
      'playable_uri_invalid',
      'normalization_failed',
    ]);
  });

  test('drops unsupported kinds and keeps the first duplicate key', () {
    final result = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        records: [
          _record(key: 'same', label: 'First', kind: 'video'),
          _record(key: 'same', label: 'Duplicate', kind: 'audio'),
          _record(key: 'dash', label: 'Deferred', kind: 'dash'),
          _record(key: 'segment', label: 'Segment', kind: 'mediaSegment'),
        ],
      ),
      episode: episode,
      mapping: mapping,
    );

    expect(result.status, SourcePlayableSourceNormalizationStatus.available);
    expect(result.results.map((item) => item.label), ['First']);
    expect(result.diagnostics.map((item) => item.code), [
      'duplicate_playable_source_key',
      'playable_kind_unsupported',
      'playable_kind_unsupported',
    ]);
  });

  test('propagates non-available runtime states without exposing records', () {
    final cases =
        <SourceRuntimeStatus, SourcePlayableSourceNormalizationStatus>{
          SourceRuntimeStatus.notFound:
              SourcePlayableSourceNormalizationStatus.notFound,
          SourceRuntimeStatus.disabled:
              SourcePlayableSourceNormalizationStatus.disabled,
          SourceRuntimeStatus.consentRequired:
              SourcePlayableSourceNormalizationStatus.consentRequired,
          SourceRuntimeStatus.incompatible:
              SourcePlayableSourceNormalizationStatus.incompatible,
          SourceRuntimeStatus.failed:
              SourcePlayableSourceNormalizationStatus.failed,
        };

    for (final entry in cases.entries) {
      final result = normalizer.normalizePlayableSources(
        package: package,
        runtimeResult: _runtime(
          status: entry.key,
          records: [_record(key: 'hidden', label: 'Hidden', kind: 'video')],
          diagnostics: [
            SourceRuntimeDiagnostic(
              code: 'runtime_failed',
              message: 'secret fixture value must not be returned',
            ),
          ],
        ),
        episode: episode,
        mapping: mapping,
      );

      expect(result.status, entry.value);
      expect(result.results, isEmpty);
      expect(result.diagnostics.single.message, isNot(contains('secret')));
    }
  });

  test('rejects mismatched identities and invalid program IDs fail closed', () {
    final mismatchedEpisode = SourceEpisodeIdentity(
      sourceId: 'other.anime',
      lineId: 'line-1',
      subjectId: 'subject-1',
      episodeId: 'episode-1',
    );
    final mismatch = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(),
      episode: mismatchedEpisode,
      mapping: mapping,
    );
    expect(mismatch.status, SourcePlayableSourceNormalizationStatus.failed);
    expect(mismatch.diagnostics.single.code, 'episode_source_mismatch');

    final invalidProgram = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        programId: 'INVALID PROGRAM',
        records: [_record(key: 'source', label: 'Source', kind: 'video')],
      ),
      episode: episode,
      mapping: mapping,
    );
    expect(
      invalidProgram.status,
      SourcePlayableSourceNormalizationStatus.failed,
    );
    expect(invalidProgram.programId, 'unknown');
    expect(invalidProgram.results, isEmpty);
    expect(invalidProgram.diagnostics.single.code, 'invalid_program_identity');
  });

  test('requires a declared playback program and declared mapping fields', () {
    final unknownProgram = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        programId: 'missing',
        records: [_record(key: 'source', label: 'Source', kind: 'video')],
      ),
      episode: episode,
      mapping: mapping,
    );
    expect(
      unknownProgram.status,
      SourcePlayableSourceNormalizationStatus.failed,
    );
    expect(unknownProgram.diagnostics.single.code, 'program_not_found');

    final undeclaredMapping = SourcePlayableSourceFieldMapping(
      sourceKeyField: 'key',
      labelField: 'notDeclared',
      kindField: 'kind',
      mediaUriField: 'media',
      pageUriField: 'page',
    );
    final undeclared = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        records: [_record(key: 'source', label: 'Source', kind: 'video')],
      ),
      episode: episode,
      mapping: undeclaredMapping,
    );
    expect(undeclared.status, SourcePlayableSourceNormalizationStatus.failed);
    expect(undeclared.diagnostics.single.code, 'mapping_field_not_declared');
    expect(undeclared.diagnostics.single.fieldName, 'notDeclared');
  });

  test('redacted diagnostics do not expose episode identity values', () {
    final unsafeEpisode = SourceEpisodeIdentity(
      sourceId: package.packageId,
      lineId: 'https://cdn.example/video.m3u8?token=secret',
      subjectId: 'subject-1',
      episodeId: 'episode-1',
    );
    final result = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        records: [_record(key: 'source', label: 'Source', kind: 'video')],
      ),
      episode: unsafeEpisode,
      mapping: mapping,
    );

    expect(result.results.single.toString(), isNot(contains('cdn.example')));
    expect(result.results.single.toString(), isNot(contains('token=secret')));
    expect(result.results.single.toString(), contains('lineIdLength'));
  });

  test('rejects runtime results from a different package or version', () {
    final packageMismatch = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(packageId: 'other.anime'),
      episode: episode,
      mapping: mapping,
    );
    expect(
      packageMismatch.status,
      SourcePlayableSourceNormalizationStatus.failed,
    );
    expect(packageMismatch.diagnostics.single.code, 'runtime_package_mismatch');

    final versionMismatch = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(packageVersion: Version.parse('1.0.1')),
      episode: episode,
      mapping: mapping,
    );
    expect(
      versionMismatch.status,
      SourcePlayableSourceNormalizationStatus.failed,
    );
    expect(versionMismatch.diagnostics.single.code, 'runtime_package_mismatch');
  });

  test('bounds invalid diagnostics and rejects C1 or overlong values', () {
    final invalid = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        records: List<SourceRuntimeRecord>.generate(
          500,
          (_) => SourceRuntimeRecord(const <String, String>{}),
        ),
      ),
      episode: episode,
      mapping: mapping,
    );
    expect(invalid.status, SourcePlayableSourceNormalizationStatus.failed);
    expect(invalid.diagnostics, hasLength(1000));

    final controls = normalizer.normalizePlayableSources(
      package: package,
      runtimeResult: _runtime(
        records: [
          _record(key: 'key', label: 'label\u0080', kind: 'video'),
          _record(
            key: 'key2',
            label: 'label',
            kind: 'video',
            media:
                'https://example.com/${List<String>.filled(4097, 'x').join()}',
          ),
        ],
      ),
      episode: episode,
      mapping: mapping,
    );
    expect(controls.status, SourcePlayableSourceNormalizationStatus.failed);
    expect(controls.diagnostics.map((item) => item.fieldName), [
      'label',
      'media',
      isNull,
    ]);
  });

  test('requires five distinct bounded field names', () {
    expect(
      () => SourcePlayableSourceFieldMapping(
        sourceKeyField: 'same',
        labelField: 'label',
        kindField: 'kind',
        mediaUriField: 'media',
        pageUriField: 'same',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlayableSourceFieldMapping(
        sourceKeyField: '1bad',
        labelField: 'label',
        kindField: 'kind',
        mediaUriField: 'media',
        pageUriField: 'page',
      ),
      throwsArgumentError,
    );
  });
}

SourceRuntimeRecord _record({
  required String key,
  required String label,
  required String kind,
  String media = 'https://example.com/video/episode-1.mp4',
  String page = 'https://example.com/watch/episode-1',
}) {
  return SourceRuntimeRecord({
    'key': key,
    'label': label,
    'kind': kind,
    'media': media,
    'page': page,
  });
}

SourceRuntimeResult _runtime({
  String packageId = 'example.anime',
  Version? packageVersion,
  String programId = 'playback',
  SourceRuntimeStatus status = SourceRuntimeStatus.available,
  Iterable<SourceRuntimeRecord> records = const [],
  Iterable<SourceRuntimeDiagnostic> diagnostics = const [],
}) {
  return SourceRuntimeResult(
    packageId: packageId,
    packageVersion: packageVersion ?? Version.parse('1.0.0'),
    programId: programId,
    status: status,
    records: records,
    diagnostics: diagnostics,
    consumedSteps: 1,
    selectorMatches: 1,
  );
}

SourcePackageManifest _package() {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'example.anime',
    displayName: 'Example Anime',
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
    securityPolicy: testSourcePolicy(
      domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
    ),
    programs: [
      SourceRuleProgram(
        programId: 'playback',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: '.source',
        ),
        fields: [
          SourceFieldRule(name: 'key', valueKind: SourceValueKind.text),
          SourceFieldRule(name: 'label', valueKind: SourceValueKind.text),
          SourceFieldRule(name: 'kind', valueKind: SourceValueKind.text),
          SourceFieldRule(name: 'media', valueKind: SourceValueKind.text),
          SourceFieldRule(name: 'page', valueKind: SourceValueKind.text),
        ],
        resultLimit: 10,
      ),
    ],
  );
}

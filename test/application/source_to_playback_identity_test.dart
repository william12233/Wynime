import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_episode_correlator.dart';
import 'package:wynime/src/application/source_episode_ordinal.dart';
import 'package:wynime/src/application/source_subject_matcher.dart';
import 'package:wynime/src/domain/models/bangumi_episode_type.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_provenance.dart';
import 'package:wynime/src/domain/models/source_playback_mapping.dart';
import 'package:wynime/src/infrastructure/repositories/drift_source_package_repository.dart';
import 'package:wynime/src/infrastructure/repositories/drift_source_playback_mapping_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_revision_calculator.dart';
import 'package:wynime/src/infrastructure/source_rules/source_title_normalizer.dart';

import '../helpers/test_database.dart';

void main() {
  test('episode type and ordinal contracts are conservative', () {
    expect(BangumiEpisodeTypeCode.isMainStory(0), isTrue);
    for (final type in [1, 2, 3, 4, 5, 6, -1, 99]) {
      expect(BangumiEpisodeTypeCode.isMainStory(type), isFalse);
    }
    expect(SourceEpisodeOrdinal.parse('EP 01')?.canonical, '1');
    expect(SourceEpisodeOrdinal.parse('第１２.５０話')?.canonical, '12.5');
    expect(SourceEpisodeOrdinal.parse('SP 1'), isNull);
    expect(SourceEpisodeOrdinal.parse('OVA'), isNull);
  });

  test('title normalization does not perform fuzzy or translated matching', () {
    const normalizer = SourceTitleNormalizer();
    expect(normalizer.normalize('  ＡＢＣ：第１話  '), 'abc 第1話');
    expect(normalizer.normalize('ABC 1'), 'abc 1');
    expect(normalizer.normalize('abc season 2'), isNot('abc season 1'));
  });

  test('subject matching accepts one exact normalized identity only', () {
    final result = const SourceSubjectMatcher().match(
      subject: _subject(name: 'Example', nameCn: '範例'),
      results: [
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-100',
          title: '  範例  ',
        ),
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-100',
          title: 'Example',
        ),
      ],
    );
    expect(result.status, SourceSubjectMatchStatus.matched);
    expect(result.identity?.identityKey, 'xifan/source-100');
  });

  test('ambiguous subject matches remain explicit', () {
    final result = const SourceSubjectMatcher().match(
      subject: _subject(name: 'Example', nameCn: '範例'),
      results: [
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-100',
          title: '範例',
        ),
        SourceSearchResult(
          sourceId: 'xifan',
          subjectId: 'source-101',
          title: 'Example',
        ),
      ],
    );
    expect(result.status, SourceSubjectMatchStatus.selectionRequired);
    expect(result.identity, isNull);
    expect(result.candidates, hasLength(2));
  });

  test(
    'subject matching reports no result instead of selecting the first row',
    () {
      final result = const SourceSubjectMatcher().match(
        subject: _subject(name: 'Example', nameCn: '範例'),
        results: [
          SourceSearchResult(
            sourceId: 'xifan',
            subjectId: 'source-100',
            title: 'Another title',
          ),
        ],
      );
      expect(result.status, SourceSubjectMatchStatus.notFound);
      expect(result.candidates, isEmpty);
    },
  );

  test('main-story correlation accepts one exact ordinal only', () {
    final sourceSubject = SourceSubjectIdentity(
      sourceId: 'xifan',
      subjectId: 'source-100',
    );
    final details = SourceSubjectDetails(
      identity: sourceSubject,
      title: 'Example',
      lines: [
        SourceSubjectLine(
          identity: sourceSubject,
          lineId: 'line-a',
          title: 'A',
          episodes: [
            SourceEpisode(
              identity: SourceEpisodeIdentity(
                sourceId: 'xifan',
                lineId: 'line-a',
                subjectId: 'source-100',
                episodeId: 'ep-1',
              ),
              title: 'Episode 1',
            ),
          ],
        ),
      ],
    );
    final result = const SourceEpisodeCorrelator().correlate(
      episode: const BangumiEpisode(
        id: 'bgm-1',
        subjectId: '100',
        name: 'Episode 1',
        nameCn: '第一集',
        sort: 1,
        type: 0,
      ),
      details: details,
    );
    expect(result.status, SourceEpisodeCorrelationStatus.automatic);
    expect(result.selected, details.lines.single.episodes.single.identity);
  });

  test('mapping provenance mismatch prunes stale identity rows', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final repository = DriftSourcePlaybackMappingRepository(database);
    final provenance = SourcePackageProvenance(
      packageId: 'xifan',
      version: Version.parse('1.0.0'),
      revisionSha256: 'a' * 64,
    );
    final sourceSubject = SourceSubjectIdentity(
      sourceId: 'xifan',
      subjectId: 'source-100',
    );
    await repository.upsertSubjectMapping(
      SourceSubjectMapping(
        bangumiSubjectId: '100',
        packageId: 'xifan',
        sourceSubject: sourceSubject,
        provenance: provenance,
        mappingKind: SubjectMappingKind.automaticExactTitle,
        confirmedAt: DateTime.utc(2026, 9, 22),
      ),
    );
    await repository.upsertEpisodeMapping(
      SourceEpisodeMapping(
        bangumiSubjectId: '100',
        bangumiEpisodeId: 'bgm-1',
        packageId: 'xifan',
        sourceEpisode: SourceEpisodeIdentity(
          sourceId: 'xifan',
          lineId: 'line-a',
          subjectId: 'source-100',
          episodeId: 'ep-1',
        ),
        provenance: provenance,
        mappingKind: EpisodeMappingKind.automaticExactNumber,
        confirmedAt: DateTime.utc(2026, 9, 22),
      ),
    );

    final stale = await repository.findValidSubjectMapping(
      bangumiSubjectId: '100',
      packageId: 'xifan',
      expectedProvenance: SourcePackageProvenance(
        packageId: 'xifan',
        version: Version.parse('1.0.0'),
        revisionSha256: 'b' * 64,
      ),
    );
    expect(stale, isNull);
    expect(
      await (database.select(database.sourceSubjectMappings)).get(),
      isEmpty,
    );
    expect(
      await (database.select(database.sourceEpisodeMappings)).get(),
      isEmpty,
    );
  });

  test(
    'removing a package snapshot clears its mapping rows atomically',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final package = const SourcePackageDecoder().decode(
        File('sources/xifan.wynsrc.json').readAsStringSync(),
      );
      final installed = InstalledSourcePackage(
        package: package,
        status: SourcePackageStatus.enabled,
        requiresConsent: false,
        requiresReconsent: false,
      );
      await DriftSourcePackageRepository(database).replaceAll([installed]);
      final mappingRepository = DriftSourcePlaybackMappingRepository(database);
      final provenance = const SourcePackageRevisionCalculator().calculate(
        package,
      );
      await mappingRepository.upsertSubjectMapping(
        SourceSubjectMapping(
          bangumiSubjectId: '100',
          packageId: 'xifan',
          sourceSubject: SourceSubjectIdentity(
            sourceId: 'xifan',
            subjectId: 'source-100',
          ),
          provenance: provenance,
          mappingKind: SubjectMappingKind.userConfirmed,
          confirmedAt: DateTime.utc(2026, 9, 22),
        ),
      );
      await DriftSourcePackageRepository(database).replaceAll(const []);
      expect(await database.select(database.sourcePackages).get(), isEmpty);
      expect(
        await database.select(database.sourceSubjectMappings).get(),
        isEmpty,
      );
    },
  );
}

BangumiSubject _subject({required String name, required String nameCn}) =>
    BangumiSubject(
      id: 'bgm-100',
      name: name,
      nameCn: nameCn,
      summary: '',
      eps: 1,
    );

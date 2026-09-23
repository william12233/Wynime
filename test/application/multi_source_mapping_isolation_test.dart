import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_provenance.dart';
import 'package:wynime/src/domain/models/source_playback_mapping.dart';
import 'package:wynime/src/infrastructure/repositories/drift_source_playback_mapping_repository.dart';

import '../helpers/test_database.dart';

void main() {
  test(
    'two source mappings remain isolated through remap and invalidation',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftSourcePlaybackMappingRepository(database);
      final confirmedAt = DateTime.utc(2026, 9, 23);
      final provenanceA = SourcePackageProvenance(
        packageId: 'source-a',
        version: Version.parse('1.0.0'),
        revisionSha256: 'a' * 64,
      );
      final provenanceB = SourcePackageProvenance(
        packageId: 'source-b',
        version: Version.parse('1.0.0'),
        revisionSha256: 'b' * 64,
      );
      const bangumiSubjectId = 'bgm-subject-100';
      const bangumiEpisodeId = 'bgm-episode-24';
      final subjectA = SourceSubjectIdentity(
        sourceId: 'source-a',
        subjectId: 'subject-a-v1',
      );
      final subjectB = SourceSubjectIdentity(
        sourceId: 'source-b',
        subjectId: 'subject-b-v1',
      );
      final episodeA = SourceEpisodeIdentity(
        sourceId: 'source-a',
        lineId: 'line-a',
        subjectId: subjectA.subjectId,
        episodeId: 'episode-a-24',
      );
      final episodeB = SourceEpisodeIdentity(
        sourceId: 'source-b',
        lineId: 'line-b',
        subjectId: subjectB.subjectId,
        episodeId: 'episode-b-24',
      );

      await repository.upsertSubjectMapping(
        SourceSubjectMapping(
          bangumiSubjectId: bangumiSubjectId,
          packageId: 'source-a',
          sourceSubject: subjectA,
          provenance: provenanceA,
          mappingKind: SubjectMappingKind.userConfirmed,
          confirmedAt: confirmedAt,
        ),
      );
      await repository.upsertSubjectMapping(
        SourceSubjectMapping(
          bangumiSubjectId: bangumiSubjectId,
          packageId: 'source-b',
          sourceSubject: subjectB,
          provenance: provenanceB,
          mappingKind: SubjectMappingKind.userConfirmed,
          confirmedAt: confirmedAt,
        ),
      );
      await repository.upsertEpisodeMapping(
        SourceEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-a',
          sourceEpisode: episodeA,
          provenance: provenanceA,
          mappingKind: EpisodeMappingKind.userConfirmed,
          confirmedAt: confirmedAt,
        ),
      );
      await repository.upsertEpisodeMapping(
        SourceEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-b',
          sourceEpisode: episodeB,
          provenance: provenanceB,
          mappingKind: EpisodeMappingKind.userConfirmed,
          confirmedAt: confirmedAt,
        ),
      );

      expect(
        (await repository.findValidSubjectMapping(
          bangumiSubjectId: bangumiSubjectId,
          packageId: 'source-a',
          expectedProvenance: provenanceA,
        ))?.sourceSubject,
        subjectA,
      );
      expect(
        (await repository.findValidSubjectMapping(
          bangumiSubjectId: bangumiSubjectId,
          packageId: 'source-b',
          expectedProvenance: provenanceB,
        ))?.sourceSubject,
        subjectB,
      );
      expect(
        (await repository.findValidEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-b',
          expectedSourceSubject: subjectB,
          expectedProvenance: provenanceB,
        ))?.sourceEpisode,
        episodeB,
      );

      final remappedSubjectA = SourceSubjectIdentity(
        sourceId: 'source-a',
        subjectId: 'subject-a-v2',
      );
      await repository.upsertSubjectMapping(
        SourceSubjectMapping(
          bangumiSubjectId: bangumiSubjectId,
          packageId: 'source-a',
          sourceSubject: remappedSubjectA,
          provenance: provenanceA,
          mappingKind: SubjectMappingKind.userConfirmed,
          confirmedAt: confirmedAt,
        ),
      );

      expect(
        await repository.findValidEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-a',
          expectedSourceSubject: remappedSubjectA,
          expectedProvenance: provenanceA,
        ),
        isNull,
      );
      expect(
        (await repository.findValidEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-b',
          expectedSourceSubject: subjectB,
          expectedProvenance: provenanceB,
        ))?.sourceEpisode,
        episodeB,
      );

      await repository.upsertEpisodeMapping(
        SourceEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-a',
          sourceEpisode: SourceEpisodeIdentity(
            sourceId: 'source-a',
            lineId: 'line-a-v2',
            subjectId: remappedSubjectA.subjectId,
            episodeId: 'episode-a-v2-24',
          ),
          provenance: provenanceA,
          mappingKind: EpisodeMappingKind.userConfirmed,
          confirmedAt: confirmedAt,
        ),
      );
      final changedProvenanceA = SourcePackageProvenance(
        packageId: 'source-a',
        version: Version.parse('1.0.1'),
        revisionSha256: 'c' * 64,
      );
      expect(
        await repository.findValidEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-a',
          expectedSourceSubject: remappedSubjectA,
          expectedProvenance: changedProvenanceA,
        ),
        isNull,
      );
      expect(
        (await repository.findValidEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-b',
          expectedSourceSubject: subjectB,
          expectedProvenance: provenanceB,
        ))?.sourceEpisode,
        episodeB,
      );

      await repository.clearAllForPackage('source-a');
      expect(
        await repository.findValidSubjectMapping(
          bangumiSubjectId: bangumiSubjectId,
          packageId: 'source-a',
          expectedProvenance: provenanceA,
        ),
        isNull,
      );
      expect(
        (await repository.findValidSubjectMapping(
          bangumiSubjectId: bangumiSubjectId,
          packageId: 'source-b',
          expectedProvenance: provenanceB,
        ))?.sourceSubject,
        subjectB,
      );
      expect(
        (await repository.findValidEpisodeMapping(
          bangumiSubjectId: bangumiSubjectId,
          bangumiEpisodeId: bangumiEpisodeId,
          packageId: 'source-b',
          expectedSourceSubject: subjectB,
          expectedProvenance: provenanceB,
        ))?.sourceEpisode,
        episodeB,
      );
    },
  );
}

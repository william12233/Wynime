import '../models/source_identity.dart';
import '../models/source_package_provenance.dart';
import '../models/source_playback_mapping.dart';

abstract interface class SourcePlaybackMappingRepository {
  Future<SourceSubjectMapping?> findValidSubjectMapping({
    required String bangumiSubjectId,
    required String packageId,
    required SourcePackageProvenance expectedProvenance,
  });

  Future<void> upsertSubjectMapping(SourceSubjectMapping mapping);

  Future<void> clearSubjectMapping({
    required String bangumiSubjectId,
    required String packageId,
  });

  Future<SourceEpisodeMapping?> findValidEpisodeMapping({
    required String bangumiSubjectId,
    required String bangumiEpisodeId,
    required String packageId,
    required SourceSubjectIdentity expectedSourceSubject,
    required SourcePackageProvenance expectedProvenance,
  });

  Future<void> upsertEpisodeMapping(SourceEpisodeMapping mapping);

  Future<void> clearEpisodeMapping({
    required String bangumiEpisodeId,
    required String packageId,
  });

  Future<void> clearEpisodesUnderSubject({
    required String bangumiSubjectId,
    required String packageId,
  });

  Future<void> clearAllForPackage(String packageId);
}

export '../models/bangumi_models.dart' show BangumiCollectionStatus;

import '../models/bangumi_models.dart';

final class BangumiEpisodeProgress {
  const BangumiEpisodeProgress({
    required this.subjectId,
    required this.watchedEpisodeIds,
  });

  final String subjectId;
  final Set<String> watchedEpisodeIds;
}

abstract interface class BangumiRepository {
  Future<BangumiEpisodeProgress?> loadEpisodeProgress(String subjectId);

  Future<void> saveCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  );

  Future<void> markEpisodeWatched(String subjectId, String episodeId);
}

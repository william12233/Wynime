import '../models/source_episode_normalization_models.dart';
import '../models/source_runtime_models.dart';

abstract interface class SourceEpisodeNormalizer {
  SourceEpisodeNormalizationResult normalizeEpisodes({
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeFieldMapping mapping,
  });
}

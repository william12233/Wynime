import '../models/source_runtime_models.dart';
import '../models/source_search_normalization_models.dart';

abstract interface class SourceSearchNormalizer {
  SourceSearchNormalizationResult normalizeSearch({
    required SourceRuntimeResult runtimeResult,
    required SourceSearchFieldMapping mapping,
  });
}

import '../models/source_cache_models.dart';

abstract interface class SourceCacheStore {
  SourceCacheEntry<T>? read<T>(SourceCacheKey key, {DateTime? now});

  void write<T>(SourceCacheEntry<T> entry);

  void invalidate(SourceCacheKey key);

  void invalidateStage({
    required String sourceId,
    required SourceCacheStage stage,
  });

  void clearSource(String sourceId);

  void clearExpired({DateTime? now});
}

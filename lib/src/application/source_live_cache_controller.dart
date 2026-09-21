import '../domain/models/source_cache_models.dart';
import '../domain/services/source_cache_store.dart';

final class SourceLiveCacheController {
  const SourceLiveCacheController({required this.store});

  final SourceCacheStore store;

  Future<T> getOrLoad<T>({
    required SourceCacheKey key,
    required Duration ttl,
    required Future<T> Function() load,
    required bool Function(T value) isCacheable,
    DateTime? now,
  }) async {
    _validateTtl(ttl);
    final current = now ?? DateTime.now().toUtc();
    if (ttl > Duration.zero) {
      final cached = store.read<T>(key, now: current);
      if (cached != null) return cached.value;
    }
    final value = await load();
    if (ttl > Duration.zero && isCacheable(value)) {
      store.write(
        SourceCacheEntry<T>(
          key: key,
          value: value,
          createdAt: current,
          expiresAt: current.add(ttl),
        ),
      );
    }
    return value;
  }

  Future<T> refreshPlayableSource<T>({
    required SourceCacheKey key,
    required Duration ttl,
    required Future<T> Function() resolve,
    required bool Function(T value) isCacheable,
    DateTime? now,
  }) async {
    if (key.stage != SourceCacheStage.playbackResolution) {
      throw ArgumentError.value(
        key,
        'key',
        'Playback refresh requires a playbackResolution cache key.',
      );
    }
    _validateTtl(ttl);
    store.invalidate(key);
    return getOrLoad(
      key: key,
      ttl: ttl,
      load: resolve,
      isCacheable: isCacheable,
      now: now,
    );
  }

  static void _validateTtl(Duration ttl) {
    if (ttl < Duration.zero ||
        ttl > SourceCachePolicy.maxTtl ||
        ttl.inMicroseconds % 1000000 != 0) {
      throw ArgumentError.value(
        ttl,
        'ttl',
        'Must be a whole-second duration between zero and '
            '${SourceCachePolicy.maxTtl}.',
      );
    }
  }
}

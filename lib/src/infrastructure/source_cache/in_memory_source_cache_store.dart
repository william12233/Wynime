import '../../domain/models/source_cache_models.dart';
import '../../domain/services/source_cache_store.dart';

final class InMemorySourceCacheStore implements SourceCacheStore {
  InMemorySourceCacheStore({
    this.maxEntries = 256,
    this.maxEntriesPerSource = 64,
  }) {
    if (maxEntries <= 0 || maxEntriesPerSource <= 0) {
      throw ArgumentError('Cache limits must be positive.');
    }
  }

  final int maxEntries;
  final int maxEntriesPerSource;
  final _entries = <SourceCacheKey, SourceCacheEntry<Object?>>{};

  int get length => _entries.length;

  @override
  SourceCacheEntry<T>? read<T>(SourceCacheKey key, {DateTime? now}) {
    final entry = _entries[key];
    if (entry == null) return null;
    final current = now ?? DateTime.now().toUtc();
    if (entry.isExpired(current)) {
      _entries.remove(key);
      return null;
    }
    if (entry.value is! T && T != Object) return null;
    return entry as SourceCacheEntry<T>;
  }

  @override
  void write<T>(SourceCacheEntry<T> entry) {
    // Use the entry's logical creation time rather than the process clock so
    // callers can replay deterministic timelines without losing fresh data.
    _removeExpired(entry.createdAt);
    _entries.remove(entry.key);
    _entries[entry.key] = entry as SourceCacheEntry<Object?>;
    _evictPerSource(entry.key.sourceId);
    while (_entries.length > maxEntries) {
      _entries.remove(_oldestKey());
    }
  }

  @override
  void invalidate(SourceCacheKey key) => _entries.remove(key);

  @override
  void invalidateStage({
    required String sourceId,
    required SourceCacheStage stage,
  }) {
    _entries.removeWhere(
      (key, _) => key.sourceId == sourceId && key.stage == stage,
    );
  }

  @override
  void clearSource(String sourceId) {
    _entries.removeWhere((key, _) => key.sourceId == sourceId);
  }

  @override
  void clearExpired({DateTime? now}) =>
      _removeExpired(now ?? DateTime.now().toUtc());

  void _evictPerSource(String sourceId) {
    while (_entries.entries
            .where((entry) => entry.key.sourceId == sourceId)
            .length >
        maxEntriesPerSource) {
      final key = _entries.entries
          .where((entry) => entry.key.sourceId == sourceId)
          .reduce(
            (left, right) =>
                left.value.createdAt.isBefore(right.value.createdAt)
                ? left
                : right,
          )
          .key;
      _entries.remove(key);
    }
  }

  void _removeExpired(DateTime now) {
    _entries.removeWhere((_, entry) => entry.isExpired(now));
  }

  SourceCacheKey _oldestKey() {
    return _entries.entries
        .reduce(
          (left, right) => left.value.createdAt.isBefore(right.value.createdAt)
              ? left
              : right,
        )
        .key;
  }
}

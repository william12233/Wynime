import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_cache_controller.dart';
import 'package:wynime/src/domain/models/source_cache_models.dart';
import 'package:wynime/src/infrastructure/source_cache/in_memory_source_cache_store.dart';

void main() {
  final key = SourceCacheKey(
    sourceId: 'xifan',
    packageVersion: Version.parse('1.0.0'),
    stage: SourceCacheStage.subjectMetadata,
    identity: '3541',
  );

  test('cache expires entries, evicts oldest entries and isolates sources', () {
    final store = InMemorySourceCacheStore(
      maxEntries: 2,
      maxEntriesPerSource: 1,
    );
    final first = DateTime.utc(2026, 1, 1);
    store.write(
      SourceCacheEntry<String>(
        key: key,
        value: 'first',
        createdAt: first,
        expiresAt: first.add(const Duration(minutes: 5)),
      ),
    );
    expect(
      store
          .read<String>(key, now: first.add(const Duration(minutes: 1)))!
          .value,
      'first',
    );
    expect(
      store.read<String>(key, now: first.add(const Duration(minutes: 5))),
      isNull,
    );

    final otherKey = SourceCacheKey(
      sourceId: 'other',
      packageVersion: Version.parse('1.0.0'),
      stage: SourceCacheStage.subjectMetadata,
      identity: '3541',
    );
    store.write(
      SourceCacheEntry<String>(
        key: key,
        value: 'xifan',
        createdAt: first.add(const Duration(minutes: 6)),
        expiresAt: first.add(const Duration(minutes: 10)),
      ),
    );
    store.write(
      SourceCacheEntry<String>(
        key: otherKey,
        value: 'other',
        createdAt: first.add(const Duration(minutes: 7)),
        expiresAt: first.add(const Duration(minutes: 10)),
      ),
    );
    final afterWrites = first.add(const Duration(minutes: 8));
    expect(store.read<String>(key, now: afterWrites)!.value, 'xifan');
    expect(store.read<String>(otherKey, now: afterWrites)!.value, 'other');
  });

  test('playback refresh invalidates only the playback key', () async {
    final store = InMemorySourceCacheStore();
    final controller = SourceLiveCacheController(store: store);
    final playbackKey = SourceCacheKey(
      sourceId: 'xifan',
      packageVersion: Version.parse('1.0.0'),
      stage: SourceCacheStage.playbackResolution,
      identity: '3541/1/1',
    );
    final metadataKey = key;
    var calls = 0;
    final now = DateTime.utc(2026, 1, 1);

    await controller.getOrLoad<String>(
      key: metadataKey,
      ttl: const Duration(hours: 1),
      load: () async => 'metadata',
      isCacheable: (_) => true,
      now: now,
    );
    await controller.getOrLoad<String>(
      key: playbackKey,
      ttl: const Duration(hours: 1),
      load: () async {
        calls++;
        return 'media-$calls';
      },
      isCacheable: (_) => true,
      now: now,
    );
    final refreshed = await controller.refreshPlayableSource<String>(
      key: playbackKey,
      ttl: const Duration(hours: 1),
      resolve: () async {
        calls++;
        return 'media-$calls';
      },
      isCacheable: (_) => true,
      now: now,
    );

    expect(refreshed, 'media-2');
    expect(calls, 2);
    expect(store.read<String>(metadataKey, now: now)!.value, 'metadata');
    expect(store.read<String>(playbackKey, now: now)!.value, 'media-2');
  });
}

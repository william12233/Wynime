import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/bangumi_sync_models.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';

import '../helpers/test_database.dart';

void main() {
  final now = DateTime.utc(2026, 9, 8, 12);

  test(
    'local-first mutations coalesce one queued operation atomically',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);

      await store.saveCollectionStatus('42', BangumiCollectionStatus.wish);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      await store.setEpisodeWatched('42', '1001', true);
      await store.setEpisodeWatched('42', '1001', false);

      final operations = await store.pendingOperations(now: now);
      expect(operations, hasLength(2));
      final collectionOperation = operations.singleWhere(
        (operation) =>
            operation.kind == BangumiSyncOperationKind.collectionStatus,
      );
      final episodeOperation = operations.singleWhere(
        (operation) =>
            operation.kind == BangumiSyncOperationKind.episodeWatched,
      );
      expect(
        collectionOperation.collectionStatus,
        BangumiCollectionStatus.watching,
      );
      expect(episodeOperation.watched, isFalse);
      expect(await store.pendingCount(), 2);
    },
  );

  test('remote reconciliation persists cache and conflict choices', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);

    final initial = _remote(
      status: BangumiCollectionStatus.wish,
      watched: const <String>{},
    );
    await store.applyRemoteState(initial);
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
    final operation = (await store.pendingOperations()).single;

    final changedRemote = _remote(
      status: BangumiCollectionStatus.completed,
      watched: const {'1001'},
    );
    await store.markConflict(operation, changedRemote);
    await store.applyRemoteState(changedRemote);
    expect(
      (await store.cachedCollections()).single.status,
      BangumiCollectionStatus.watching,
    );
    final conflicts = await store.conflicts();

    expect(conflicts, hasLength(1));
    expect(
      conflicts.single.remoteState.status,
      BangumiCollectionStatus.completed,
    );
    expect(conflicts.single.remoteState.watchedEpisodeIds, {'1001'});
    expect(await store.pendingCount(), 0);

    await store.requeueAgainstRevision(
      conflicts.single.operation,
      changedRemote.remoteRevision,
    );
    expect(await store.conflicts(), isEmpty);
    expect(await store.pendingCount(), 1);
    expect(
      (await store.pendingOperations()).single.baseRemoteRevision,
      changedRemote.remoteRevision,
    );

    final secondOperation = (await store.pendingOperations()).single;
    await store.markConflict(secondOperation, changedRemote);
    await store.adoptRemote((await store.conflicts()).single);
    expect(await store.conflicts(), isEmpty);
    expect(await store.pendingCount(), 0);
    expect(
      (await store.cachedCollections()).single.status,
      BangumiCollectionStatus.completed,
    );
    expect(await store.loadEpisodeProgress('42'), isNotNull);
  });

  test(
    'local-only queued collection remains visible after reauth cache refresh',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final first = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(first);
      await first.saveCollectionStatus('42', BangumiCollectionStatus.watching);

      // The server page does not contain subject 42 yet, but a stale cache
      // page may still be received while the user reauthenticates.
      final second = DriftBangumiLocalStore(database, clock: () => now);
      await second.loadActiveAccount();
      await second.cacheCollection(
        const BangumiCollectionEntry(
          subjectId: '42',
          status: BangumiCollectionStatus.wish,
        ),
      );

      expect(
        (await second.localFirstCollections()).single.status,
        BangumiCollectionStatus.watching,
      );
      expect(
        (await second.cachedCollections()).single.status,
        BangumiCollectionStatus.watching,
      );
      expect(await second.pendingCount(), 1);
    },
  );

  test(
    'reauth cache refresh preserves a persisted local-first collection status',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final first = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(first);
      await first.applyRemoteState(
        _remote(
          status: BangumiCollectionStatus.wish,
          watched: const <String>{},
        ),
      );
      await first.saveCollectionStatus('42', BangumiCollectionStatus.watching);

      // A new controller/store after restart rehydrates the active account,
      // then receives the stale remote page during reauthentication.
      final second = DriftBangumiLocalStore(database, clock: () => now);
      await second.loadActiveAccount();
      await second.cacheCollection(
        const BangumiCollectionEntry(
          subjectId: '42',
          status: BangumiCollectionStatus.wish,
        ),
      );

      expect(
        (await second.cachedCollections()).single.status,
        BangumiCollectionStatus.watching,
      );
      expect(await second.pendingCount(), 1);
    },
  );

  test(
    'restarting the store restores the active non-secret account identity',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final first = DriftBangumiLocalStore(database, clock: () => now);
      await first.saveAccount(
        const BangumiUserIdentity(
          id: '7',
          username: 'alice',
          nickname: 'Alice',
        ),
      );

      final second = DriftBangumiLocalStore(database, clock: () => now);
      final account = await second.loadActiveAccount();
      expect(account?.accountId, '7');
      expect(account?.username, 'alice');
      expect(second.activeAccountId, '7');
    },
  );

  test(
    'schedule cache survives restart and exposes freshness separately',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final first = DriftBangumiLocalStore(database, clock: () => now);
      await first.saveAccount(
        const BangumiUserIdentity(id: '7', username: 'alice'),
      );
      await first.cacheSchedule(const [
        BangumiScheduleEntry(
          id: 'schedule-1',
          subjectId: '42',
          subjectName: '作品',
          airWeekday: 2,
          episodeNumber: 3,
        ),
      ]);

      final second = DriftBangumiLocalStore(database, clock: () => now);
      final account = await second.loadActiveAccount();
      expect(await second.cachedScheduleUpdatedAt(), now);
      expect(account?.scheduleUpdatedAt, now);
      final cached = await second.cachedSchedule();
      expect(cached, hasLength(1));
      expect(cached.single.id, 'schedule-1');
      expect(cached.single.subjectId, '42');
      expect(cached.single.subjectName, '作品');
      expect(cached.single.airWeekday, 2);
      expect(cached.single.episodeNumber, 3);

      await second.cacheSchedule(const <BangumiScheduleEntry>[]);
      expect(await second.cachedSchedule(), isEmpty);
      expect(await second.cachedScheduleUpdatedAt(), now);
    },
  );
}

Future<void> _seed(DriftBangumiLocalStore store) async {
  await store.saveAccount(
    const BangumiUserIdentity(id: '7', username: 'alice'),
  );
  await store.cacheSubject(
    const BangumiSubject(
      id: '42',
      name: 'Title',
      nameCn: '作品',
      summary: 'Summary',
      eps: 2,
    ),
  );
  await store.cacheEpisodes(
    const BangumiEpisodePage(
      episodes: [
        BangumiEpisode(
          id: '1001',
          subjectId: '42',
          name: 'Episode 1',
          nameCn: '第一集',
          sort: 1,
          type: 0,
        ),
        BangumiEpisode(
          id: '1002',
          subjectId: '42',
          name: 'Episode 2',
          nameCn: '第二集',
          sort: 2,
          type: 0,
        ),
      ],
      offset: 0,
      limit: 100,
      total: 2,
    ),
  );
}

BangumiRemoteState _remote({
  required BangumiCollectionStatus status,
  required Set<String> watched,
}) {
  return BangumiRemoteState(
    accountId: '7',
    subjectId: '42',
    status: status,
    watchedEpisodeIds: watched,
    remoteRevision: BangumiRemoteState.fingerprint(
      subjectId: '42',
      status: status,
      watchedEpisodeIds: watched,
    ),
  );
}

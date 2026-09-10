import 'package:flutter_test/flutter_test.dart';
import 'package:drift/drift.dart' show Value;
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/bangumi_sync_models.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
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
    expect(
      (await store.cachedCollections()).single.imageUrl,
      Uri.parse('https://lain.bgm.tv/pic/cover/c/42.jpg'),
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
    'subject detail cache round-trips typed sections and watched state',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await store.saveAccount(
        const BangumiUserIdentity(id: '7', username: 'alice'),
      );
      final snapshot = BangumiSubjectDetailSnapshot(
        subject: const BangumiSubject(
          id: '42',
          name: 'Title',
          nameCn: '作品',
          summary: 'Summary',
          eps: 2,
          totalEpisodes: 2,
          volumes: 1,
          platform: 'TV',
          rank: 5,
          rating: BangumiRating(total: 12, score: 8.25, count: {8: 6}),
          collectionStats: BangumiPublicCollectionStats(
            wish: 1,
            completed: 2,
            watching: 3,
            onHold: 4,
            dropped: 5,
          ),
          infobox: [
            BangumiInfoboxItem(
              key: 'Alias',
              values: [BangumiInfoboxValue(text: 'Alias')],
            ),
          ],
          tags: [BangumiTag(name: 'Action', count: 4, totalCount: 9)],
        ),
        episodes: const BangumiEpisodePage(
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
        characters: const [
          BangumiCharacter(
            id: 'c1',
            name: 'Character',
            actors: [BangumiActor(id: 'p1', name: 'Actor')],
          ),
        ],
        persons: const [
          BangumiPersonCredit(id: 'p2', name: 'Director', career: ['Director']),
        ],
        relations: const [
          BangumiSubjectRelation(
            id: '43',
            type: 2,
            name: 'Related',
            nameCn: '相關作品',
          ),
        ],
      );

      await store.cacheSubjectDetail(snapshot);
      await store.cacheCollection(
        const BangumiCollectionEntry(
          subjectId: '42',
          status: BangumiCollectionStatus.watching,
          totalEpisodes: 2,
          epStatus: 1,
        ),
      );
      await store.applyRemoteState(
        _remote(
          status: BangumiCollectionStatus.watching,
          watched: const {'1001'},
        ),
      );

      final restored = await store.cachedSubjectDetail('42');
      expect(restored, isNotNull);
      expect(restored!.subject.rating?.score, 8.25);
      expect(restored.subject.collectionStats?.dropped, 5);
      expect(restored.subject.infobox.single.values.single.text, 'Alias');
      expect(restored.subject.tags.single.totalCount, 9);
      expect(restored.characters.single.actors.single.name, 'Actor');
      expect(restored.persons.single.career, ['Director']);
      expect(restored.relations.single.nameCn, '相關作品');
      expect(restored.collectionStatus, BangumiCollectionStatus.watching);
      expect(restored.epStatus, 1);
      expect(restored.watchedEpisodeIds, {'1001'});
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

  test('remote-only Bangumi changes are imported into local cache', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);

    await store.applyRemoteState(
      _remote(
        status: BangumiCollectionStatus.completed,
        watched: const {'1001'},
      ),
    );

    expect(
      (await store.cachedCollections()).single.status,
      BangumiCollectionStatus.completed,
    );
    expect((await store.loadEpisodeProgress('42'))?.watchedEpisodeIds, {
      '1001',
    });
    expect(await store.pendingCount(), 0);
  });

  test(
    'blocked and legacy failed rows do not override refreshed remote state',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await database
          .into(database.bangumiSyncOperations)
          .insert(
            BangumiSyncOperationsCompanion.insert(
              operationId: 'blocked-operation',
              accountId: '7',
              subjectId: '42',
              kind: BangumiSyncOperationKind.collectionStatus.name,
              collectionStatus: const Value(3),
              state: BangumiSyncOperationState.blocked.name,
              lastErrorCode: const Value('http_403'),
              createdAt: now,
              updatedAt: now,
            ),
          );

      await store.applyRemoteState(
        _remote(
          status: BangumiCollectionStatus.completed,
          watched: const <String>{},
        ),
      );

      expect(
        (await store.cachedCollections()).single.status,
        BangumiCollectionStatus.completed,
      );
      expect(await store.localFirstCollections(), isEmpty);
      expect(await store.pendingCount(), 0);
      expect(await store.blockedCount(), 1);
    },
  );
}

Future<void> _seed(DriftBangumiLocalStore store) async {
  await store.saveAccount(
    const BangumiUserIdentity(id: '7', username: 'alice'),
  );
  await store.cacheSubject(
    BangumiSubject(
      id: '42',
      name: 'Title',
      nameCn: '作品',
      summary: 'Summary',
      eps: 2,
      imageUrl: Uri.parse('https://lain.bgm.tv/pic/cover/c/42.jpg'),
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

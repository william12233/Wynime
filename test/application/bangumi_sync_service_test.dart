import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/bangumi_sync_service.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';

import '../helpers/test_database.dart';

void main() {
  final now = DateTime.utc(2026, 9, 8, 12);

  test('sync is single-flight and reconciles a successful mutation', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);
    final initial = _remote(BangumiCollectionStatus.wish);
    await store.applyRemoteState(initial);
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);

    final gate = Completer<void>();
    var remoteCalls = 0;
    final reconciled = _remote(BangumiCollectionStatus.watching);
    final client = FakeBangumiClient(
      onRemoteState: (subjectId) async {
        await gate.future;
        remoteCalls++;
        return remoteCalls == 1 ? initial : reconciled;
      },
    );
    final service = BangumiSyncService(
      client: client,
      store: store,
      sessionProvider: () => _session,
      clock: () => now,
    );

    final first = service.syncNow();
    final second = service.syncNow();
    expect(identical(first, second), isTrue);
    gate.complete();

    final result = await first;
    expect(result.processed, 1);
    expect(result.conflicts, 0);
    expect(result.failed, 0);
    expect(client.collectionMutations, [
      ('42', BangumiCollectionStatus.watching),
    ]);
    expect(await store.pendingCount(), 0);
  });

  test(
    'remote revision race becomes a visible conflict without mutation',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      final initial = _remote(BangumiCollectionStatus.wish);
      await store.applyRemoteState(initial);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final remoteChanged = _remote(BangumiCollectionStatus.completed);
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remoteChanged,
      );

      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
      ).syncNow();

      expect(result.conflicts, 1);
      expect(result.processed, 0);
      expect(client.collectionMutations, isEmpty);
      expect(
        (await store.conflicts()).single.remoteState.status,
        BangumiCollectionStatus.completed,
      );
    },
  );

  test(
    'retryable API failure is bounded and records exhausted state',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => throw const BangumiApiException(
          code: 'rate_limited',
          retryable: true,
        ),
      );

      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        maxAttempts: 1,
      ).syncNow();

      expect(result.failed, 1);
      expect(await store.pendingCount(), 0);
      final operation = (await store.pendingOperations()).toList();
      expect(operation, isEmpty);
      expect((await store.conflictOperations()), isEmpty);
    },
  );

  test(
    'rebases multiple local changes for one subject after each mutation',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      final initial = _remote(BangumiCollectionStatus.wish);
      await store.applyRemoteState(initial);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      await store.setEpisodeWatched('42', '1001', true);

      var remote = initial;
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote,
        onCollectionMutation: (subjectId, status) {
          remote = _remote(status, watched: remote.watchedEpisodeIds);
        },
        onEpisodeMutation: (subjectId, episodeId, watched) {
          final watchedEpisodes = {...remote.watchedEpisodeIds};
          if (watched) {
            watchedEpisodes.add(episodeId);
          } else {
            watchedEpisodes.remove(episodeId);
          }
          remote = _remote(
            remote.status ?? BangumiCollectionStatus.wish,
            watched: watchedEpisodes,
          );
        },
      );

      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
      ).syncNow();

      expect(result.processed, 2);
      expect(result.conflicts, 0);
      expect(await store.pendingCount(), 0);
      expect(
        (await store.cachedCollections()).single.status,
        BangumiCollectionStatus.watching,
      );
      expect((await store.loadEpisodeProgress('42'))?.watchedEpisodeIds, {
        '1001',
      });
    },
  );

  test('a post-mutation remote race becomes a visible conflict', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);
    final initial = _remote(BangumiCollectionStatus.wish);
    await store.applyRemoteState(initial);
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
    var remoteCalls = 0;
    final client = FakeBangumiClient(
      onRemoteState: (subjectId) async {
        remoteCalls++;
        return remoteCalls == 1
            ? initial
            : _remote(BangumiCollectionStatus.completed);
      },
    );

    final result = await BangumiSyncService(
      client: client,
      store: store,
      sessionProvider: () => _session,
      clock: () => now,
    ).syncNow();

    expect(result.conflicts, 1);
    expect(result.processed, 0);
    expect(client.collectionMutations, [
      ('42', BangumiCollectionStatus.watching),
    ]);
    expect(
      (await store.conflicts()).single.remoteState.status,
      BangumiCollectionStatus.completed,
    );
  });

  test(
    'auth failure keeps the local mutation visible for reauthentication',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final service = BangumiSyncService(
        client: FakeBangumiClient(
          onRemoteState: (subjectId) async =>
              throw const BangumiApiException(code: 'auth_required'),
        ),
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
      );

      await expectLater(service.syncNow(), throwsA(isA<BangumiApiException>()));

      expect(await store.pendingCount(), 1);
      expect(await store.failedCount(), 0);
      expect(
        (await store.pendingOperations()).single.lastErrorCode,
        'auth_required',
      );
    },
  );

  test('malformed remote payload is a visible failed queue item', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);

    final result = await BangumiSyncService(
      client: FakeBangumiClient(
        onRemoteState: (subjectId) async =>
            throw const BangumiPayloadException('malformed_json'),
      ),
      store: store,
      sessionProvider: () => _session,
      clock: () => now,
    ).syncNow();

    expect(result.failed, 1);
    expect(await store.pendingCount(), 0);
    expect(await store.failedCount(), 1);
  });
}

final _session = BangumiAuthSession(
  accountId: '7',
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  expiresAt: DateTime.utc(2030),
);

final class FakeBangumiClient implements BangumiClient {
  FakeBangumiClient({
    required this.onRemoteState,
    this.onCollectionMutation,
    this.onEpisodeMutation,
  });

  final Future<BangumiRemoteState> Function(String subjectId) onRemoteState;
  final void Function(String subjectId, BangumiCollectionStatus status)?
  onCollectionMutation;
  final void Function(String subjectId, String episodeId, bool watched)?
  onEpisodeMutation;
  Future<BangumiRemoteState> Function(String subjectId)? onRemoteStateOverride;
  final List<(String, BangumiCollectionStatus)> collectionMutations =
      <(String, BangumiCollectionStatus)>[];

  @override
  Future<BangumiUserIdentity> currentUser() async =>
      const BangumiUserIdentity(id: '7', username: 'alice');

  @override
  Future<BangumiCollectionPage> collections({
    int offset = 0,
    int limit = 30,
  }) async => const BangumiCollectionPage(
    collections: <BangumiCollectionEntry>[],
    offset: 0,
    limit: 30,
    total: 0,
  );

  @override
  Future<List<BangumiScheduleEntry>> calendar() async =>
      const <BangumiScheduleEntry>[];

  @override
  Future<BangumiSubject> subject(String id) async => const BangumiSubject(
    id: '42',
    name: 'Title',
    nameCn: '作品',
    summary: '',
    eps: 1,
  );

  @override
  Future<BangumiEpisodePage> episodes(String subjectId) async =>
      const BangumiEpisodePage(
        episodes: <BangumiEpisode>[],
        offset: 0,
        limit: 1,
        total: 0,
      );

  @override
  Future<BangumiRemoteState> remoteState(String subjectId) =>
      onRemoteStateOverride?.call(subjectId) ?? onRemoteState(subjectId);

  @override
  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) async {
    collectionMutations.add((subjectId, status));
    onCollectionMutation?.call(subjectId, status);
  }

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {
    onEpisodeMutation?.call(subjectId, episodeId, watched);
  }
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
      summary: '',
      eps: 1,
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
      ],
      offset: 0,
      limit: 100,
      total: 1,
    ),
  );
}

BangumiRemoteState _remote(
  BangumiCollectionStatus status, {
  Set<String> watched = const <String>{},
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

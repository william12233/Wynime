import 'dart:async';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/bangumi_sync_service.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/bangumi_sync_models.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
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
    expect(result.blocked, 0);
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
    'retryable API failure remains retryable after the foreground limit',
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

      expect(result.retryWaiting, 1);
      expect(result.blocked, 0);
      expect(await store.pendingCount(), 1);
      final operation = (await store.pendingOperations()).toList();
      expect(operation, isEmpty);
      expect((await store.pendingOperations(forceRetry: true)), hasLength(1));
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
    'a stale in-flight response cannot complete a newer coalesced intent',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      final initial = _remote(BangumiCollectionStatus.wish);
      var remote = initial;
      await store.applyRemoteState(initial);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);

      final mutationStarted = Completer<void>();
      final releaseMutation = Completer<void>();
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote,
        onCollectionMutation: (subjectId, status) async {
          if (!mutationStarted.isCompleted) mutationStarted.complete();
          await releaseMutation.future;
          remote = _remote(status);
        },
      );
      final service = BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        delay: (_) async {},
      );

      final inFlight = service.syncNow();
      await mutationStarted.future;
      await store.saveCollectionStatus('42', BangumiCollectionStatus.completed);
      releaseMutation.complete();

      final first = await inFlight;
      expect(first.processed, 0);
      expect(await store.pendingCount(), 1);
      expect(
        (await store.cachedCollections()).single.status,
        BangumiCollectionStatus.completed,
      );
      expect(
        (await store.pendingOperations(
          forceRetry: true,
        )).single.collectionStatus,
        BangumiCollectionStatus.completed,
      );
      expect(remote.status, BangumiCollectionStatus.watching);

      final second = await service.syncNow();
      expect(second.processed, 1);
      expect(await store.pendingCount(), 0);
      expect(client.collectionMutations, [
        ('42', BangumiCollectionStatus.watching),
        ('42', BangumiCollectionStatus.completed),
      ]);
      expect(remote.status, BangumiCollectionStatus.completed);
    },
  );

  test('manual sync requeues only valid HTTP 415 blocked operations', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);
    final desired = _remote(BangumiCollectionStatus.watching);
    await store.applyRemoteState(desired);
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
    final collection = (await store.pendingOperations()).single;
    await store.markBlocked(collection, 'http_415', statusCode: 415);

    await store.setEpisodeWatched('42', '1001', true);
    final episode = (await store.pendingOperations()).single;
    await store.markBlocked(episode, 'http_403', statusCode: 403);

    final client = FakeBangumiClient(
      onRemoteState: (subjectId) async => desired,
    );
    final service = BangumiSyncService(
      client: client,
      store: store,
      sessionProvider: () => _session,
      clock: () => now,
      delay: (_) async {},
    );

    expect((await service.syncNow(forceRetry: false)).processed, 0);
    expect(await store.blockedCount(), 2);

    final result = await service.syncNow();
    expect(result.processed, 1);
    expect(result.conflicts, 0);
    expect(client.collectionMutations, isEmpty);
    expect(await store.pendingCount(), 0);
    expect(await store.blockedCount(), 1);
    expect((await store.conflictOperations()), isEmpty);
  });

  test(
    'a valid HTTP 415 blocked operation can be requeued and verified after mutation',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      final initial = _remote(BangumiCollectionStatus.wish);
      var remote = initial;
      await store.applyRemoteState(initial);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final operation = (await store.pendingOperations()).single;
      await store.markBlocked(operation, 'http_415', statusCode: 415);

      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote,
        onCollectionMutation: (subjectId, status) {
          remote = _remote(status);
        },
      );
      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        delay: (_) async {},
      ).syncNow();

      expect(result.processed, 1);
      expect(result.conflicts, 0);
      expect(client.collectionMutations, [
        ('42', BangumiCollectionStatus.watching),
      ]);
      expect(await store.pendingCount(), 0);
      expect(await store.blockedCount(), 0);
      expect(remote.status, BangumiCollectionStatus.watching);
    },
  );

  test(
    'a legacy blocked 415 sibling cannot overwrite a newer pending intent',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      final initial = _remote(BangumiCollectionStatus.wish);
      var remote = initial;
      await store.applyRemoteState(initial);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final oldBlocked = (await store.pendingOperations()).single;
      await store.markBlocked(oldBlocked, 'http_415', statusCode: 415);

      // This directly reproduces the baseline database shape: the old
      // implementation could not coalesce a new pending row with a blocked
      // 415 row, leaving both logical intents persisted.
      await database
          .into(database.bangumiSyncOperations)
          .insert(
            BangumiSyncOperationsCompanion.insert(
              operationId: 'newer-pending-operation',
              accountId: '7',
              subjectId: '42',
              kind: BangumiSyncOperationKind.collectionStatus.name,
              collectionStatus: Value(
                BangumiCollectionStatus.completed.apiType,
              ),
              baseRemoteRevision: Value(initial.remoteRevision),
              baseCollectionStatus: Value(initial.status!.apiType),
              state: BangumiSyncOperationState.pending.name,
              createdAt: now.add(const Duration(seconds: 1)),
              updatedAt: now.add(const Duration(seconds: 1)),
            ),
          );

      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote,
        onCollectionMutation: (subjectId, status) {
          remote = _remote(status);
        },
      );
      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        delay: (_) async {},
      ).syncNow();

      expect(result.processed, 1);
      expect(result.conflicts, 0);
      expect(client.collectionMutations, [
        ('42', BangumiCollectionStatus.completed),
      ]);
      expect(remote.status, BangumiCollectionStatus.completed);
      expect(await store.pendingCount(), 0);
      expect(await store.blockedCount(), 0);
      expect(await store.recoverableHttp415Operations(), isEmpty);
    },
  );

  test(
    'a requeued blocked 415 row cannot leave an active duplicate target',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      final initial = _remote(BangumiCollectionStatus.wish);
      var remote = initial;
      await store.applyRemoteState(initial);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final oldBlocked = (await store.pendingOperations()).single;
      await store.markBlocked(oldBlocked, 'http_415', statusCode: 415);
      await database
          .into(database.bangumiSyncOperations)
          .insert(
            BangumiSyncOperationsCompanion.insert(
              operationId: 'newer-pending-operation-2',
              accountId: '7',
              subjectId: '42',
              kind: BangumiSyncOperationKind.collectionStatus.name,
              collectionStatus: Value(
                BangumiCollectionStatus.completed.apiType,
              ),
              baseRemoteRevision: Value(initial.remoteRevision),
              baseCollectionStatus: Value(initial.status!.apiType),
              state: BangumiSyncOperationState.pending.name,
              createdAt: now.add(const Duration(seconds: 1)),
              updatedAt: now.add(const Duration(seconds: 1)),
            ),
          );

      expect(await store.requeueHttp415(oldBlocked), isTrue);

      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote,
        onCollectionMutation: (subjectId, status) {
          remote = _remote(status);
        },
      );
      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        delay: (_) async {},
      ).syncNow();

      expect(result.processed, 1);
      expect(client.collectionMutations, [
        ('42', BangumiCollectionStatus.completed),
      ]);
      expect(remote.status, BangumiCollectionStatus.completed);
      expect(await store.pendingCount(), 0);
      expect(await store.blockedCount(), 0);
    },
  );

  test('HTTP 415 mutation failures remain blocked but recoverable', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);
    await store.applyRemoteState(_remote(BangumiCollectionStatus.wish));
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);

    final result = await BangumiSyncService(
      client: FakeBangumiClient(
        onRemoteState: (subjectId) async =>
            _remote(BangumiCollectionStatus.wish),
        onCollectionMutation: (subjectId, status) async =>
            throw const BangumiApiException(
              code: 'http_415',
              statusCode: 415,
              retryable: false,
            ),
      ),
      store: store,
      sessionProvider: () => _session,
      clock: () => now,
    ).syncNow();

    expect(result.processed, 0);
    expect(result.blocked, 1);
    expect(await store.pendingCount(), 0);
    expect(await store.blockedCount(), 1);
    expect(await store.recoverableHttp415Operations(), hasLength(1));
  });

  test(
    'three rapid watching-to-dropped mutations converge independently',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      for (final subjectId in ['43', '44']) {
        await store.cacheSubject(
          BangumiSubject(
            id: subjectId,
            name: 'Title $subjectId',
            nameCn: '作品 $subjectId',
            summary: '',
            eps: 1,
          ),
        );
      }

      final remote = <String, BangumiRemoteState>{
        for (final subjectId in ['42', '43', '44'])
          subjectId: _remote(
            BangumiCollectionStatus.watching,
            subjectId: subjectId,
          ),
      };
      for (final state in remote.values) {
        await store.applyRemoteState(state);
      }
      for (final subjectId in remote.keys) {
        await store.saveCollectionStatus(
          subjectId,
          BangumiCollectionStatus.dropped,
        );
      }

      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote[subjectId]!,
        onCollectionMutation: (subjectId, status) {
          remote[subjectId] = _remote(status, subjectId: subjectId);
        },
      );
      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
      ).syncNow();

      expect(result.processed, 3);
      expect(result.conflicts, 0);
      expect(await store.pendingCount(), 0);
      expect(
        (await store.cachedCollections()).map((entry) => entry.status).toSet(),
        {BangumiCollectionStatus.dropped},
      );
      expect(remote.values.map((state) => state.status).toSet(), {
        BangumiCollectionStatus.dropped,
      });
      expect(client.collectionMutations.toSet(), {
        ('42', BangumiCollectionStatus.dropped),
        ('43', BangumiCollectionStatus.dropped),
        ('44', BangumiCollectionStatus.dropped),
      });
    },
  );

  test(
    'synchronizes every collection status and the dropped-to-watching reverse',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      var remote = _remote(BangumiCollectionStatus.completed);
      await store.applyRemoteState(remote);
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote,
        onCollectionMutation: (subjectId, status) {
          remote = _remote(status);
        },
      );
      final service = BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
      );

      for (final status in BangumiCollectionStatus.values) {
        await store.saveCollectionStatus('42', status);
        final result = await service.syncNow();
        expect(result.processed, 1);
        expect(remote.status, status);
      }

      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final reverse = await service.syncNow();
      expect(reverse.processed, 1);
      expect(remote.status, BangumiCollectionStatus.watching);
      expect(await store.pendingCount(), 0);
      expect(client.collectionMutations, [
        ('42', BangumiCollectionStatus.wish),
        ('42', BangumiCollectionStatus.completed),
        ('42', BangumiCollectionStatus.watching),
        ('42', BangumiCollectionStatus.onHold),
        ('42', BangumiCollectionStatus.dropped),
        ('42', BangumiCollectionStatus.watching),
      ]);
    },
  );

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

  test('malformed remote payload remains a retryable queue item', () async {
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

    expect(result.retryWaiting, 1);
    expect(result.blocked, 0);
    expect(await store.pendingCount(), 1);
    expect(await store.blockedCount(), 0);
  });

  test(
    'remote already equals desired state completes without mutation',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      final desired = _remote(BangumiCollectionStatus.watching);
      await store.applyRemoteState(desired);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => desired,
      );

      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
      ).syncNow();

      expect(result.processed, 1);
      expect(client.collectionMutations, isEmpty);
      expect(await store.pendingCount(), 0);
    },
  );

  test('post-write verification retries a stale remote read', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);
    final initial = _remote(BangumiCollectionStatus.wish);
    final desired = _remote(BangumiCollectionStatus.watching);
    await store.applyRemoteState(initial);
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
    var remoteCalls = 0;
    final client = FakeBangumiClient(
      onRemoteState: (subjectId) async {
        remoteCalls++;
        return remoteCalls < 3 ? initial : desired;
      },
    );

    final result = await BangumiSyncService(
      client: client,
      store: store,
      sessionProvider: () => _session,
      clock: () => now,
      delay: (_) async {},
    ).syncNow();

    expect(result.processed, 1);
    expect(remoteCalls, 3);
    expect(await store.pendingCount(), 0);
  });

  test(
    'lost mutation response is recovered by observing remote desired state',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      var remote = _remote(BangumiCollectionStatus.wish);
      await store.applyRemoteState(remote);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async => remote,
        onCollectionMutation: (subjectId, status) {
          remote = _remote(status);
          throw const BangumiApiException(
            code: 'network_error',
            retryable: true,
          );
        },
      );
      final service = BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        delay: (_) async {},
        retryJitter: () => 0,
      );

      final first = await service.syncNow();
      expect(first.retryWaiting, 1);
      expect(await store.pendingCount(), 1);

      final second = await service.syncNow();
      expect(second.processed, 1);
      expect(client.collectionMutations, [
        ('42', BangumiCollectionStatus.watching),
      ]);
      expect(await store.pendingCount(), 0);
    },
  );

  test('HTTP 429 and 5xx remain retryable with diagnostics', () async {
    for (final failure in <({int statusCode, String code})>[
      (statusCode: 429, code: 'rate_limited'),
      (statusCode: 503, code: 'remote_server_error'),
    ]) {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final result = await BangumiSyncService(
        client: FakeBangumiClient(
          onRemoteState: (subjectId) async => throw BangumiApiException(
            code: failure.code,
            statusCode: failure.statusCode,
            retryable: true,
          ),
        ),
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        retryJitter: () => 0,
      ).syncNow();

      expect(result.retryWaiting, 1);
      expect(await store.pendingCount(), 1);
      expect(await store.blockedCount(), 0);
      expect(
        (await store.pendingOperations(forceRetry: true)).single.statusCode,
        failure.statusCode,
      );
    }
  });

  test('network timeout and unknown failures remain retryable', () async {
    for (final error in <Object>[
      const BangumiApiException(code: 'network_timeout', retryable: true),
      StateError('temporary provider failure'),
    ]) {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      final result = await BangumiSyncService(
        client: FakeBangumiClient(
          onRemoteState: (subjectId) async => throw error,
        ),
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        retryJitter: () => 0,
      ).syncNow();

      expect(result.retryWaiting, 1);
      expect(await store.pendingCount(), 1);
      expect(await store.blockedCount(), 0);
    }
  });

  test(
    'a later manual retry succeeds after an earlier transient failure',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      var calls = 0;
      var remote = _remote(BangumiCollectionStatus.wish);
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async {
          calls++;
          if (calls == 1) {
            throw const BangumiApiException(
              code: 'network_error',
              retryable: true,
            );
          }
          return remote;
        },
        onCollectionMutation: (subjectId, status) {
          remote = _remote(status);
        },
      );
      final service = BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        retryJitter: () => 0,
      );

      expect((await service.syncNow()).retryWaiting, 1);
      expect((await service.syncNow()).processed, 1);
      expect(await store.pendingCount(), 0);
    },
  );

  test('remote changes to another field are merged safely', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(store);
    final initial = _remote(BangumiCollectionStatus.wish);
    var remote = initial;
    await store.applyRemoteState(initial);
    await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
    final client = FakeBangumiClient(
      onRemoteState: (subjectId) async => remote,
      onCollectionMutation: (subjectId, status) {
        remote = _remote(status, watched: {'1001'});
      },
    );

    // Another client watched an episode, but did not touch collection status.
    remote = _remote(BangumiCollectionStatus.wish, watched: {'1001'});
    final result = await BangumiSyncService(
      client: client,
      store: store,
      sessionProvider: () => _session,
      clock: () => now,
    ).syncNow();

    expect(result.processed, 1);
    expect(result.conflicts, 0);
    expect(await store.pendingCount(), 0);
    expect((await store.loadEpisodeProgress('42'))?.watchedEpisodeIds, {
      '1001',
    });
  });

  test(
    'automatic retry respects backoff while manual retry bypasses it',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);
      var first = true;
      final client = FakeBangumiClient(
        onRemoteState: (subjectId) async {
          if (first) {
            first = false;
            throw const BangumiApiException(
              code: 'rate_limited',
              retryable: true,
            );
          }
          return _remote(BangumiCollectionStatus.watching);
        },
      );
      final service = BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
        retryJitter: () => 0,
      );

      expect((await service.syncNow()).retryWaiting, 1);
      expect(await store.pendingOperations(), isEmpty);
      expect(await store.pendingOperations(forceRetry: true), hasLength(1));
      expect((await service.syncNow(forceRetry: false)).processed, 0);
      expect((await service.syncNow()).processed, 1);
      expect(await store.pendingCount(), 0);
    },
  );

  test('retryable queue state survives a store restart', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final firstStore = DriftBangumiLocalStore(database, clock: () => now);
    await _seed(firstStore);
    await firstStore.saveCollectionStatus(
      '42',
      BangumiCollectionStatus.watching,
    );
    await BangumiSyncService(
      client: FakeBangumiClient(
        onRemoteState: (subjectId) async => throw const BangumiApiException(
          code: 'network_timeout',
          retryable: true,
        ),
      ),
      store: firstStore,
      sessionProvider: () => _session,
      clock: () => now,
      retryJitter: () => 0,
    ).syncNow();

    final restarted = DriftBangumiLocalStore(database, clock: () => now);
    await restarted.loadActiveAccount();
    expect(await restarted.pendingOperations(), isEmpty);
    final persisted = await restarted.pendingOperations(forceRetry: true);
    expect(persisted, hasLength(1));
    expect(persisted.single.attempts, 1);
    expect(persisted.single.nextAttemptAt, isNotNull);
  });

  test(
    'structurally invalid local operation becomes blocked, not poisoned',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seed(store);
      await store.applyRemoteState(_remote(BangumiCollectionStatus.wish));
      await database
          .into(database.bangumiSyncOperations)
          .insert(
            BangumiSyncOperationsCompanion.insert(
              operationId: 'invalid-operation',
              accountId: '7',
              subjectId: '42',
              kind: BangumiSyncOperationKind.collectionStatus.name,
              state: BangumiSyncOperationState.pending.name,
              createdAt: now,
              updatedAt: now,
            ),
          );

      final result = await BangumiSyncService(
        client: FakeBangumiClient(
          onRemoteState: (subjectId) async =>
              _remote(BangumiCollectionStatus.wish),
        ),
        store: store,
        sessionProvider: () => _session,
        clock: () => now,
      ).syncNow();

      expect(result.blocked, 1);
      expect(await store.pendingCount(), 0);
      expect(await store.blockedCount(), 1);
      expect(await store.localFirstCollections(), isEmpty);
      expect(
        (await store.cachedCollections()).single.status,
        BangumiCollectionStatus.wish,
      );
    },
  );
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
  final FutureOr<void> Function(
    String subjectId,
    BangumiCollectionStatus status,
  )?
  onCollectionMutation;
  final FutureOr<void> Function(
    String subjectId,
    String episodeId,
    bool watched,
  )?
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
  Future<List<BangumiCharacter>> characters(String subjectId) async =>
      const <BangumiCharacter>[];

  @override
  Future<List<BangumiPersonCredit>> persons(String subjectId) async =>
      const <BangumiPersonCredit>[];

  @override
  Future<List<BangumiSubjectRelation>> relations(String subjectId) async =>
      const <BangumiSubjectRelation>[];

  @override
  Future<BangumiRemoteState> remoteState(String subjectId) =>
      onRemoteStateOverride?.call(subjectId) ?? onRemoteState(subjectId);

  @override
  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) async {
    collectionMutations.add((subjectId, status));
    await onCollectionMutation?.call(subjectId, status);
  }

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {
    await onEpisodeMutation?.call(subjectId, episodeId, watched);
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
  String subjectId = '42',
  Set<String> watched = const <String>{},
}) {
  return BangumiRemoteState(
    accountId: '7',
    subjectId: subjectId,
    status: status,
    watchedEpisodeIds: watched,
    remoteRevision: BangumiRemoteState.fingerprint(
      subjectId: subjectId,
      status: status,
      watchedEpisodeIds: watched,
    ),
  );
}

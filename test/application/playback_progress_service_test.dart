import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/bangumi_sync_service.dart';
import 'package:wynime/src/application/playback/playback_progress_service.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/bangumi_sync_models.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/watch_progress.dart';
import 'package:wynime/src/domain/repositories/watch_history_repository.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';
import 'package:wynime/src/domain/services/watch_progress_policy.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';
import 'package:wynime/src/infrastructure/repositories/drift_watch_history_repository.dart';

import '../helpers/playback_test_support.dart';
import '../helpers/test_database.dart';

void main() {
  final now = DateTime.utc(2026, 9, 12, 12);

  test(
    'EPROG-R01 and matrix 1/34/35 persist identity and the final checkpoint',
    () async {
      final history = _MemoryWatchHistory();
      final session = testPlaybackSession(sessionId: 'session-a');
      final events = StreamController<PlaybackEvent>.broadcast(sync: true);
      addTearDown(events.close);
      final binding =
          await PlaybackProgressService(
            history: history,
            clock: () => now,
          ).bind(
            session: session,
            events: events.stream,
            duration: const Duration(minutes: 24),
            playerBackendId: 'router',
          );
      addTearDown(binding.close);

      events.add(_event(session, 1, const Duration(minutes: 8)));
      await binding.flush();
      events.add(
        _event(
          session,
          2,
          const Duration(minutes: 8, seconds: 2),
          state: PlaybackState.closed,
        ),
      );
      await binding.close();

      final saved = await history.findByIdentity(session.episode);
      expect(saved, isNotNull);
      expect(saved!.sourceId, session.episode.sourceId);
      expect(saved.lineId, session.episode.lineId);
      expect(saved.subjectId, session.episode.subjectId);
      expect(saved.episodeId, session.episode.episodeId);
      expect(saved.position, const Duration(minutes: 8, seconds: 2));
      expect(saved.duration, const Duration(minutes: 24));
      expect(saved.isCompleted, isFalse);
      expect(saved.timelineMapId, session.timelineMapIdentity);
      expect(saved.playerBackendId, 'router');
    },
  );

  test(
    'EPROG-R02/R03/R12/R18 and matrix 6/7/8/31/32 keep generations and seeks separate',
    () async {
      final history = _MemoryWatchHistory();
      final first = testPlaybackSession(sessionId: 'session-first');
      final second = testPlaybackSession(sessionId: 'session-second');
      final firstEvents = StreamController<PlaybackEvent>.broadcast(sync: true);
      final secondEvents = StreamController<PlaybackEvent>.broadcast(
        sync: true,
      );
      addTearDown(firstEvents.close);
      addTearDown(secondEvents.close);
      final service = PlaybackProgressService(
        history: history,
        clock: () => now,
        checkpointDelta: const Duration(seconds: 1),
      );

      final firstBinding = await service.bind(
        session: first,
        events: firstEvents.stream,
        duration: const Duration(minutes: 24),
      );
      firstEvents.add(
        _event(first, 1, const Duration(minutes: 18, seconds: 20)),
      );
      await firstBinding.flush();

      final secondBinding = await service.bind(
        session: second,
        events: secondEvents.stream,
        duration: const Duration(minutes: 24),
      );
      secondEvents.add(
        _event(second, 1, const Duration(minutes: 21, seconds: 10)),
      );
      await secondBinding.flush();
      // This is a deliberate seek in the current session, not a stale event.
      secondEvents.add(
        _event(
          second,
          2,
          const Duration(minutes: 10),
          state: PlaybackState.paused,
        ),
      );
      await secondBinding.flush();
      // The old binding was closed by bind(); its late callback is ignored.
      firstEvents.add(
        _event(first, 2, const Duration(minutes: 18, seconds: 25)),
      );
      await secondBinding.close();

      final saved = await history.findByIdentity(first.episode);
      expect(saved!.position, const Duration(minutes: 10));
      expect(saved.updatedAt, now);
      expect(await history.rows, hasLength(1));
    },
  );

  test(
    'EPROG-R18 and matrix 33 keep three rapid episode switches independent',
    () async {
      final history = _MemoryWatchHistory();
      final service = PlaybackProgressService(
        history: history,
        clock: () => now,
        checkpointDelta: const Duration(seconds: 1),
      );
      final controllers = <StreamController<PlaybackEvent>>[];
      addTearDown(() async {
        for (final controller in controllers) {
          await controller.close();
        }
      });

      PlaybackProgressBinding? latestBinding;
      PlaybackSession? firstSession;
      StreamController<PlaybackEvent>? firstEvents;
      for (var index = 1; index <= 3; index++) {
        final session = testPlaybackSession(
          sessionId: 'rapid-session-$index',
          episode: testEpisode(episodeId: 'episode-$index'),
        );
        final events = StreamController<PlaybackEvent>.broadcast(sync: true);
        controllers.add(events);
        latestBinding = await service.bind(
          session: session,
          events: events.stream,
          duration: const Duration(minutes: 24),
        );
        events.add(_event(session, 1, Duration(minutes: index)));
        await latestBinding.flush();

        // A late event from an already-switched episode must not mutate the
        // newly active episode.
        if (index == 1) {
          firstSession = session;
          firstEvents = events;
        } else if (index == 2) {
          firstEvents!.add(
            _event(firstSession!, 2, const Duration(minutes: 23)),
          );
        }
      }
      await latestBinding!.close();

      final rows = await history.rows;
      expect(rows, hasLength(3));
      expect(rows.map((row) => row.episodeId).toSet(), {
        'episode-1',
        'episode-2',
        'episode-3',
      });
      expect(rows.map((row) => row.position).toSet(), {
        const Duration(minutes: 1),
        const Duration(minutes: 2),
        const Duration(minutes: 3),
      });
    },
  );

  test(
    'EPROG-R04 and matrix 2/3/34 throttle ticks while preserving close state',
    () async {
      var current = now;
      final history = _MemoryWatchHistory();
      final session = testPlaybackSession(sessionId: 'throttle-session');
      final events = StreamController<PlaybackEvent>.broadcast(sync: true);
      addTearDown(events.close);
      final binding =
          await PlaybackProgressService(
            history: history,
            clock: () => current,
            checkpointInterval: const Duration(seconds: 10),
            checkpointDelta: const Duration(seconds: 5),
          ).bind(
            session: session,
            events: events.stream,
            duration: const Duration(minutes: 24),
          );

      events.add(_event(session, 1, const Duration(seconds: 6)));
      await binding.flush();
      expect(history.saveCount, 1);
      events.add(_event(session, 2, const Duration(seconds: 7)));
      await binding.flush();
      expect(history.saveCount, 1);
      current = current.add(const Duration(seconds: 10));
      events.add(_event(session, 3, const Duration(seconds: 8)));
      await binding.flush();
      expect(history.saveCount, 2);
      events.add(
        _event(
          session,
          4,
          const Duration(seconds: 9),
          state: PlaybackState.closed,
        ),
      );
      await binding.close();
      expect(history.saveCount, 3);
      expect(
        (await history.findByIdentity(session.episode))!.position,
        const Duration(seconds: 9),
      );

      final tinyHistory = _MemoryWatchHistory();
      final tinyEvents = StreamController<PlaybackEvent>.broadcast(sync: true);
      addTearDown(tinyEvents.close);
      final tinyBinding =
          await PlaybackProgressService(
            history: tinyHistory,
            clock: () => now,
          ).bind(
            session: session,
            events: tinyEvents.stream,
            duration: const Duration(minutes: 24),
          );
      tinyEvents.add(_event(session, 1, const Duration(seconds: 1)));
      await tinyBinding.close();
      expect(await tinyHistory.rows, isEmpty);
    },
  );

  test(
    'EPROG-R05/R10/R11 and matrix 9/10/29/30 use one safe completion/resume policy',
    () {
      const policy = WatchProgressPolicy();
      const duration = Duration(minutes: 10);
      final threshold = const Duration(minutes: 9);
      expect(
        policy.isAtCompletionThreshold(
          position: threshold - const Duration(microseconds: 1),
          duration: duration,
        ),
        isFalse,
      );
      expect(
        policy.isAtCompletionThreshold(position: threshold, duration: duration),
        isTrue,
      );
      expect(
        policy.isAtCompletionThreshold(
          position: threshold + const Duration(seconds: 1),
          duration: duration,
        ),
        isTrue,
      );
      expect(
        policy.isAtCompletionThreshold(
          position: const Duration(seconds: 9),
          duration: const Duration(seconds: 10),
        ),
        isTrue,
      );
      expect(
        policy.resumePosition(
          WatchProgress(
            progressId: 'tiny',
            sourceId: 'source',
            lineId: 'line',
            subjectId: 'subject',
            episodeId: 'episode',
            position: const Duration(seconds: 1),
            duration: duration,
            isCompleted: false,
            updatedAt: now,
          ),
        ),
        Duration.zero,
      );
      expect(
        policy.resumePosition(
          WatchProgress(
            progressId: 'resume',
            sourceId: 'source',
            lineId: 'line',
            subjectId: 'subject',
            episodeId: 'episode',
            position: const Duration(minutes: 4),
            duration: duration,
            isCompleted: false,
            updatedAt: now,
          ),
        ),
        const Duration(minutes: 4),
      );
      expect(
        policy.resumePosition(
          WatchProgress(
            progressId: 'complete',
            sourceId: 'source',
            lineId: 'line',
            subjectId: 'subject',
            episodeId: 'episode',
            position: const Duration(minutes: 9, seconds: 30),
            duration: duration,
            isCompleted: false,
            updatedAt: now,
          ),
        ),
        Duration.zero,
      );
      final continueItems = policy.selectContinueWatching([
        WatchProgress(
          progressId: 'newest',
          sourceId: 'source',
          lineId: 'line',
          subjectId: 'subject',
          episodeId: 'episode-newest',
          position: const Duration(minutes: 2),
          duration: duration,
          isCompleted: false,
          updatedAt: now.add(const Duration(minutes: 1)),
        ),
        WatchProgress(
          progressId: 'completed',
          sourceId: 'source',
          lineId: 'line',
          subjectId: 'subject',
          episodeId: 'episode-completed',
          position: duration,
          duration: duration,
          isCompleted: true,
          updatedAt: now,
        ),
      ]);
      expect(continueItems.map((value) => value.progressId), ['newest']);
    },
  );

  test(
    'EPROG-R06/R07/R19 and matrix 11/12/13/14 enqueue watched true once',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seedBangumi(store);
      final history = DriftWatchHistoryRepository(database);
      final session = testPlaybackSession(sessionId: 'completion-session');
      final events = StreamController<PlaybackEvent>.broadcast(sync: true);
      addTearDown(events.close);
      final binding =
          await PlaybackProgressService(
            history: history,
            bangumi: store,
            clock: () => now,
          ).bind(
            session: session,
            events: events.stream,
            duration: const Duration(minutes: 10),
            bangumiEpisode: BangumiEpisodeTarget(
              subjectId: '42',
              episodeId: '1001',
            ),
          );

      events.add(_event(session, 1, const Duration(minutes: 9)));
      await binding.flush();
      expect(binding.completionIntentQueued, isTrue);
      expect(await store.pendingCount(), 1);
      expect((await store.pendingOperations()).single.watched, isTrue);

      events.add(_event(session, 2, const Duration(minutes: 9, seconds: 5)));
      events.add(
        _event(
          session,
          3,
          const Duration(minutes: 10),
          state: PlaybackState.ended,
        ),
      );
      await binding.close();
      expect(await store.pendingCount(), 1);

      // Replaying an already completed row never creates watched=false.
      final replayEvents = StreamController<PlaybackEvent>.broadcast(
        sync: true,
      );
      addTearDown(replayEvents.close);
      final replay =
          await PlaybackProgressService(
            history: history,
            bangumi: store,
            clock: () => now,
          ).bind(
            session: testPlaybackSession(sessionId: 'replay-session'),
            events: replayEvents.stream,
            duration: const Duration(minutes: 10),
            bangumiEpisode: BangumiEpisodeTarget(
              subjectId: '42',
              episodeId: '1001',
            ),
          );
      replayEvents.add(
        _event(replay.session, 1, Duration.zero, state: PlaybackState.playing),
      );
      await replay.close();
      expect((await store.pendingOperations()).single.watched, isTrue);

      // Only this explicit domain action is allowed to produce unwatched.
      await store.setEpisodeWatched('42', '1001', false);
      expect((await store.pendingOperations()).single.watched, isFalse);
    },
  );

  test(
    'EPROG-R08/R09/R20 and matrix 15/16/17/19 reconnect a local completion truthfully',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seedBangumi(store);
      final history = DriftWatchHistoryRepository(database);
      final session = testPlaybackSession(sessionId: 'offline-session');
      final events = StreamController<PlaybackEvent>.broadcast(sync: true);
      addTearDown(events.close);
      final binding =
          await PlaybackProgressService(
            history: history,
            bangumi: store,
            clock: () => now,
          ).bind(
            session: session,
            events: events.stream,
            duration: const Duration(minutes: 10),
            bangumiEpisode: BangumiEpisodeTarget(
              subjectId: '42',
              episodeId: '1001',
            ),
          );
      events.add(_event(session, 1, const Duration(minutes: 9)));
      await binding.close();
      expect(
        (await history.findByIdentity(session.episode))!.isCompleted,
        isTrue,
      );
      expect(await store.pendingCount(), 1);

      // Re-materializing repositories represents an app/database restart;
      // the existing queue row is not duplicated by opening the row again.
      final restartedHistory = DriftWatchHistoryRepository(database);
      final restartedStore = DriftBangumiLocalStore(database, clock: () => now);
      await restartedStore.loadActiveAccount();
      expect(
        (await restartedHistory.findByIdentity(session.episode))!.isCompleted,
        isTrue,
      );
      expect(await restartedStore.pendingCount(), 1);

      final remoteClient = _FakeBangumiClient(
        remote: _remote(watched: const <String>{}),
      );
      final result = await BangumiSyncService(
        client: remoteClient,
        store: restartedStore,
        sessionProvider: () => _session('account-a'),
        clock: () => now,
        delay: (_) async {},
      ).syncNow();
      expect(result.processed, 1);
      expect(remoteClient.episodeMutations, [('42', '1001', true)]);
      expect(await restartedStore.pendingCount(), 0);
      expect(remoteClient.remote.watchedEpisodeIds, {'1001'});
    },
  );

  test(
    'EPROG-R06/R08/R09 and matrix 15/19 repair a missing completion intent on rebind',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seedBangumi(store);
      final history = DriftWatchHistoryRepository(database);
      final session = testPlaybackSession(sessionId: 'recovery-session');

      // Simulate the process ending after WatchHistory.save completed but
      // before BangumiLocalStore.setEpisodeWatched could create its queue row.
      await history.save(
        WatchProgress(
          progressId: 'recovery-progress',
          sourceId: session.episode.sourceId,
          lineId: session.episode.lineId,
          subjectId: session.episode.subjectId,
          episodeId: session.episode.episodeId,
          position: const Duration(minutes: 9),
          duration: const Duration(minutes: 10),
          isCompleted: true,
          updatedAt: now,
        ),
      );
      expect(await store.pendingCount(), 0);

      final events = StreamController<PlaybackEvent>.broadcast(sync: true);
      addTearDown(events.close);
      final binding =
          await PlaybackProgressService(
            history: history,
            bangumi: store,
            clock: () => now,
          ).bind(
            session: session,
            events: events.stream,
            duration: const Duration(minutes: 10),
            bangumiEpisode: BangumiEpisodeTarget(
              subjectId: '42',
              episodeId: '1001',
            ),
          );

      expect(binding.completionIntentQueued, isTrue);
      expect(await store.pendingCount(), 1);
      final operation = (await store.pendingOperations()).single;
      expect(operation.subjectId, '42');
      expect(operation.episodeId, '1001');
      expect(operation.watched, isTrue);

      // A later close/rebind remains idempotent and cannot create watched=false.
      await binding.close();
      expect(await store.pendingCount(), 1);
    },
  );

  test(
    'EPROG-R13/R14/R15 and matrix 20/21/22 import remote watched separately',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seedBangumi(store);
      final history = DriftWatchHistoryRepository(database);

      await store.applyRemoteState(_remote(watched: const {'1001'}));
      final remoteOnly = await store.loadEpisodeProgress('42');
      expect(remoteOnly!.watchedEpisodeIds, {'1001'});
      expect(
        await history.findByIdentity(
          SourceEpisodeIdentity(
            sourceId: 'source',
            lineId: 'line',
            subjectId: 'subject',
            episodeId: 'episode-remote-only',
          ),
        ),
        isNull,
      );

      final local = WatchProgress(
        progressId: 'local-progress',
        sourceId: 'source',
        lineId: 'line',
        subjectId: 'subject',
        episodeId: 'episode-local',
        position: const Duration(minutes: 4),
        duration: const Duration(minutes: 10),
        isCompleted: false,
        updatedAt: now,
      );
      await history.save(local);
      await store.applyRemoteState(_remote(watched: const {'1001'}));
      final preserved = await history.findByIdentity(localIdentity);
      expect(preserved!.position, local.position);
      expect(preserved.isCompleted, isFalse);
    },
  );

  test(
    'EPROG-R16 and matrix 23 never guess a Bangumi episode mapping',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seedBangumi(store);
      final history = DriftWatchHistoryRepository(database);
      final session = testPlaybackSession(sessionId: 'unmapped-session');
      final events = StreamController<PlaybackEvent>.broadcast(sync: true);
      addTearDown(events.close);
      final binding =
          await PlaybackProgressService(
            history: history,
            bangumi: store,
            clock: () => now,
          ).bind(
            session: session,
            events: events.stream,
            duration: const Duration(minutes: 10),
          );
      events.add(_event(session, 1, const Duration(minutes: 9)));
      await binding.close();

      expect(binding.lastBangumiErrorCode, 'subject_mapping_required');
      expect(
        (await history.findByIdentity(session.episode))!.isCompleted,
        isTrue,
      );
      expect(await store.pendingCount(), 0);
    },
  );

  test(
    'EPROG-R17 and matrix 24 keep account A queue rows out of account B sync',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seedBangumi(store, accountId: 'account-a');
      await store.setEpisodeWatched('42', '1001', true);
      expect(await store.pendingCount(), 1);

      await store.saveAccount(
        const BangumiUserIdentity(id: 'account-b', username: 'b'),
      );
      final client = _FakeBangumiClient(
        remote: _remote(accountId: 'account-b'),
      );
      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session('account-b'),
        clock: () => now,
        delay: (_) async {},
      ).syncNow();

      expect(result.processed, 0);
      expect(client.episodeMutations, isEmpty);
      expect(await store.pendingCount(), 0);
      final rows = await database.select(database.bangumiSyncOperations).get();
      expect(rows.single.accountId, 'account-a');
      expect(rows.single.state, BangumiSyncOperationState.pending.name);
    },
  );

  test(
    'EPROG-R20 and matrix 25/26/27/28 preserve retryable and permanent failures',
    () async {
      for (final failure in <({String code, int? status, bool retryable})>[
        (code: 'auth_required', status: 401, retryable: false),
        (code: 'rate_limited', status: 429, retryable: true),
        (code: 'remote_server_error', status: 503, retryable: true),
      ]) {
        final database = openTestDatabase();
        final store = DriftBangumiLocalStore(database, clock: () => now);
        await _seedBangumi(store);
        await store.setEpisodeWatched('42', '1001', true);
        final client = _FakeBangumiClient(
          remote: _remote(watched: const <String>{}),
          remoteFailure: BangumiApiException(
            code: failure.code,
            statusCode: failure.status,
            retryable: failure.retryable,
          ),
        );
        BangumiSyncResult? result;
        try {
          result = await BangumiSyncService(
            client: client,
            store: store,
            sessionProvider: () => _session('account-a'),
            clock: () => now,
            delay: (_) async {},
          ).syncNow();
        } on BangumiApiException catch (error) {
          expect(failure.code, 'auth_required');
          expect(error.code, failure.code);
        }
        expect(result?.processed ?? 0, 0);
        expect(await store.pendingCount(), 1);
        await database.close();
      }

      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database, clock: () => now);
      await _seedBangumi(store);
      await store.setEpisodeWatched('42', '1001', true);
      final client = _FakeBangumiClient(
        remote: _remote(watched: const <String>{}),
        episodeFailure: const BangumiApiException(
          code: 'not_found',
          statusCode: 404,
        ),
      );
      final result = await BangumiSyncService(
        client: client,
        store: store,
        sessionProvider: () => _session('account-a'),
        clock: () => now,
        delay: (_) async {},
      ).syncNow();
      expect(result.processed, 0);
      expect(result.blocked, 1);
      expect(await store.pendingCount(), 0);
      expect(await store.blockedCount(), 1);
    },
  );
}

final SourceEpisodeIdentity localIdentity = SourceEpisodeIdentity(
  sourceId: 'source',
  lineId: 'line',
  subjectId: 'subject',
  episodeId: 'episode-local',
);

PlaybackEvent _event(
  PlaybackSession session,
  int sequence,
  Duration position, {
  PlaybackState state = PlaybackState.playing,
  Duration? duration,
}) => PlaybackEvent(
  sequence: sequence,
  state: state,
  position: position,
  duration: duration ?? Duration.zero,
  sessionId: session.sessionId,
  timelineMapIdentity: session.timelineMapIdentity,
);

Future<void> _seedBangumi(
  DriftBangumiLocalStore store, {
  String accountId = 'account-a',
}) async {
  await store.saveAccount(
    BangumiUserIdentity(id: accountId, username: accountId),
  );
  await store.cacheSubject(
    const BangumiSubject(
      id: '42',
      name: 'Title',
      nameCn: '作品',
      summary: '',
      eps: 1,
      totalEpisodes: 1,
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
          duration: 600,
        ),
      ],
      offset: 0,
      limit: 1,
      total: 1,
    ),
  );
  await store.applyRemoteState(_remote(accountId: accountId));
}

BangumiRemoteState _remote({
  String accountId = 'account-a',
  Set<String> watched = const <String>{},
}) {
  return BangumiRemoteState(
    accountId: accountId,
    subjectId: '42',
    status: BangumiCollectionStatus.watching,
    watchedEpisodeIds: watched,
    remoteRevision: BangumiRemoteState.fingerprint(
      subjectId: '42',
      status: BangumiCollectionStatus.watching,
      watchedEpisodeIds: watched,
    ),
  );
}

BangumiAuthSession _session(String accountId) => BangumiAuthSession(
  accountId: accountId,
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  expiresAt: DateTime.utc(2030),
);

final class _MemoryWatchHistory implements WatchHistoryRepository {
  final Map<SourceEpisodeIdentity, WatchProgress> _values =
      <SourceEpisodeIdentity, WatchProgress>{};
  int saveCount = 0;

  Future<List<WatchProgress>> get rows async => _values.values.toList();

  @override
  Future<void> save(WatchProgress progress) async {
    saveCount++;
    _values[SourceEpisodeIdentity(
          sourceId: progress.sourceId,
          lineId: progress.lineId,
          subjectId: progress.subjectId,
          episodeId: progress.episodeId,
        )] =
        progress;
  }

  @override
  Future<WatchProgress?> findById(String progressId) async => _values.values
      .where((value) => value.progressId == progressId)
      .firstOrNull;

  @override
  Future<WatchProgress?> findByIdentity(SourceEpisodeIdentity identity) async =>
      _values[identity];

  @override
  Stream<List<WatchProgress>> watchRecent({int limit = 50}) =>
      Stream.value(_values.values.take(limit).toList(growable: false));

  @override
  Future<void> remove(String progressId) async {
    _values.removeWhere((_, value) => value.progressId == progressId);
  }
}

final class _FakeBangumiClient implements BangumiClient {
  _FakeBangumiClient({
    required this.remote,
    this.remoteFailure,
    this.episodeFailure,
  });

  BangumiRemoteState remote;
  final BangumiApiException? remoteFailure;
  final BangumiApiException? episodeFailure;
  final List<(String, String, bool)> episodeMutations = [];

  @override
  Future<BangumiRemoteState> remoteState(String subjectId) async {
    final failure = remoteFailure;
    if (failure != null) throw failure;
    return remote;
  }

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {
    episodeMutations.add((subjectId, episodeId, watched));
    final failure = episodeFailure;
    if (failure != null) throw failure;
    final watchedIds = {...remote.watchedEpisodeIds};
    if (watched) {
      watchedIds.add(episodeId);
    } else {
      watchedIds.remove(episodeId);
    }
    remote = _remote(accountId: remote.accountId, watched: watchedIds);
  }

  @override
  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) async => throw UnimplementedError();

  @override
  Future<BangumiUserIdentity> currentUser() async =>
      BangumiUserIdentity(id: remote.accountId, username: remote.accountId);

  @override
  Future<BangumiCollectionPage> collections({int offset = 0, int limit = 30}) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiScheduleEntry>> calendar() => throw UnimplementedError();

  @override
  Future<BangumiSubject> subject(String id) => throw UnimplementedError();

  @override
  Future<BangumiEpisodePage> episodes(String subjectId) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiCharacter>> characters(String subjectId) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiPersonCredit>> persons(String subjectId) =>
      throw UnimplementedError();

  @override
  Future<List<BangumiSubjectRelation>> relations(String subjectId) =>
      throw UnimplementedError();
}

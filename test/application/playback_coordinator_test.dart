import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test(
    'open hands one loopback session to the player and stop releases it',
    () async {
      final session = testPlaybackSession();
      final resolver = _FakeResolver(session);
      final proxy = _FakeProxy();
      final player = _FakePlayer();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );

      final opened = await coordinator.open(_request());

      expect(opened.playbackUri?.host, '127.0.0.1');
      expect(opened.playbackUri?.hasPort, isTrue);
      expect(player.openedSessions, [opened]);
      expect(coordinator.currentSession, same(opened));

      await coordinator.pause();
      await coordinator.seek(const Duration(seconds: 12));
      await coordinator.stop();

      expect(player.pauseCount, 1);
      expect(player.seekPositions, [const Duration(seconds: 12)]);
      expect(player.closeCount, 1);
      expect(proxy.leases.single.isClosed, isTrue);
      expect(coordinator.hasActivePlayback, isFalse);

      await coordinator.close();
      expect(proxy.closeCount, 1);
    },
  );

  test('near-expiry session refreshes before the proxy sees it', () async {
    late PlaybackSession refreshed;
    final initial = testPlaybackSession(
      expiresAt: DateTime.now().toUtc().add(const Duration(seconds: 5)),
      refresh: () async => refreshed,
    );
    refreshed = testPlaybackSession(
      expiresAt: DateTime.now().toUtc().add(const Duration(minutes: 10)),
      refresh: () async => refreshed,
    );
    final proxy = _FakeProxy();
    final coordinator = PlaybackCoordinator(
      resolver: _FakeResolver(initial),
      proxy: proxy,
      player: _FakePlayer(),
    );

    final opened = await coordinator.open(
      _request(refreshLeeway: const Duration(seconds: 30)),
    );

    expect(proxy.requests.single.session.expiresAt, refreshed.expiresAt);
    expect(opened.expiresAt, refreshed.expiresAt);
    await coordinator.close();
  });

  test(
    'authorization failure refreshes and reopens the same session identity',
    () async {
      var refreshCount = 0;
      late PlaybackSession refreshed;
      final initial = testPlaybackSession(
        refresh: () async {
          refreshCount += 1;
          return refreshed;
        },
      );
      refreshed = testPlaybackSession(
        mediaUri: Uri.parse('https://media.example/video/refreshed.m3u8'),
        refresh: () async => refreshed,
      );
      final proxy = _FakeProxy();
      final player = _FakePlayer();
      final coordinator = PlaybackCoordinator(
        resolver: _FakeResolver(initial),
        proxy: proxy,
        player: player,
      );
      final eventFuture = coordinator.events.firstWhere(
        (event) => event.state == PlaybackState.failed,
      );

      await coordinator.open(_request());
      player.emit(_refreshFailure(sequence: 1));
      await eventFuture;
      await _waitUntil(() => player.openedSessions.length == 2);

      expect(refreshCount, 1);
      expect(proxy.requests.length, 2);
      expect(proxy.leases.first.isClosed, isTrue);
      expect(player.closeCount, 1);
      expect(player.openedSessions.last.sessionId, initial.sessionId);
      expect(player.openedSessions.last.episode, initial.episode);
      expect(
        player.openedSessions.last.mediaUri,
        Uri.parse('https://media.example/video/refreshed.m3u8'),
      );

      await coordinator.close();
    },
  );

  test(
    'automatic refresh limit prevents an authorization failure loop',
    () async {
      var refreshCount = 0;
      late PlaybackSession refreshed;
      final initial = testPlaybackSession(
        refresh: () async {
          refreshCount += 1;
          return refreshed;
        },
      );
      refreshed = testPlaybackSession(
        refresh: () async {
          refreshCount += 1;
          return refreshed;
        },
      );
      final player = _FakePlayer();
      final coordinator = PlaybackCoordinator(
        resolver: _FakeResolver(initial),
        proxy: _FakeProxy(),
        player: player,
      );

      await coordinator.open(_request(maxAutomaticRefreshes: 1));
      player.emit(_refreshFailure(sequence: 1));
      await _waitUntil(() => player.openedSessions.length == 2);
      player.emit(_refreshFailure(sequence: 2));
      await Future<void>.delayed(const Duration(milliseconds: 30));

      expect(refreshCount, 1);
      expect(player.openedSessions.length, 2);
      await coordinator.close();
    },
  );

  test('player open failure closes the newly-created proxy lease', () async {
    final proxy = _FakeProxy();
    final player = _FakePlayer()..openError = StateError('native open failed');
    final coordinator = PlaybackCoordinator(
      resolver: _FakeResolver(testPlaybackSession()),
      proxy: proxy,
      player: player,
    );

    await expectLater(coordinator.open(_request()), throwsStateError);

    expect(proxy.leases.single.isClosed, isTrue);
    expect(coordinator.hasActivePlayback, isFalse);
    await coordinator.close();
  });
}

PlaybackOpenRequest _request({
  Duration refreshLeeway = Duration.zero,
  int maxAutomaticRefreshes = 1,
}) {
  final episode = testEpisode();
  return PlaybackOpenRequest(
    resolution: PlaybackSessionResolutionRequest(
      episode: episode,
      pageUri: Uri.parse('https://media.example/episode/1'),
      candidate: WebMediaCandidate(
        kind: WebCandidateKind.hls,
        uri: Uri.parse('https://media.example/video/master.m3u8'),
        headers: const {},
        sourceEventSequence: 1,
      ),
      securityPolicy: testSourcePolicy(),
      adRemovalPlan: testAdRemovalPlan(episode),
    ),
    proxyBudget: testProxyBudget(),
    refreshLeeway: refreshLeeway,
    maxAutomaticRefreshes: maxAutomaticRefreshes,
  );
}

PlaybackEvent _refreshFailure({required int sequence}) => PlaybackEvent(
  sequence: sequence,
  state: PlaybackState.failed,
  failure: PlaybackFailure(
    code: 'upstream_http_403',
    kind: PlaybackFailureKind.authorization,
    stage: PlaybackErrorStage.player,
    retryable: true,
    shouldRefreshSession: true,
    httpStatus: 403,
  ),
);

Future<void> _waitUntil(bool Function() condition) async {
  for (var attempt = 0; attempt < 100; attempt += 1) {
    if (condition()) {
      return;
    }
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  fail('Condition was not met before the test timeout.');
}

final class _FakeResolver implements PlaybackSessionResolver {
  _FakeResolver(this.session);

  final PlaybackSession session;
  int resolveCount = 0;

  @override
  Future<PlaybackSession> resolve(
    PlaybackSessionResolutionRequest request,
  ) async {
    resolveCount += 1;
    return session;
  }
}

final class _FakeProxy implements PlaybackProxyService {
  final List<PlaybackProxyRequest> requests = [];
  final List<PlaybackProxyLease> leases = [];
  int closeCount = 0;

  @override
  Future<PlaybackProxyLease> expose(PlaybackProxyRequest request) async {
    requests.add(request);
    final index = leases.length + 1;
    late PlaybackProxyLease lease;
    lease = PlaybackProxyLease(
      sessionId: request.session.sessionId,
      playbackUri: Uri.parse(
        'http://127.0.0.1:${42000 + index}/v1/session/token-$index/master',
      ),
      close: () async {},
    );
    leases.add(lease);
    return lease;
  }

  @override
  Future<void> close() async {
    closeCount += 1;
    for (final lease in leases) {
      await lease.close();
    }
  }
}

final class _FakePlayer implements PlayerBackend {
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final List<PlaybackSession> openedSessions = [];
  final List<Duration> seekPositions = [];
  int pauseCount = 0;
  int closeCount = 0;
  Object? openError;

  @override
  String get backendId => 'fake-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<void> open(PlaybackSession session) async {
    final error = openError;
    if (error != null) {
      throw error;
    }
    openedSessions.add(session);
  }

  @override
  Future<void> pause() async {
    pauseCount += 1;
  }

  @override
  Future<void> seek(Duration position) async {
    seekPositions.add(position);
  }

  @override
  Future<void> close() async {
    closeCount += 1;
  }

  void emit(PlaybackEvent event) => _events.add(event);
}

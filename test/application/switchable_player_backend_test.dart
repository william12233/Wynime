import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/playback/switchable_player_backend.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_handoff.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend_capability.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test('opens fallback when the preferred backend probe is unavailable', () async {
    final primary = _FakeBackend(
      backendId: 'windows-mpv',
      kind: PlayerBackendKind.mpv,
      availability: PlayerBackendAvailability.unavailable,
    );
    final fallback = _FakeBackend(
      backendId: 'android-media3',
      kind: PlayerBackendKind.media3,
    );
    final backend = SwitchablePlayerBackend(
      primary: primary,
      fallback: fallback,
    );
    addTearDown(backend.close);
    final session = _loopbackSession();

    await backend.open(session);

    expect(primary.opened, isEmpty);
    expect(fallback.opened, [same(session)]);
    expect(backend.activeBackend, same(fallback));
  });

  test('decoder failure hands the same session and position to fallback', () async {
    final primary = _FakeBackend(
      backendId: 'windows-mpv',
      kind: PlayerBackendKind.mpv,
    );
    final fallback = _FakeBackend(
      backendId: 'android-media3',
      kind: PlayerBackendKind.media3,
    );
    final backend = SwitchablePlayerBackend(
      primary: primary,
      fallback: fallback,
    );
    addTearDown(backend.close);
    final session = _loopbackSession();
    await backend.open(session);
    primary.emit(
      PlaybackState.paused,
      position: const Duration(seconds: 42),
    );

    primary.emitFailure(
      kind: PlaybackFailureKind.decoder,
      position: const Duration(seconds: 42),
    );
    await _eventually(() => fallback.opened.isNotEmpty);

    expect(fallback.opened.single, same(session));
    expect(fallback.seeks, [const Duration(seconds: 42)]);
    expect(fallback.pauseCalls, 1);
    expect(primary.closeCalls, 1);
    expect(backend.lastHandoff?.sessionId, session.sessionId);
    expect(
      backend.lastHandoff?.timelineMapIdentity,
      session.timelineMapIdentity,
    );
    expect(backend.lastHandoff?.intent, PlaybackIntent.paused);
  });

  test('network failure is forwarded without engine fallback', () async {
    final primary = _FakeBackend(
      backendId: 'windows-mpv',
      kind: PlayerBackendKind.mpv,
    );
    final fallback = _FakeBackend(
      backendId: 'android-media3',
      kind: PlayerBackendKind.media3,
    );
    final backend = SwitchablePlayerBackend(
      primary: primary,
      fallback: fallback,
    );
    addTearDown(backend.close);
    final events = <PlaybackEvent>[];
    final subscription = backend.events.listen(events.add);
    addTearDown(subscription.cancel);
    await backend.open(_loopbackSession());

    primary.emitFailure(kind: PlaybackFailureKind.network);
    await _eventually(() => events.isNotEmpty);

    expect(events.last.state, PlaybackState.failed);
    expect(events.last.failure?.kind, PlaybackFailureKind.network);
    expect(fallback.opened, isEmpty);
  });

  test('stale primary events are ignored after fallback is active', () async {
    final primary = _FakeBackend(
      backendId: 'windows-mpv',
      kind: PlayerBackendKind.mpv,
    );
    final fallback = _FakeBackend(
      backendId: 'android-media3',
      kind: PlayerBackendKind.media3,
    );
    final backend = SwitchablePlayerBackend(
      primary: primary,
      fallback: fallback,
    );
    addTearDown(backend.close);
    final events = <PlaybackEvent>[];
    final subscription = backend.events.listen(events.add);
    addTearDown(subscription.cancel);
    await backend.open(_loopbackSession());
    primary.emitFailure(kind: PlaybackFailureKind.renderer);
    await _eventually(() => fallback.opened.isNotEmpty);
    final count = events.length;

    primary.emit(
      PlaybackState.playing,
      position: const Duration(hours: 1),
    );
    await Future<void>.delayed(Duration.zero);

    expect(events, hasLength(count));
  });
}

PlaybackSession _loopbackSession() => testPlaybackSession(
  playbackUri: Uri.parse('http://127.0.0.1:41000/session/master.m3u8'),
);

Future<void> _eventually(bool Function() predicate) async {
  for (var attempt = 0; attempt < 50; attempt += 1) {
    if (predicate()) {
      return;
    }
    await Future<void>.delayed(const Duration(milliseconds: 1));
  }
  fail('Condition was not satisfied.');
}

final class _FakeBackend
    implements PlayerBackend, PlayerBackendCapabilitySource {
  _FakeBackend({
    required this.backendId,
    required this.kind,
    this.availability = PlayerBackendAvailability.available,
  });

  @override
  final String backendId;

  @override
  final PlayerBackendKind kind;

  PlayerBackendAvailability availability;
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final List<PlaybackSession> opened = [];
  final List<Duration> seeks = [];
  int pauseCalls = 0;
  int closeCalls = 0;
  int _sequence = 0;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendCapability> probe() async => PlayerBackendCapability(
    backendId: backendId,
    availability: availability,
    code: availability == PlayerBackendAvailability.available
        ? 'available'
        : 'runtime_missing',
  );

  @override
  Future<void> open(PlaybackSession session) async {
    opened.add(session);
  }

  @override
  Future<void> pause() async {
    pauseCalls += 1;
  }

  @override
  Future<void> seek(Duration position) async {
    seeks.add(position);
  }

  @override
  Future<void> close() async {
    closeCalls += 1;
  }

  void emit(
    PlaybackState state, {
    Duration position = Duration.zero,
  }) {
    _events.add(
      PlaybackEvent(
        sequence: _sequence++,
        state: state,
        position: position,
      ),
    );
  }

  void emitFailure({
    required PlaybackFailureKind kind,
    Duration position = Duration.zero,
  }) {
    _events.add(
      PlaybackEvent(
        sequence: _sequence++,
        state: PlaybackState.failed,
        position: position,
        failure: PlaybackFailure(
          code: '${kind.name}_failure',
          kind: kind,
          stage: PlaybackErrorStage.player,
          retryable: false,
          shouldRefreshSession: false,
        ),
      ),
    );
  }
}

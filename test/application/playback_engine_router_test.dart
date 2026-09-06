import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/playback/playback_engine_router.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

import '../helpers/fake_player_backend.dart';
import '../helpers/playback_test_support.dart';

void main() {
  test('router selects the first available backend', () async {
    final media3 = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
      availabilityStatus: PlayerBackendAvailabilityStatus.unavailable,
    );
    final mpv = FakePlayerBackend(
      kind: PlayerBackendKind.mpv,
      backendId: 'mpv',
    );
    final router = PlaybackEngineRouter(
      backends: {PlayerBackendKind.media3: media3, PlayerBackendKind.mpv: mpv},
      preference: const [PlayerBackendKind.media3, PlayerBackendKind.mpv],
    );
    final session = _session();

    await router.open(session);

    expect(router.activeKind, PlayerBackendKind.mpv);
    expect(media3.openedSessions, isEmpty);
    expect(mpv.openedSessions, [same(session)]);
    expect(mpv.volumes, [1]);
    expect(mpv.rates, [1]);
    expect(mpv.playCount, 1);
  });

  test(
    'decoder fallback preserves one session, URI, state, and track choices',
    () async {
      final media3 = FakePlayerBackend(
        kind: PlayerBackendKind.media3,
        backendId: 'media3',
      );
      final mpv = FakePlayerBackend(
        kind: PlayerBackendKind.mpv,
        backendId: 'mpv',
      );
      final router = PlaybackEngineRouter(
        backends: {
          PlayerBackendKind.media3: media3,
          PlayerBackendKind.mpv: mpv,
        },
        preference: const [PlayerBackendKind.media3, PlayerBackendKind.mpv],
      );
      final session = _session(
        audioTracks: [MediaTrack(id: 'audio-1', label: 'Japanese')],
        subtitles: [MediaTrack(id: 'subtitle-1', label: '繁體中文')],
      );
      await router.open(session);
      await router.seek(const Duration(seconds: 45));
      await router.setVolume(0.4);
      await router.setRate(1.5);
      await router.selectAudioTrack('audio-1');
      await router.selectSubtitleTrack('subtitle-1');

      media3.emit(
        _failure(
          sequence: 1,
          kind: PlaybackFailureKind.decoder,
          code: 'decoder_failed',
          session: session,
          position: const Duration(seconds: 45),
        ),
      );
      await _waitUntil(() => mpv.openedSessions.isNotEmpty);

      expect(router.activeKind, PlayerBackendKind.mpv);
      expect(mpv.openedSessions.single, same(session));
      expect(mpv.openedSessions.single.playbackUri, session.playbackUri);
      expect(mpv.seeks, [const Duration(seconds: 45)]);
      expect(mpv.volumes, [0.4]);
      expect(mpv.rates, [1.5]);
      expect(mpv.audioTracks, ['audio-1']);
      expect(mpv.subtitleTracks, ['subtitle-1']);
      expect(mpv.playCount, 1);
      expect(media3.closeCount, 1);
      expect(router.state.timelineMapIdentity, session.timelineMapIdentity);
    },
  );

  test('authorization failure never changes playback engine', () async {
    final media3 = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
    );
    final mpv = FakePlayerBackend(
      kind: PlayerBackendKind.mpv,
      backendId: 'mpv',
    );
    final router = PlaybackEngineRouter(
      backends: {PlayerBackendKind.media3: media3, PlayerBackendKind.mpv: mpv},
      preference: const [PlayerBackendKind.media3, PlayerBackendKind.mpv],
    );
    final session = _session();
    await router.open(session);
    final failureFuture = router.events.firstWhere(
      (event) => event.state == PlaybackState.failed,
    );

    media3.emit(
      _failure(
        sequence: 1,
        kind: PlaybackFailureKind.authorization,
        code: 'http_authorization',
        session: session,
      ),
    );

    final event = await failureFuture;
    expect(event.failure?.kind, PlaybackFailureKind.authorization);
    expect(router.activeKind, PlayerBackendKind.media3);
    expect(mpv.openedSessions, isEmpty);
  });

  test('open network failure never changes playback engine', () async {
    final media3 = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
      openError: PlaybackOperationException(
        PlaybackFailure(
          code: 'network_failure',
          kind: PlaybackFailureKind.network,
          stage: PlaybackErrorStage.player,
          retryable: true,
          shouldRefreshSession: false,
        ),
      ),
    );
    final mpv = FakePlayerBackend(
      kind: PlayerBackendKind.mpv,
      backendId: 'mpv',
    );
    final router = PlaybackEngineRouter(
      backends: {PlayerBackendKind.media3: media3, PlayerBackendKind.mpv: mpv},
      preference: const [PlayerBackendKind.media3, PlayerBackendKind.mpv],
    );
    final failureFuture = router.events.firstWhere(
      (event) => event.state == PlaybackState.failed,
    );

    await expectLater(
      router.open(_session()),
      throwsA(isA<PlaybackOperationException>()),
    );

    final failure = await failureFuture;
    expect(failure.failure?.kind, PlaybackFailureKind.network);
    expect(mpv.openedSessions, isEmpty);
    expect(router.activeKind, isNull);
  });

  test('initial fallback is limited to one backend transition', () async {
    final media3 = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
      openError: PlaybackOperationException(
        PlaybackFailure(
          code: 'decoder_failure',
          kind: PlaybackFailureKind.decoder,
          stage: PlaybackErrorStage.player,
          retryable: false,
          shouldRefreshSession: false,
        ),
      ),
    );
    final mpv = FakePlayerBackend(
      kind: PlayerBackendKind.mpv,
      backendId: 'mpv',
      openError: PlaybackOperationException(
        PlaybackFailure(
          code: 'renderer_failure',
          kind: PlaybackFailureKind.renderer,
          stage: PlaybackErrorStage.player,
          retryable: false,
          shouldRefreshSession: false,
        ),
      ),
    );
    final webView = FakePlayerBackend(
      kind: PlayerBackendKind.webView,
      backendId: 'webview',
    );
    final router = PlaybackEngineRouter(
      backends: {
        PlayerBackendKind.media3: media3,
        PlayerBackendKind.mpv: mpv,
        PlayerBackendKind.webView: webView,
      },
      preference: const [
        PlayerBackendKind.media3,
        PlayerBackendKind.mpv,
        PlayerBackendKind.webView,
      ],
    );

    await expectLater(
      router.open(_session()),
      throwsA(isA<PlaybackOperationException>()),
    );

    expect(webView.openedSessions, isEmpty);
    expect(media3.closeCount, 1);
    expect(mpv.closeCount, 1);
    expect(router.activeKind, isNull);
  });

  test('automatic fallback occurs at most once per operation', () async {
    final media3 = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
    );
    final mpv = FakePlayerBackend(
      kind: PlayerBackendKind.mpv,
      backendId: 'mpv',
    );
    final webView = FakePlayerBackend(
      kind: PlayerBackendKind.webView,
      backendId: 'webview',
    );
    final router = PlaybackEngineRouter(
      backends: {
        PlayerBackendKind.media3: media3,
        PlayerBackendKind.mpv: mpv,
        PlayerBackendKind.webView: webView,
      },
      preference: const [
        PlayerBackendKind.media3,
        PlayerBackendKind.mpv,
        PlayerBackendKind.webView,
      ],
    );
    final session = _session();
    await router.open(session);
    media3.emit(
      _failure(
        sequence: 1,
        kind: PlaybackFailureKind.decoder,
        code: 'decoder_failed',
        session: session,
      ),
    );
    await _waitUntil(() => mpv.openedSessions.isNotEmpty);
    final failureFuture = router.events.firstWhere(
      (event) => event.state == PlaybackState.failed,
    );

    mpv.emit(
      _failure(
        sequence: 2,
        kind: PlaybackFailureKind.renderer,
        code: 'renderer_failed',
        session: session,
      ),
    );

    final event = await failureFuture;
    expect(event.failure?.kind, PlaybackFailureKind.renderer);
    expect(router.activeKind, PlayerBackendKind.mpv);
    expect(webView.openedSessions, isEmpty);
  });

  test(
    'a failure emitted during open is deferred until handoff completes',
    () async {
      final session = _session();
      final mpv = FakePlayerBackend(
        kind: PlayerBackendKind.mpv,
        backendId: 'mpv',
        emitDuringOpen: _failure(
          sequence: 0,
          kind: PlaybackFailureKind.decoder,
          code: 'decoder_failed',
          session: session,
        ),
      );
      final webView = FakePlayerBackend(
        kind: PlayerBackendKind.webView,
        backendId: 'webview',
      );
      final router = PlaybackEngineRouter(
        backends: {
          PlayerBackendKind.mpv: mpv,
          PlayerBackendKind.webView: webView,
        },
        preference: const [PlayerBackendKind.mpv, PlayerBackendKind.webView],
      );

      await router.open(session);
      await _waitUntil(() => webView.openedSessions.isNotEmpty);

      expect(mpv.closeCount, 1);
      expect(webView.openedSessions.single, same(session));
      expect(router.activeKind, PlayerBackendKind.webView);
    },
  );

  test('stale backend events are ignored after a switch', () async {
    final media3 = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
    );
    final mpv = FakePlayerBackend(
      kind: PlayerBackendKind.mpv,
      backendId: 'mpv',
    );
    final router = PlaybackEngineRouter(
      backends: {PlayerBackendKind.media3: media3, PlayerBackendKind.mpv: mpv},
      preference: const [PlayerBackendKind.media3, PlayerBackendKind.mpv],
    );
    final session = _session();
    await router.open(session);
    media3.emit(
      _failure(
        sequence: 1,
        kind: PlaybackFailureKind.decoder,
        code: 'decoder_failed',
        session: session,
      ),
    );
    await _waitUntil(() => mpv.openedSessions.isNotEmpty);
    final before = router.state.position;

    media3.emit(
      PlaybackEvent(
        sequence: 2,
        state: PlaybackState.playing,
        position: const Duration(hours: 3),
        timelineMapIdentity: session.timelineMapIdentity,
      ),
    );
    await Future<void>.delayed(const Duration(milliseconds: 20));

    expect(router.state.position, before);
    expect(router.activeKind, PlayerBackendKind.mpv);
  });

  test('timeline identity mismatch fails closed', () async {
    final media3 = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
    );
    final router = PlaybackEngineRouter(
      backends: {PlayerBackendKind.media3: media3},
      preference: const [PlayerBackendKind.media3],
    );
    final session = _session();
    await router.open(session);
    final failureFuture = router.events.firstWhere(
      (event) => event.failure?.code == 'timeline_identity_mismatch',
    );

    media3.emit(
      PlaybackEvent(
        sequence: 1,
        state: PlaybackState.playing,
        timelineMapIdentity: 'wrong-map',
      ),
    );

    final event = await failureFuture;
    expect(event.failure?.kind, PlaybackFailureKind.manifest);
    await _waitUntil(() => media3.closeCount == 1);
    expect(router.activeKind, isNull);
  });

  test(
    'router rejects foreign track events before shared state mutation',
    () async {
      final backend = FakePlayerBackend(
        kind: PlayerBackendKind.media3,
        backendId: 'media3',
      );
      final router = PlaybackEngineRouter(
        backends: {PlayerBackendKind.media3: backend},
        preference: const [PlayerBackendKind.media3],
      );
      final session = _session(
        audioTracks: [MediaTrack(id: 'audio-1', label: 'Japanese')],
        subtitles: [MediaTrack(id: 'subtitle-1', label: '繁體中文')],
      );
      await router.open(session);
      final failureFuture = router.events.firstWhere(
        (event) => event.failure?.code == 'track_identity_mismatch',
      );

      backend.emit(
        PlaybackEvent(
          sequence: 1,
          state: PlaybackState.playing,
          audioTrackId: 'native-audio-id',
          subtitleTrackId: 'native-subtitle-id',
          timelineMapIdentity: session.timelineMapIdentity,
        ),
      );

      final failure = await failureFuture;
      expect(failure.failure?.kind, PlaybackFailureKind.unknown);
      expect(router.state.audioTrack.id, isNull);
      expect(router.state.subtitleTrack.id, isNull);
      await _waitUntil(() => backend.closeCount == 1);
      expect(router.activeKind, isNull);
    },
  );

  test('router rejects external URI tracks for selection and events', () async {
    final backend = FakePlayerBackend(
      kind: PlayerBackendKind.media3,
      backendId: 'media3',
    );
    final router = PlaybackEngineRouter(
      backends: {PlayerBackendKind.media3: backend},
      preference: const [PlayerBackendKind.media3],
    );
    final session = _session(
      audioTracks: [
        MediaTrack(
          id: 'external-audio',
          label: 'External audio',
          uri: Uri.parse('https://media.example/audio.m4a'),
        ),
      ],
      subtitles: [
        MediaTrack(
          id: 'external-subtitle',
          label: 'External subtitle',
          uri: Uri.parse('https://media.example/subtitle.vtt'),
        ),
      ],
    );
    await router.open(session);

    await expectLater(
      router.selectAudioTrack('external-audio'),
      throwsStateError,
    );
    await expectLater(
      router.selectSubtitleTrack('external-subtitle'),
      throwsStateError,
    );
    final failureFuture = router.events.firstWhere(
      (event) => event.failure?.code == 'track_identity_mismatch',
    );

    backend.emit(
      PlaybackEvent(
        sequence: 1,
        state: PlaybackState.playing,
        audioTrackId: 'external-audio',
        timelineMapIdentity: session.timelineMapIdentity,
      ),
    );

    final failure = await failureFuture;
    expect(failure.failure?.code, 'track_identity_mismatch');
    await _waitUntil(() => backend.closeCount == 1);
  });
}

PlaybackSession _session({
  Iterable<MediaTrack> audioTracks = const [],
  Iterable<MediaTrack> subtitles = const [],
}) => testPlaybackSession(
  playbackUri: Uri.parse(
    'http://127.0.0.1:42000/v1/session/token/resource/master',
  ),
  audioTracks: audioTracks,
  subtitles: subtitles,
);

PlaybackEvent _failure({
  required int sequence,
  required PlaybackFailureKind kind,
  required String code,
  required PlaybackSession session,
  Duration position = Duration.zero,
}) => PlaybackEvent(
  sequence: sequence,
  state: PlaybackState.failed,
  position: position,
  failure: PlaybackFailure(
    code: code,
    kind: kind,
    stage: PlaybackErrorStage.player,
    retryable: false,
    shouldRefreshSession: false,
  ),
  timelineMapIdentity: session.timelineMapIdentity,
);

Future<void> _waitUntil(bool Function() condition) async {
  for (var attempt = 0; attempt < 200; attempt += 1) {
    if (condition()) {
      return;
    }
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  fail('Condition was not met before the test timeout.');
}

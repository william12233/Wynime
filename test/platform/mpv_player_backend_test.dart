import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/platform/playback/media_kit_facade.dart';
import 'package:wynime/src/platform/playback/mpv_player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test('mpv probe exposes native runtime availability', () async {
    final facade = _FakeMediaKitFacade();
    final backend = MpvPlayerBackend(facade: facade);

    final availability = await backend.probe();

    expect(availability.status, PlayerBackendAvailabilityStatus.available);
    expect(facade.probeCount, 1);
  });

  test('mpv accepts only a numeric-loopback capability URI', () async {
    final backend = MpvPlayerBackend(facade: _FakeMediaKitFacade());

    await expectLater(backend.open(testPlaybackSession()), throwsArgumentError);
    await expectLater(
      () => backend.open(
        testPlaybackSession(
          playbackUri: Uri.parse(
            'http://localhost:42000/v1/session/token/resource/master',
          ),
        ),
      ),
      throwsArgumentError,
    );
    await expectLater(
      () => backend.open(
        testPlaybackSession(
          playbackUri: Uri.parse(
            'http://127.0.0.1:42000/v1/session/token/resource/master?secret=x',
          ),
        ),
      ),
      throwsArgumentError,
    );
  });

  test('mpv opens the same capability and exposes all controls', () async {
    final player = _FakeMediaKitPlayer();
    final facade = _FakeMediaKitFacade(player: player);
    final backend = MpvPlayerBackend(facade: facade);
    final session = _loopbackSession(
      audioTracks: [MediaTrack(id: 'audio-1', label: 'Japanese')],
      subtitles: [MediaTrack(id: 'subtitle-1', label: '繁體中文')],
    );
    final opening = backend.events.first;

    await backend.open(session);
    expect((await opening).state, PlaybackState.opening);
    expect(player.openedUris, [session.playbackUri]);

    await backend.play();
    await backend.pause();
    await backend.seek(const Duration(seconds: 42));
    await backend.setVolume(0.4);
    await backend.setRate(1.5);
    await backend.selectAudioTrack('audio-1');
    await backend.selectSubtitleTrack('subtitle-1');
    await backend.selectSubtitleTrack(null);

    expect(player.playCount, 1);
    expect(player.pauseCount, 1);
    expect(player.seeks, [const Duration(seconds: 42)]);
    expect(player.volumes, [0.4]);
    expect(player.rates, [1.5]);
    expect(player.audioTracks, ['audio-1']);
    expect(player.subtitleTracks, ['subtitle-1', null]);

    await expectLater(backend.selectAudioTrack('missing'), throwsStateError);
    await backend.close();
    expect(player.disposeCount, 1);
  });

  test(
    'mpv rejects external track identity instead of loading its URI',
    () async {
      final player = _FakeMediaKitPlayer();
      final backend = MpvPlayerBackend(
        facade: _FakeMediaKitFacade(player: player),
      );
      await backend.open(
        _loopbackSession(
          audioTracks: [
            MediaTrack(
              id: 'external-audio',
              label: 'External',
              uri: Uri.parse('https://media.example/audio.m4a'),
            ),
          ],
        ),
      );

      await expectLater(
        backend.selectAudioTrack('external-audio'),
        throwsStateError,
      );
      expect(player.audioTracks, isEmpty);
    },
  );

  test('mpv maps facade state without exposing raw errors', () async {
    final player = _FakeMediaKitPlayer();
    final backend = MpvPlayerBackend(
      facade: _FakeMediaKitFacade(player: player),
    );
    final session = _loopbackSession();
    await backend.open(session);

    final eventsFuture = backend.events.take(4).toList();
    player.emit(const MediaKitFacadeEvent.playing(true));
    player.emit(const MediaKitFacadeEvent.position(Duration(seconds: 12)));
    player.emit(const MediaKitFacadeEvent.buffer(Duration(seconds: 30)));
    player.emit(
      const MediaKitFacadeEvent.failure(
        'decoder failed at https://secret.example/video?token=private',
      ),
    );

    final events = await eventsFuture;
    expect(events[0].state, PlaybackState.playing);
    expect(events[1].position, const Duration(seconds: 12));
    expect(events[2].bufferedPosition, const Duration(seconds: 30));
    expect(events[3].failure?.kind, PlaybackFailureKind.decoder);
    expect(events[3].failure.toString(), isNot(contains('secret.example')));
    expect(events[3].failure.toString(), isNot(contains('private')));
    expect(
      events.every(
        (event) => event.timelineMapIdentity == session.timelineMapIdentity,
      ),
      isTrue,
    );
  });

  test('mpv fails closed on a non-session track event', () async {
    final player = _FakeMediaKitPlayer();
    final backend = MpvPlayerBackend(
      facade: _FakeMediaKitFacade(player: player),
    );
    final session = _loopbackSession(
      audioTracks: [MediaTrack(id: 'audio-1', label: 'Japanese')],
    );
    await backend.open(session);
    final failureFuture = backend.events.firstWhere(
      (event) => event.failure?.code == 'track_identity_mismatch',
    );

    player.emit(
      const MediaKitFacadeEvent.track(
        audioTrackId: 'native-only-id',
        subtitleTrackId: null,
      ),
    );

    final failure = await failureFuture;
    expect(failure.failure?.kind, PlaybackFailureKind.unknown);
    await Future<void>.delayed(Duration.zero);
    expect(backend.activePlayer, isNull);
  });

  test('mpv facade creation failure leaves no active player', () async {
    final facade = _FakeMediaKitFacade()
      ..createError = StateError('native bootstrap failed');
    final backend = MpvPlayerBackend(facade: facade);

    await expectLater(backend.open(_loopbackSession()), throwsStateError);

    expect(backend.activePlayer, isNull);
  });
}

PlaybackSession _loopbackSession({
  Iterable<MediaTrack> audioTracks = const [],
  Iterable<MediaTrack> subtitles = const [],
}) => testPlaybackSession(
  playbackUri: Uri.parse(
    'http://127.0.0.1:42000/v1/session/token/resource/master',
  ),
  audioTracks: audioTracks,
  subtitles: subtitles,
);

final class _FakeMediaKitFacade implements MediaKitFacade {
  _FakeMediaKitFacade({this.player});

  _FakeMediaKitPlayer? player;
  bool available = true;
  int probeCount = 0;
  Object? createError;

  @override
  Future<bool> probe() async {
    probeCount += 1;
    return available;
  }

  @override
  Future<MediaKitFacadePlayer> createPlayer() async {
    final error = createError;
    if (error != null) {
      throw error;
    }
    return player ??= _FakeMediaKitPlayer();
  }
}

final class _FakeMediaKitPlayer implements MediaKitFacadePlayer {
  final StreamController<MediaKitFacadeEvent> _events =
      StreamController<MediaKitFacadeEvent>.broadcast();
  final List<Uri> openedUris = [];
  final List<Duration> startPositions = [];
  final List<Duration> seeks = [];
  final List<double> volumes = [];
  final List<double> rates = [];
  final List<String?> audioTracks = [];
  final List<String?> subtitleTracks = [];
  int playCount = 0;
  int pauseCount = 0;
  int disposeCount = 0;

  @override
  Stream<MediaKitFacadeEvent> get events => _events.stream;

  @override
  Object get videoControllerHandle => this;

  @override
  Future<void> open(Uri uri, {Duration startPosition = Duration.zero}) async {
    openedUris.add(uri);
    startPositions.add(startPosition);
  }

  @override
  Future<void> play() async {
    playCount += 1;
  }

  @override
  Future<void> pause() async {
    pauseCount += 1;
  }

  @override
  Future<void> seek(Duration position) async {
    seeks.add(position);
  }

  @override
  Future<void> setVolume(double volume) async {
    volumes.add(volume);
  }

  @override
  Future<void> setRate(double rate) async {
    rates.add(rate);
  }

  @override
  Future<void> selectAudioTrack(MediaTrack? track) async {
    audioTracks.add(track?.id);
  }

  @override
  Future<void> selectSubtitleTrack(MediaTrack? track) async {
    subtitleTracks.add(track?.id);
  }

  @override
  Future<void> dispose() async {
    disposeCount += 1;
  }

  void emit(MediaKitFacadeEvent event) => _events.add(event);
}

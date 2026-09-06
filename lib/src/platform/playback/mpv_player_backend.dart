import 'dart:async';

import 'package:wynime/src/application/playback/playback_failure_classifier.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/player_backend.dart';
import 'package:wynime/src/platform/playback/media_kit_facade.dart';

final class MpvPlayerBackend implements PlayerBackend {
  MpvPlayerBackend({
    this._facade = const ProductionMediaKitFacade(),
    this._classifier = const PlaybackFailureClassifier(),
    this.backendId = 'mpv-libmpv',
  });

  final MediaKitFacade _facade;
  final PlaybackFailureClassifier _classifier;
  final PlaybackErrorBoundary _errorBoundary = const PlaybackErrorBoundary();

  @override
  final String backendId;

  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  MediaKitFacadePlayer? _player;
  StreamSubscription<MediaKitFacadeEvent>? _subscription;
  PlaybackSession? _session;
  int _sequence = 0;
  bool _playing = false;
  Duration _position = Duration.zero;
  Duration _bufferedPosition = Duration.zero;
  double _volume = 1;
  double _rate = 1;
  String? _audioTrackId;
  String? _subtitleTrackId;

  @override
  PlayerBackendKind get kind => PlayerBackendKind.mpv;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  MediaKitFacadePlayer? get activePlayer => _player;

  @override
  Future<PlayerBackendAvailability> probe() async {
    try {
      return await _facade.probe()
          ? PlayerBackendAvailability.available(backendId)
          : PlayerBackendAvailability.unavailable(
              backendId,
              reasonCode: 'mpv_runtime_unavailable',
            );
    } on Object {
      return PlayerBackendAvailability.probeFailed(
        backendId,
        reasonCode: 'mpv_probe_failed',
      );
    }
  }

  @override
  Future<void> open(PlaybackSession session) async {
    final uri = _validatedCapabilityUri(session);
    await close();
    final availability = await probe();
    if (!availability.isAvailable) {
      throw UnsupportedError('libmpv runtime is unavailable.');
    }

    _session = session;
    _sequence = 0;
    _playing = false;
    _position = Duration.zero;
    _bufferedPosition = Duration.zero;
    _volume = 1;
    _rate = 1;
    _audioTrackId = null;
    _subtitleTrackId = null;
    _emit(PlaybackState.opening);

    try {
      final player = await _facade.createPlayer();
      _player = player;
      _subscription = player.events.listen(
        _onFacadeEvent,
        onError: (Object _) {
          _emitFailure(
            _classifier.classifyMediaKitError('event_stream_failed'),
          );
        },
      );
      await player.open(uri);
    } on Object catch (error) {
      final stableError = _errorBoundary.exceptionFrom(
        error,
        stage: PlaybackErrorStage.player,
      );
      _emitFailure(stableError.failure);
      try {
        await _releasePlayer();
      } finally {
        _session = null;
      }
      throw stableError;
    }
  }

  @override
  Future<void> play() async {
    await _runPlayerOperation(_requirePlayer().play);
    _playing = true;
  }

  @override
  Future<void> pause() async {
    await _runPlayerOperation(_requirePlayer().pause);
    _playing = false;
  }

  @override
  Future<void> seek(Duration position) async {
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
    await _runPlayerOperation(() => _requirePlayer().seek(position));
    _position = position;
  }

  @override
  Future<void> setVolume(double volume) async {
    if (!volume.isFinite || volume < 0 || volume > 1) {
      throw ArgumentError.value(volume, 'volume', 'Must be between 0 and 1.');
    }
    await _runPlayerOperation(() => _requirePlayer().setVolume(volume));
    _volume = volume;
  }

  @override
  Future<void> setRate(double rate) async {
    if (!rate.isFinite || rate < 0.25 || rate > 4) {
      throw ArgumentError.value(rate, 'rate', 'Must be between 0.25 and 4.');
    }
    await _runPlayerOperation(() => _requirePlayer().setRate(rate));
    _rate = rate;
  }

  @override
  Future<void> selectAudioTrack(String? trackId) async {
    final session = _requireSession();
    final track = _trackById(session.audioTracks, trackId, 'audio');
    await _runPlayerOperation(() => _requirePlayer().selectAudioTrack(track));
    _audioTrackId = trackId;
  }

  @override
  Future<void> selectSubtitleTrack(String? trackId) async {
    final session = _requireSession();
    final track = _trackById(session.subtitles, trackId, 'subtitle');
    await _runPlayerOperation(
      () => _requirePlayer().selectSubtitleTrack(track),
    );
    _subtitleTrackId = trackId;
  }

  @override
  Future<void> close() async {
    final identity = _session?.timelineMapIdentity;
    final hadPlayer = _player != null || _session != null;
    await _releasePlayer();
    _session = null;
    _playing = false;
    if (hadPlayer && identity != null) {
      _events.add(
        PlaybackEvent(
          sequence: _sequence++,
          state: PlaybackState.closed,
          position: _position,
          bufferedPosition: _bufferedPosition,
          volume: _volume,
          rate: _rate,
          audioTrackId: _audioTrackId,
          subtitleTrackId: _subtitleTrackId,
          timelineMapIdentity: identity,
        ),
      );
    }
  }

  void _onFacadeEvent(MediaKitFacadeEvent event) {
    final session = _session;
    if (session == null || _player == null) {
      return;
    }
    switch (event.type) {
      case MediaKitFacadeEventType.playing:
        _playing = event.flag ?? false;
        _emit(_playing ? PlaybackState.playing : PlaybackState.paused);
      case MediaKitFacadeEventType.completed:
        if (event.flag ?? false) {
          _playing = false;
          _emit(PlaybackState.ended);
        }
      case MediaKitFacadeEventType.position:
        _position = event.position ?? _position;
        _emit(_playing ? PlaybackState.playing : PlaybackState.ready);
      case MediaKitFacadeEventType.buffer:
        _bufferedPosition = event.position ?? _bufferedPosition;
        _emit(_playing ? PlaybackState.playing : PlaybackState.buffering);
      case MediaKitFacadeEventType.volume:
        final value = event.value;
        if (value != null && value.isFinite && value >= 0 && value <= 1) {
          _volume = value;
          _emit(_playing ? PlaybackState.playing : PlaybackState.ready);
        }
      case MediaKitFacadeEventType.rate:
        final value = event.value;
        if (value != null && value.isFinite && value >= 0.25 && value <= 4) {
          _rate = value;
          _emit(_playing ? PlaybackState.playing : PlaybackState.ready);
        }
      case MediaKitFacadeEventType.track:
        if (!_isAuthoritativeTrackId(session.audioTracks, event.audioTrackId) ||
            !_isAuthoritativeTrackId(
              session.subtitles,
              event.subtitleTrackId,
            )) {
          _playing = false;
          _emitFailure(
            PlaybackFailure(
              code: 'track_identity_mismatch',
              kind: PlaybackFailureKind.unknown,
              stage: PlaybackErrorStage.player,
              retryable: false,
              shouldRefreshSession: false,
            ),
          );
          unawaited(_releasePlayer());
          return;
        }
        _audioTrackId = event.audioTrackId;
        _subtitleTrackId = event.subtitleTrackId;
        _emit(_playing ? PlaybackState.playing : PlaybackState.ready);
      case MediaKitFacadeEventType.failure:
        _playing = false;
        _emitFailure(
          _classifier.classifyMediaKitError(event.rawError ?? 'unknown'),
        );
    }
  }

  void _emit(PlaybackState state) {
    final session = _session;
    if (session == null || _events.isClosed) {
      return;
    }
    _events.add(
      PlaybackEvent(
        sequence: _sequence++,
        state: state,
        position: _position,
        bufferedPosition: _bufferedPosition,
        volume: _volume,
        rate: _rate,
        audioTrackId: _audioTrackId,
        subtitleTrackId: _subtitleTrackId,
        timelineMapIdentity: session.timelineMapIdentity,
      ),
    );
  }

  void _emitFailure(PlaybackFailure failure) {
    final session = _session;
    if (session == null || _events.isClosed) {
      return;
    }
    _events.add(
      PlaybackEvent(
        sequence: _sequence++,
        state: PlaybackState.failed,
        position: _position,
        bufferedPosition: _bufferedPosition,
        failure: failure,
        volume: _volume,
        rate: _rate,
        audioTrackId: _audioTrackId,
        subtitleTrackId: _subtitleTrackId,
        timelineMapIdentity: session.timelineMapIdentity,
      ),
    );
  }

  Future<void> _releasePlayer() async {
    final subscription = _subscription;
    final player = _player;
    _subscription = null;
    _player = null;
    try {
      await subscription?.cancel();
      await player?.dispose();
    } on Object catch (error) {
      throw _errorBoundary.exceptionFrom(
        error,
        stage: PlaybackErrorStage.player,
      );
    }
  }

  Future<void> _runPlayerOperation(Future<void> Function() operation) async {
    try {
      await operation();
    } on Object catch (error) {
      throw _errorBoundary.exceptionFrom(
        error,
        stage: PlaybackErrorStage.player,
      );
    }
  }

  MediaKitFacadePlayer _requirePlayer() {
    final player = _player;
    if (player == null) {
      throw StateError('No active libmpv player.');
    }
    return player;
  }

  PlaybackSession _requireSession() {
    final session = _session;
    if (session == null) {
      throw StateError('No active PlaybackSession.');
    }
    return session;
  }
}

Uri _validatedCapabilityUri(PlaybackSession session) {
  final uri = session.playbackUri;
  if (uri == null ||
      uri.scheme != 'http' ||
      (uri.host != '127.0.0.1' && uri.host != '::1') ||
      !uri.hasPort ||
      uri.port < 1 ||
      uri.port > 65535 ||
      uri.userInfo.isNotEmpty ||
      uri.hasQuery ||
      uri.hasFragment ||
      uri.pathSegments.length < 4 ||
      uri.pathSegments.length > 16 ||
      uri.pathSegments[0] != 'v1' ||
      uri.pathSegments[1] != 'session' ||
      uri.pathSegments.any(
        (segment) => segment.isEmpty || segment.length > 256,
      ) ||
      uri.path.length > 2048) {
    throw ArgumentError.value(
      uri,
      'session.playbackUri',
      'libmpv requires a bounded numeric-loopback capability URI.',
    );
  }
  return uri;
}

MediaTrack? _trackById(Iterable<MediaTrack> tracks, String? id, String type) {
  if (id == null) {
    return null;
  }
  for (final track in tracks) {
    if (track.id == id) {
      if (track.uri != null) {
        throw StateError('External $type track mapping is not supported.');
      }
      return track;
    }
  }
  throw StateError('Requested $type track is unavailable.');
}

bool _isAuthoritativeTrackId(Iterable<MediaTrack> tracks, String? id) =>
    id == null || tracks.any((track) => track.id == id && track.uri == null);

import 'dart:async';

import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

abstract interface class MediaKitFacade {
  Future<bool> probe();

  Future<MediaKitFacadePlayer> createPlayer();
}

abstract interface class MediaKitFacadePlayer {
  Stream<MediaKitFacadeEvent> get events;

  Object get videoControllerHandle;

  Future<void> open(Uri uri, {Duration startPosition = Duration.zero});

  Future<void> play();

  Future<void> pause();

  Future<void> seek(Duration position);

  Future<void> setVolume(double volume);

  Future<void> setRate(double rate);

  Future<void> selectAudioTrack(String? trackId);

  Future<void> selectSubtitleTrack(String? trackId);

  Future<void> dispose();
}

enum MediaKitFacadeEventType {
  playing,
  completed,
  position,
  buffer,
  volume,
  rate,
  track,
  failure,
}

final class MediaKitFacadeEvent {
  const MediaKitFacadeEvent._({
    required this.type,
    this.flag,
    this.position,
    this.value,
    this.audioTrackId,
    this.subtitleTrackId,
    this.rawError,
  });

  const MediaKitFacadeEvent.playing(bool value)
    : this._(type: MediaKitFacadeEventType.playing, flag: value);

  const MediaKitFacadeEvent.completed(bool value)
    : this._(type: MediaKitFacadeEventType.completed, flag: value);

  const MediaKitFacadeEvent.position(Duration value)
    : this._(type: MediaKitFacadeEventType.position, position: value);

  const MediaKitFacadeEvent.buffer(Duration value)
    : this._(type: MediaKitFacadeEventType.buffer, position: value);

  const MediaKitFacadeEvent.volume(double value)
    : this._(type: MediaKitFacadeEventType.volume, value: value);

  const MediaKitFacadeEvent.rate(double value)
    : this._(type: MediaKitFacadeEventType.rate, value: value);

  const MediaKitFacadeEvent.track({
    required String? audioTrackId,
    required String? subtitleTrackId,
  }) : this._(
         type: MediaKitFacadeEventType.track,
         audioTrackId: audioTrackId,
         subtitleTrackId: subtitleTrackId,
       );

  const MediaKitFacadeEvent.failure(String rawError)
    : this._(type: MediaKitFacadeEventType.failure, rawError: rawError);

  final MediaKitFacadeEventType type;
  final bool? flag;
  final Duration? position;
  final double? value;
  final String? audioTrackId;
  final String? subtitleTrackId;
  final String? rawError;
}

final class ProductionMediaKitFacade implements MediaKitFacade {
  const ProductionMediaKitFacade();

  static void ensureInitialized() => MediaKit.ensureInitialized();

  @override
  Future<bool> probe() async {
    try {
      ensureInitialized();
      final player = Player();
      await player.dispose();
      return true;
    } on Object {
      return false;
    }
  }

  @override
  Future<MediaKitFacadePlayer> createPlayer() async {
    ensureInitialized();
    return ProductionMediaKitFacadePlayer();
  }
}

final class ProductionMediaKitFacadePlayer implements MediaKitFacadePlayer {
  ProductionMediaKitFacadePlayer()
    : _player = Player(
        configuration: const PlayerConfiguration(
          title: 'Wynime',
          logLevel: MPVLogLevel.error,
        ),
      ) {
    _videoController = VideoController(
      _player,
      configuration: const VideoControllerConfiguration(
        enableHardwareAcceleration: true,
      ),
    );
    _subscriptions.addAll([
      _player.stream.playing.listen(
        (value) => _emit(MediaKitFacadeEvent.playing(value)),
      ),
      _player.stream.completed.listen(
        (value) => _emit(MediaKitFacadeEvent.completed(value)),
      ),
      _player.stream.position.listen(
        (value) => _emit(MediaKitFacadeEvent.position(value)),
      ),
      _player.stream.buffer.listen(
        (value) => _emit(MediaKitFacadeEvent.buffer(value)),
      ),
      _player.stream.volume.listen(
        (value) => _emit(MediaKitFacadeEvent.volume(value / 100)),
      ),
      _player.stream.rate.listen(
        (value) => _emit(MediaKitFacadeEvent.rate(value)),
      ),
      _player.stream.track.listen(
        (value) => _emit(
          MediaKitFacadeEvent.track(
            audioTrackId: _normalizedSelectedTrack(value.audio.id),
            subtitleTrackId: _normalizedSelectedTrack(value.subtitle.id),
          ),
        ),
      ),
      _player.stream.error.listen(
        (value) => _emit(MediaKitFacadeEvent.failure(value)),
      ),
    ]);
  }

  final Player _player;
  late final VideoController _videoController;
  final StreamController<MediaKitFacadeEvent> _events =
      StreamController<MediaKitFacadeEvent>.broadcast();
  final List<StreamSubscription<dynamic>> _subscriptions = [];
  bool _disposed = false;

  @override
  Stream<MediaKitFacadeEvent> get events => _events.stream;

  @override
  Object get videoControllerHandle => _videoController;

  @override
  Future<void> open(Uri uri, {Duration startPosition = Duration.zero}) async {
    _ensureActive();
    await _player.open(
      Media(
        uri.toString(),
        httpHeaders: const <String, String>{},
        start: startPosition,
      ),
      play: false,
    );
  }

  @override
  Future<void> play() {
    _ensureActive();
    return _player.play();
  }

  @override
  Future<void> pause() {
    _ensureActive();
    return _player.pause();
  }

  @override
  Future<void> seek(Duration position) {
    _ensureActive();
    return _player.seek(position);
  }

  @override
  Future<void> setVolume(double volume) {
    _ensureActive();
    return _player.setVolume(volume * 100);
  }

  @override
  Future<void> setRate(double rate) {
    _ensureActive();
    return _player.setRate(rate);
  }

  @override
  Future<void> selectAudioTrack(String? trackId) async {
    _ensureActive();
    if (trackId == null) {
      await _player.setAudioTrack(AudioTrack.no());
      return;
    }
    final track = _firstWhereOrNull(
      _player.state.tracks.audio,
      (candidate) => candidate.id == trackId,
    );
    if (track == null) {
      throw StateError('Requested audio track is unavailable.');
    }
    await _player.setAudioTrack(track);
  }

  @override
  Future<void> selectSubtitleTrack(String? trackId) async {
    _ensureActive();
    if (trackId == null) {
      await _player.setSubtitleTrack(SubtitleTrack.no());
      return;
    }
    final track = _firstWhereOrNull(
      _player.state.tracks.subtitle,
      (candidate) => candidate.id == trackId,
    );
    if (track == null) {
      throw StateError('Requested subtitle track is unavailable.');
    }
    await _player.setSubtitleTrack(track);
  }

  @override
  Future<void> dispose() async {
    if (_disposed) {
      return;
    }
    _disposed = true;
    for (final subscription in _subscriptions) {
      await subscription.cancel();
    }
    _subscriptions.clear();
    await _player.dispose();
    await _events.close();
  }

  void _emit(MediaKitFacadeEvent event) {
    if (!_disposed && !_events.isClosed) {
      _events.add(event);
    }
  }

  void _ensureActive() {
    if (_disposed) {
      throw StateError('MediaKit player has been disposed.');
    }
  }
}

T? _firstWhereOrNull<T>(Iterable<T> values, bool Function(T) test) {
  for (final value in values) {
    if (test(value)) {
      return value;
    }
  }
  return null;
}

String? _normalizedSelectedTrack(String id) =>
    id == 'auto' || id == 'no' ? null : id;

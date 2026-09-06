import 'dart:async';

import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:wynime/src/domain/models/playback_session.dart';

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

  Future<void> selectAudioTrack(MediaTrack? track);

  Future<void> selectSubtitleTrack(MediaTrack? track);

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
      _player.stream.track.listen((value) {
        final selectedAudio = _normalizedSelectedTrack(value.audio.id);
        final selectedSubtitle = _normalizedSelectedTrack(value.subtitle.id);
        _emit(
          MediaKitFacadeEvent.track(
            audioTrackId:
                selectedAudio != null && selectedAudio == _audioNativeId
                ? _audioAuthoritativeId
                : null,
            subtitleTrackId:
                selectedSubtitle != null &&
                    selectedSubtitle == _subtitleNativeId
                ? _subtitleAuthoritativeId
                : null,
          ),
        );
      }),
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
  String? _audioNativeId;
  String? _audioAuthoritativeId;
  String? _subtitleNativeId;
  String? _subtitleAuthoritativeId;

  @override
  Stream<MediaKitFacadeEvent> get events => _events.stream;

  @override
  Object get videoControllerHandle => _videoController;

  @override
  Future<void> open(Uri uri, {Duration startPosition = Duration.zero}) async {
    _ensureActive();
    _clearTrackBindings();
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
  Future<void> selectAudioTrack(MediaTrack? track) async {
    _ensureActive();
    if (track == null) {
      _audioNativeId = null;
      _audioAuthoritativeId = null;
      await _player.setAudioTrack(AudioTrack.no());
      return;
    }
    if (track.uri != null) {
      throw StateError('External audio track mapping is not supported.');
    }
    final candidate = _uniqueAudioTrack(_player.state.tracks.audio, track);
    final previousNative = _audioNativeId;
    final previousAuthoritative = _audioAuthoritativeId;
    _audioNativeId = candidate.id;
    _audioAuthoritativeId = track.id;
    try {
      await _player.setAudioTrack(candidate);
    } on Object {
      _audioNativeId = previousNative;
      _audioAuthoritativeId = previousAuthoritative;
      rethrow;
    }
  }

  @override
  Future<void> selectSubtitleTrack(MediaTrack? track) async {
    _ensureActive();
    if (track == null) {
      _subtitleNativeId = null;
      _subtitleAuthoritativeId = null;
      await _player.setSubtitleTrack(SubtitleTrack.no());
      return;
    }
    if (track.uri != null) {
      throw StateError('External subtitle track mapping is not supported.');
    }
    final candidate = _uniqueSubtitleTrack(
      _player.state.tracks.subtitle,
      track,
    );
    final previousNative = _subtitleNativeId;
    final previousAuthoritative = _subtitleAuthoritativeId;
    _subtitleNativeId = candidate.id;
    _subtitleAuthoritativeId = track.id;
    try {
      await _player.setSubtitleTrack(candidate);
    } on Object {
      _subtitleNativeId = previousNative;
      _subtitleAuthoritativeId = previousAuthoritative;
      rethrow;
    }
  }

  @override
  Future<void> dispose() async {
    if (_disposed) {
      return;
    }
    _disposed = true;
    _clearTrackBindings();
    for (final subscription in _subscriptions) {
      await subscription.cancel();
    }
    _subscriptions.clear();
    await _player.dispose();
    await _events.close();
  }

  void _clearTrackBindings() {
    _audioNativeId = null;
    _audioAuthoritativeId = null;
    _subtitleNativeId = null;
    _subtitleAuthoritativeId = null;
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

AudioTrack _uniqueAudioTrack(List<AudioTrack> candidates, MediaTrack target) {
  final internal = candidates
      .where((candidate) => !candidate.uri && !_reservedTrackId(candidate.id))
      .toList(growable: false);
  final exact = internal
      .where((candidate) => candidate.id == target.id)
      .toList();
  if (exact.length == 1) {
    return exact.single;
  }
  if (exact.length > 1) {
    throw StateError('Requested audio track ID is ambiguous.');
  }
  final metadata = internal.where(
    (candidate) =>
        candidate.title == target.label &&
        _optionalCaseInsensitiveEquals(
          target.languageCode,
          candidate.language,
        ) &&
        (!target.isDefault || candidate.isDefault == true),
  );
  final matches = metadata.toList(growable: false);
  if (matches.length != 1) {
    throw StateError(
      matches.isEmpty
          ? 'Requested authoritative audio track is unavailable.'
          : 'Requested authoritative audio track mapping is ambiguous.',
    );
  }
  return matches.single;
}

SubtitleTrack _uniqueSubtitleTrack(
  List<SubtitleTrack> candidates,
  MediaTrack target,
) {
  final internal = candidates
      .where(
        (candidate) =>
            !candidate.uri &&
            !candidate.data &&
            !_reservedTrackId(candidate.id),
      )
      .toList(growable: false);
  final exact = internal
      .where((candidate) => candidate.id == target.id)
      .toList();
  if (exact.length == 1) {
    return exact.single;
  }
  if (exact.length > 1) {
    throw StateError('Requested subtitle track ID is ambiguous.');
  }
  final metadata = internal.where(
    (candidate) =>
        candidate.title == target.label &&
        _optionalCaseInsensitiveEquals(
          target.languageCode,
          candidate.language,
        ) &&
        (!target.isDefault || candidate.isDefault == true),
  );
  final matches = metadata.toList(growable: false);
  if (matches.length != 1) {
    throw StateError(
      matches.isEmpty
          ? 'Requested authoritative subtitle track is unavailable.'
          : 'Requested authoritative subtitle track mapping is ambiguous.',
    );
  }
  return matches.single;
}

bool _optionalCaseInsensitiveEquals(String? expected, String? actual) {
  if (expected == null) {
    return true;
  }
  return actual != null && expected.toLowerCase() == actual.toLowerCase();
}

bool _reservedTrackId(String id) => id == 'auto' || id == 'no';

String? _normalizedSelectedTrack(String id) => _reservedTrackId(id) ? null : id;

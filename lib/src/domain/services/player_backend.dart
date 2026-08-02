import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';

enum PlayerBackendKind { media3, mpv, webView, unsupported }

abstract interface class PlayerBackend {
  String get backendId;

  PlayerBackendKind get kind;

  Stream<PlaybackEvent> get events;

  Future<PlayerBackendAvailability> probe();

  Future<void> open(PlaybackSession session);

  Future<void> play();

  Future<void> pause();

  Future<void> seek(Duration position);

  Future<void> setVolume(double volume);

  Future<void> setRate(double rate);

  Future<void> selectAudioTrack(String? trackId);

  Future<void> selectSubtitleTrack(String? trackId);

  Future<void> close();
}

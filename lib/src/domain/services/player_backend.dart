import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';

enum PlayerBackendKind { media3, mpv, webView, unsupported }

abstract interface class PlayerBackend {
  String get backendId;

  PlayerBackendKind get kind;

  Stream<PlaybackEvent> get events;

  Future<void> open(PlaybackSession session);

  Future<void> pause();

  Future<void> seek(Duration position);

  Future<void> close();
}

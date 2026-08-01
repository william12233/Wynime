import 'package:wynime/src/domain/models/playback_session.dart';

enum PlayerBackendKind { media3, mpv, webView }

abstract interface class PlayerBackend {
  String get backendId;

  PlayerBackendKind get kind;

  Future<void> open(PlaybackSession session);

  Future<void> pause();

  Future<void> seek(Duration position);

  Future<void> close();
}

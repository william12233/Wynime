import 'package:flutter/foundation.dart';
import 'package:wynime/src/application/playback/playback_engine_router.dart';
import 'package:wynime/src/domain/services/player_backend.dart';
import 'package:wynime/src/platform/playback/media3_player_backend.dart';
import 'package:wynime/src/platform/playback/media_kit_facade.dart';
import 'package:wynime/src/platform/playback/mpv_player_backend.dart';

final class PlayerBackendFactory {
  const PlayerBackendFactory._();

  static PlaybackEngineRouter create({
    TargetPlatform? platform,
    Media3PlatformTransport? media3Transport,
    MediaKitFacade mediaKitFacade = const ProductionMediaKitFacade(),
  }) {
    final resolvedPlatform = platform ?? defaultTargetPlatform;
    final media3 = Media3PlayerBackend(transport: media3Transport);
    final mpv = MpvPlayerBackend(facade: mediaKitFacade);
    final webView = UnsupportedPlayerBackend(
      backendId: 'webview-playback-not-implemented',
    );

    return switch (resolvedPlatform) {
      TargetPlatform.android => PlaybackEngineRouter(
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
      ),
      TargetPlatform.windows => PlaybackEngineRouter(
        backends: {
          PlayerBackendKind.mpv: mpv,
          PlayerBackendKind.webView: webView,
        },
        preference: const [PlayerBackendKind.mpv, PlayerBackendKind.webView],
      ),
      _ => PlaybackEngineRouter(
        backends: {PlayerBackendKind.webView: webView},
        preference: const [PlayerBackendKind.webView],
        automaticFallback: false,
      ),
    };
  }
}

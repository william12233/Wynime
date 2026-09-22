import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../application/playback/playback_engine_router.dart';
import 'media3_player_surface.dart';
import 'mpv_player_backend.dart';
import 'mpv_player_surface.dart';

/// Presentation-only access to the surface belonging to the active typed
/// backend. It exposes no media URI or direct player controls.
abstract interface class PlaybackSurfaceHost {
  Widget build(BuildContext context);
}

final class PlatformPlaybackSurfaceHost implements PlaybackSurfaceHost {
  const PlatformPlaybackSurfaceHost(this.router);

  final PlaybackEngineRouter router;

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform == TargetPlatform.android) {
      return const Media3PlayerSurface();
    }
    final backend = router.activeBackend;
    if (backend is MpvPlayerBackend) {
      final player = backend.activePlayer;
      if (player != null) return MpvPlayerSurface(player: player);
    }
    return ColoredBox(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: const Center(child: CircularProgressIndicator()),
    );
  }
}

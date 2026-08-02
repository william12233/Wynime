import 'dart:collection';

import 'package:wynime/src/application/playback/switchable_player_backend.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

enum PlaybackHostPlatform { android, windows, unsupported }

final class PlayerBackendRegistry {
  PlayerBackendRegistry(Iterable<PlayerBackend> backends)
    : _backends = UnmodifiableMapView(_index(backends));

  final UnmodifiableMapView<String, PlayerBackend> _backends;

  PlayerBackend require(String backendId) {
    final backend = _backends[backendId];
    if (backend == null) {
      throw StateError('Required player backend is unavailable: $backendId');
    }
    return backend;
  }

  static Map<String, PlayerBackend> _index(
    Iterable<PlayerBackend> backends,
  ) {
    final indexed = <String, PlayerBackend>{};
    for (final backend in backends) {
      final existing = indexed[backend.backendId];
      if (existing != null) {
        throw ArgumentError.value(
          backend.backendId,
          'backends',
          'Player backend IDs must be unique.',
        );
      }
      indexed[backend.backendId] = backend;
    }
    if (indexed.isEmpty) {
      throw ArgumentError.value(
        backends,
        'backends',
        'At least one player backend is required.',
      );
    }
    return indexed;
  }
}

final class PlayerBackendSelectionPolicy {
  const PlayerBackendSelectionPolicy();

  PlayerBackend select({
    required PlaybackHostPlatform platform,
    required PlayerBackendRegistry registry,
  }) => switch (platform) {
    PlaybackHostPlatform.android => SwitchablePlayerBackend(
      primary: registry.require('android-media3'),
      fallback: registry.require('android-mpv'),
    ),
    PlaybackHostPlatform.windows => registry.require('windows-mpv'),
    PlaybackHostPlatform.unsupported => throw UnsupportedError(
      'No player backend policy exists for this host platform.',
    ),
  };
}

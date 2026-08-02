import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/playback/player_backend_registry.dart';
import 'package:wynime/src/application/playback/switchable_player_backend.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

void main() {
  test('Android selects Media3 primary with mpv fallback', () async {
    final media3 = _Backend('android-media3', PlayerBackendKind.media3);
    final mpv = _Backend('android-mpv', PlayerBackendKind.mpv);
    final registry = PlayerBackendRegistry([media3, mpv]);

    final selected = const PlayerBackendSelectionPolicy().select(
      platform: PlaybackHostPlatform.android,
      registry: registry,
    );
    addTearDown(selected.close);

    expect(selected, isA<SwitchablePlayerBackend>());
    expect(selected.kind, PlayerBackendKind.media3);
  });

  test('Windows selects the direct Windows mpv backend', () {
    final mpv = _Backend('windows-mpv', PlayerBackendKind.mpv);
    final registry = PlayerBackendRegistry([mpv]);

    final selected = const PlayerBackendSelectionPolicy().select(
      platform: PlaybackHostPlatform.windows,
      registry: registry,
    );

    expect(selected, same(mpv));
  });

  test('registry rejects duplicate backend identities', () {
    expect(
      () => PlayerBackendRegistry([
        _Backend('android-media3', PlayerBackendKind.media3),
        _Backend('android-media3', PlayerBackendKind.mpv),
      ]),
      throwsArgumentError,
    );
  });

  test('missing and unsupported platform selections fail closed', () {
    final registry = PlayerBackendRegistry([
      _Backend('android-media3', PlayerBackendKind.media3),
    ]);

    expect(
      () => const PlayerBackendSelectionPolicy().select(
        platform: PlaybackHostPlatform.android,
        registry: registry,
      ),
      throwsStateError,
    );
    expect(
      () => const PlayerBackendSelectionPolicy().select(
        platform: PlaybackHostPlatform.unsupported,
        registry: registry,
      ),
      throwsUnsupportedError,
    );
  });
}

final class _Backend implements PlayerBackend {
  _Backend(this.backendId, this.kind);

  @override
  final String backendId;

  @override
  final PlayerBackendKind kind;

  @override
  Stream<PlaybackEvent> get events => const Stream.empty();

  @override
  Future<void> open(PlaybackSession session) async {}

  @override
  Future<void> pause() async {}

  @override
  Future<void> seek(Duration position) async {}

  @override
  Future<void> close() async {}
}

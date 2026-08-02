import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/player_backend_capability.dart';
import 'package:wynime/src/platform/playback/mpv_player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test('maps a typed runtime capability probe', () async {
    final transport = _FakeMpvTransport(
      probePayload: const {
        'backendId': 'windows-mpv',
        'availability': 'available',
        'code': 'libmpv_ready',
        'clientApiVersion': 131072,
        'runtimeVersion': '0.40.0',
      },
    );
    final backend = MpvPlayerBackend(
      backendId: 'windows-mpv',
      transport: transport,
    );

    final capability = await backend.probe();

    expect(capability.availability, PlayerBackendAvailability.available);
    expect(capability.clientApiVersion, 131072);
    expect(capability.runtimeVersion, '0.40.0');
  });

  test('opens only a numeric loopback capability URI', () async {
    final transport = _FakeMpvTransport();
    final backend = MpvPlayerBackend(
      backendId: 'windows-mpv',
      transport: transport,
    );
    final session = testPlaybackSession(
      playbackUri: Uri.parse('http://127.0.0.1:42000/capability/master.m3u8'),
    );

    await backend.open(session);

    expect(transport.calls.single.method, 'open');
    expect(transport.calls.single.arguments, {
      'sessionId': session.sessionId,
      'uri': session.playbackUri!.toString(),
      'timelineMapIdentity': session.timelineMapIdentity,
    });
  });

  test('rejects direct upstream and non-numeric player URLs', () async {
    final transport = _FakeMpvTransport();
    final backend = MpvPlayerBackend(
      backendId: 'windows-mpv',
      transport: transport,
    );

    expect(() => backend.open(testPlaybackSession()), throwsArgumentError);
    expect(
      () => backend.open(
        testPlaybackSession(
          playbackUri: Uri.parse('http://localhost:42000/master.m3u8'),
        ),
      ),
      throwsArgumentError,
    );
  });

  test('rejects stale session events', () async {
    final transport = _FakeMpvTransport();
    final backend = MpvPlayerBackend(
      backendId: 'windows-mpv',
      transport: transport,
    );
    final errors = <Object>[];
    final subscription = backend.events.listen((_) {}, onError: errors.add);
    addTearDown(subscription.cancel);
    await backend.open(
      testPlaybackSession(
        sessionId: 'current-session',
        playbackUri: Uri.parse('http://127.0.0.1:42000/master.m3u8'),
      ),
    );

    transport.addEvent(const {
      'sequence': 1,
      'state': 'playing',
      'sessionId': 'stale-session',
      'positionMs': 10,
    });
    await _eventually(() => errors.isNotEmpty);

    expect(errors.single, isA<StateError>());
  });

  test('maps decoder failures through the shared classifier', () async {
    final transport = _FakeMpvTransport();
    final backend = MpvPlayerBackend(
      backendId: 'windows-mpv',
      transport: transport,
    );
    final events = <PlaybackEvent>[];
    final subscription = backend.events.listen(events.add);
    addTearDown(subscription.cancel);
    await backend.open(
      testPlaybackSession(
        playbackUri: Uri.parse('http://[::1]:42000/master.m3u8'),
      ),
    );

    transport.addEvent(const {
      'sequence': 1,
      'state': 'failed',
      'sessionId': 'session-1',
      'errorCode': 'decoder_init_failed',
      'positionMs': 1200,
    });
    await _eventually(() => events.isNotEmpty);

    expect(events.single.state, PlaybackState.failed);
    expect(events.single.failure?.kind, PlaybackFailureKind.decoder);
  });
}

Future<void> _eventually(bool Function() predicate) async {
  for (var attempt = 0; attempt < 50; attempt += 1) {
    if (predicate()) {
      return;
    }
    await Future<void>.delayed(const Duration(milliseconds: 1));
  }
  fail('Condition was not satisfied.');
}

final class _FakeMpvTransport implements MpvPlatformTransport {
  _FakeMpvTransport({
    this.probePayload = const {
      'backendId': 'windows-mpv',
      'availability': 'unavailable',
      'code': 'runtime_missing',
    },
  });

  final Map<String, Object?> probePayload;
  final StreamController<Map<String, Object?>> _events =
      StreamController<Map<String, Object?>>.broadcast();
  final List<_Call> calls = [];

  @override
  Stream<Map<String, Object?>> get events => _events.stream;

  @override
  Future<Map<String, Object?>> probe() async => probePayload;

  @override
  Future<void> invoke(String method, Map<String, Object?> arguments) async {
    calls.add(_Call(method, Map<String, Object?>.unmodifiable(arguments)));
  }

  void addEvent(Map<String, Object?> event) => _events.add(event);
}

final class _Call {
  const _Call(this.method, this.arguments);

  final String method;
  final Map<String, Object?> arguments;
}

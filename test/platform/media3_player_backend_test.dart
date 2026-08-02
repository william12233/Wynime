import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/platform/playback/media3_player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test('Media3 backend accepts only the loopback proxy handoff', () async {
    final transport = _FakeMedia3Transport();
    final backend = Media3PlayerBackend(transport: transport);
    final session = testPlaybackSession(
      playbackUri: Uri.parse(
        'http://127.0.0.1:42000/v1/session/token/resource/master',
      ),
    );

    await backend.open(session);

    expect(transport.calls.single.method, 'open');
    expect(transport.calls.single.arguments['sessionId'], session.sessionId);
    expect(
      transport.calls.single.arguments['uri'],
      session.playbackUri.toString(),
    );

    await expectLater(backend.open(testPlaybackSession()), throwsArgumentError);
  });

  test(
    'Media3 events stay bound to the active session and classify 403',
    () async {
      final transport = _FakeMedia3Transport();
      final backend = Media3PlayerBackend(transport: transport);
      final session = testPlaybackSession(
        playbackUri: Uri.parse(
          'http://127.0.0.1:42000/v1/session/token/resource/master',
        ),
      );
      await backend.open(session);

      final eventsFuture = backend.events.take(2).toList();
      transport.add({
        'sequence': 0,
        'sessionId': session.sessionId,
        'state': 'playing',
        'positionMs': 1200,
        'bufferedPositionMs': 5000,
      });
      transport.add({
        'sequence': 1,
        'sessionId': session.sessionId,
        'state': 'failed',
        'errorCode': 'error_code_io_bad_http_status',
        'httpStatus': 403,
        'sessionExpired': true,
        'positionMs': 1200,
        'bufferedPositionMs': 5000,
      });

      final events = await eventsFuture;
      expect(events.first.state, PlaybackState.playing);
      expect(events.first.position, const Duration(milliseconds: 1200));
      expect(events.last.failure?.kind, PlaybackFailureKind.sessionExpired);
      expect(events.last.failure?.shouldRefreshSession, isTrue);
    },
  );

  test('Media3 rejects stale or non-monotonic platform events', () async {
    final transport = _FakeMedia3Transport();
    final backend = Media3PlayerBackend(transport: transport);
    final session = testPlaybackSession(
      playbackUri: Uri.parse(
        'http://127.0.0.1:42000/v1/session/token/resource/master',
      ),
    );
    await backend.open(session);

    final staleError = expectLater(
      backend.events,
      emitsError(isA<StateError>()),
    );
    transport.add({
      'sequence': 0,
      'sessionId': 'other-session',
      'state': 'playing',
    });
    await staleError;
  });

  test('pause, seek, and close use the typed platform transport', () async {
    final transport = _FakeMedia3Transport();
    final backend = Media3PlayerBackend(transport: transport);

    await backend.pause();
    await backend.seek(const Duration(seconds: 42));
    await backend.close();

    expect(transport.calls.map((call) => call.method), [
      'pause',
      'seek',
      'close',
    ]);
    expect(transport.calls[1].arguments['positionMs'], 42000);
    await expectLater(
      backend.seek(const Duration(milliseconds: -1)),
      throwsArgumentError,
    );
  });

  test(
    'Windows placeholder backend reports explicit unsupported failure',
    () async {
      final backend = UnsupportedPlayerBackend(backendId: 'windows-phase4');
      final failureFuture = backend.events.first;

      await expectLater(
        backend.open(testPlaybackSession()),
        throwsUnsupportedError,
      );
      final event = await failureFuture;
      expect(event.state, PlaybackState.failed);
      expect(event.failure?.kind, PlaybackFailureKind.unsupported);
    },
  );
}

final class _FakeMedia3Transport implements Media3PlatformTransport {
  final StreamController<Map<String, Object?>> _controller =
      StreamController<Map<String, Object?>>.broadcast();
  final List<_Call> calls = [];

  @override
  Stream<Map<String, Object?>> get events => _controller.stream;

  @override
  Future<void> invoke(String method, Map<String, Object?> arguments) async {
    calls.add(_Call(method, arguments));
  }

  void add(Map<String, Object?> event) => _controller.add(event);
}

final class _Call {
  const _Call(this.method, this.arguments);

  final String method;
  final Map<String, Object?> arguments;
}

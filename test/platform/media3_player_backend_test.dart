import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/platform/playback/media3_player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  test('Media3 probe reports an available native bridge', () async {
    final transport = _FakeMedia3Transport();
    final backend = Media3PlayerBackend(transport: transport);

    final availability = await backend.probe();

    expect(availability.status, PlayerBackendAvailabilityStatus.available);
    expect(transport.calls.single.method, 'probe');
  });

  test('Media3 probe fails closed when the plugin is missing', () async {
    final transport = _FakeMedia3Transport()
      ..errors['probe'] = MissingPluginException('not installed');
    final backend = Media3PlayerBackend(transport: transport);

    final availability = await backend.probe();

    expect(availability.status, PlayerBackendAvailabilityStatus.unavailable);
    expect(availability.reasonCode, 'platform_plugin_missing');
  });

  test('Media3 backend accepts only the loopback proxy handoff', () async {
    final transport = _FakeMedia3Transport();
    final backend = Media3PlayerBackend(transport: transport);
    final session = _loopbackSession();

    await backend.open(session);

    expect(transport.calls.single.method, 'open');
    expect(transport.calls.single.arguments['sessionId'], session.sessionId);
    expect(
      transport.calls.single.arguments['timelineMapIdentity'],
      session.timelineMapIdentity,
    );
    expect(
      transport.calls.single.arguments['uri'],
      session.playbackUri.toString(),
    );

    await expectLater(backend.open(testPlaybackSession()), throwsArgumentError);
    await expectLater(
      () => backend.open(
        testPlaybackSession(
          playbackUri: Uri.parse(
            'http://example.com:42000/v1/session/token/resource/master',
          ),
        ),
      ),
      throwsArgumentError,
    );
  });

  test(
    'Media3 events stay bound to session and timeline and classify 403',
    () async {
      final transport = _FakeMedia3Transport();
      final backend = Media3PlayerBackend(transport: transport);
      final session = _loopbackSession();
      await backend.open(session);

      final eventsFuture = backend.events.take(2).toList();
      transport.add({
        'sequence': 0,
        'sessionId': session.sessionId,
        'timelineMapIdentity': session.timelineMapIdentity,
        'state': 'playing',
        'positionMs': 1200,
        'bufferedPositionMs': 5000,
        'volume': 0.5,
        'rate': 1.25,
        'audioTrackId': '0:0',
      });
      transport.add({
        'sequence': 1,
        'sessionId': session.sessionId,
        'timelineMapIdentity': session.timelineMapIdentity,
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
      expect(events.first.volume, 0.5);
      expect(events.first.rate, 1.25);
      expect(events.first.audioTrackId, '0:0');
      expect(events.last.failure?.kind, PlaybackFailureKind.sessionExpired);
      expect(events.last.failure?.shouldRefreshSession, isTrue);
    },
  );

  test('Media3 rejects stale session and timeline events', () async {
    final transport = _FakeMedia3Transport();
    final backend = Media3PlayerBackend(transport: transport);
    final session = _loopbackSession();
    await backend.open(session);

    final staleSession = expectLater(
      backend.events,
      emitsError(isA<StateError>()),
    );
    transport.add({
      'sequence': 0,
      'sessionId': 'other-session',
      'timelineMapIdentity': session.timelineMapIdentity,
      'state': 'playing',
    });
    await staleSession;

    final staleTimeline = expectLater(
      backend.events,
      emitsError(isA<StateError>()),
    );
    transport.add({
      'sequence': 1,
      'sessionId': session.sessionId,
      'timelineMapIdentity': 'other-timeline',
      'state': 'playing',
    });
    await staleTimeline;
  });

  test('Media3 exposes the complete typed control surface', () async {
    final transport = _FakeMedia3Transport();
    final backend = Media3PlayerBackend(transport: transport);

    await backend.play();
    await backend.pause();
    await backend.seek(const Duration(seconds: 42));
    await backend.setVolume(0.4);
    await backend.setRate(1.5);
    await backend.selectAudioTrack('0:1');
    await backend.selectSubtitleTrack(null);
    await backend.close();

    expect(transport.calls.map((call) => call.method), [
      'play',
      'pause',
      'seek',
      'setVolume',
      'setRate',
      'selectTrack',
      'selectTrack',
      'close',
    ]);
    expect(transport.calls[2].arguments['positionMs'], 42000);
    expect(transport.calls[3].arguments['volume'], 0.4);
    expect(transport.calls[4].arguments['rate'], 1.5);
    expect(transport.calls[5].arguments, {'type': 'audio', 'id': '0:1'});
    expect(transport.calls[6].arguments, {'type': 'subtitle', 'id': null});

    await expectLater(
      backend.seek(const Duration(milliseconds: -1)),
      throwsArgumentError,
    );
    await expectLater(backend.setVolume(2), throwsArgumentError);
    await expectLater(backend.setRate(0), throwsArgumentError);
  });

  test(
    'unsupported backend reports explicit unavailability and failure',
    () async {
      final backend = UnsupportedPlayerBackend(
        backendId: 'windows-placeholder',
      );
      final availability = await backend.probe();
      final failureFuture = backend.events.first;

      expect(availability.isAvailable, isFalse);
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

PlaybackSession _loopbackSession() => testPlaybackSession(
  playbackUri: Uri.parse(
    'http://127.0.0.1:42000/v1/session/token/resource/master',
  ),
);

final class _FakeMedia3Transport implements Media3PlatformTransport {
  final StreamController<Map<String, Object?>> _controller =
      StreamController<Map<String, Object?>>.broadcast();
  final List<_Call> calls = [];
  final Map<String, Object> errors = {};

  @override
  Stream<Map<String, Object?>> get events => _controller.stream;

  @override
  Future<void> invoke(String method, Map<String, Object?> arguments) async {
    calls.add(_Call(method, arguments));
    final error = errors[method];
    if (error != null) {
      throw error;
    }
  }

  void add(Map<String, Object?> event) => _controller.add(event);
}

final class _Call {
  const _Call(this.method, this.arguments);

  final String method;
  final Map<String, Object?> arguments;
}

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

abstract interface class Media3PlatformTransport {
  Stream<Map<String, Object?>> get events;

  Future<void> invoke(String method, Map<String, Object?> arguments);
}

final class MethodChannelMedia3Transport implements Media3PlatformTransport {
  factory MethodChannelMedia3Transport({
    MethodChannel methodChannel = const MethodChannel(
      'io.github.william12233.wynime/media3',
    ),
    EventChannel eventChannel = const EventChannel(
      'io.github.william12233.wynime/media3/events',
    ),
  }) => MethodChannelMedia3Transport._(methodChannel, eventChannel);

  MethodChannelMedia3Transport._(this._methodChannel, this._eventChannel);

  final MethodChannel _methodChannel;
  final EventChannel _eventChannel;
  Stream<Map<String, Object?>>? _events;

  @override
  Stream<Map<String, Object?>> get events => _events ??= _eventChannel
      .receiveBroadcastStream()
      .map(_stringObjectMap)
      .asBroadcastStream();

  @override
  Future<void> invoke(String method, Map<String, Object?> arguments) async {
    await _methodChannel.invokeMethod<void>(method, arguments);
  }
}

final class Media3PlayerBackend implements PlayerBackend {
  factory Media3PlayerBackend({
    Media3PlatformTransport? transport,
    PlaybackErrorClassifier classifier = const PlaybackErrorClassifier(),
  }) => Media3PlayerBackend._(
    transport ?? MethodChannelMedia3Transport(),
    classifier,
  );

  Media3PlayerBackend._(this._transport, this._classifier) {
    _events = _transport.events.map(_mapEvent).asBroadcastStream();
  }

  final Media3PlatformTransport _transport;
  final PlaybackErrorClassifier _classifier;
  late final Stream<PlaybackEvent> _events;
  int _lastSequence = -1;
  String? _activeSessionId;

  @override
  String get backendId => 'android-media3';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events;

  @override
  Future<void> open(PlaybackSession session) async {
    final uri = session.playbackUri;
    if (uri == null || !_isNumericLoopback(uri)) {
      throw ArgumentError.value(
        uri,
        'session.playbackUri',
        'Media3 accepts only a loopback proxy endpoint.',
      );
    }
    _activeSessionId = session.sessionId;
    try {
      await _transport.invoke('open', {
        'sessionId': session.sessionId,
        'uri': uri.toString(),
        'timelineMapIdentity': session.timelineMapIdentity,
      });
    } on Object {
      _activeSessionId = null;
      rethrow;
    }
  }

  @override
  Future<void> pause() => _transport.invoke('pause', const {});

  @override
  Future<void> seek(Duration position) async {
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
    await _transport.invoke('seek', {'positionMs': position.inMilliseconds});
  }

  @override
  Future<void> close() async {
    await _transport.invoke('close', const {});
    _activeSessionId = null;
  }

  PlaybackEvent _mapEvent(Map<String, Object?> raw) {
    final sequence = _requiredInt(raw, 'sequence');
    if (sequence <= _lastSequence) {
      throw StateError('Media3 event sequence must be strictly monotonic.');
    }
    _lastSequence = sequence;
    final state = _stateFromName(_requiredString(raw, 'state'));
    final eventSessionId = _optionalString(raw, 'sessionId');
    final activeSessionId = _activeSessionId;
    if (state != PlaybackState.idle) {
      if (eventSessionId == null) {
        throw const FormatException(
          'Non-idle Media3 events require a sessionId.',
        );
      }
      if (activeSessionId != null && eventSessionId != activeSessionId) {
        throw StateError('Media3 event belongs to a stale PlaybackSession.');
      }
    }

    final position = Duration(
      milliseconds: _optionalInt(raw, 'positionMs') ?? 0,
    );
    final buffered = Duration(
      milliseconds: _optionalInt(raw, 'bufferedPositionMs') ?? 0,
    );

    PlaybackFailure? failure;
    if (state == PlaybackState.failed) {
      failure = _classifier.classify(
        PlaybackErrorSignal(
          code: _requiredString(raw, 'errorCode'),
          stage: PlaybackErrorStage.player,
          httpStatus: _optionalInt(raw, 'httpStatus'),
          sessionExpired: raw['sessionExpired'] == true,
        ),
      );
    }
    return PlaybackEvent(
      sequence: sequence,
      state: state,
      position: position,
      bufferedPosition: buffered,
      failure: failure,
    );
  }
}

final class UnsupportedPlayerBackend implements PlayerBackend {
  UnsupportedPlayerBackend({required this.backendId});

  @override
  final String backendId;

  final StreamController<PlaybackEvent> _controller =
      StreamController<PlaybackEvent>.broadcast();
  int _sequence = 0;

  @override
  PlayerBackendKind get kind => PlayerBackendKind.unsupported;

  @override
  Stream<PlaybackEvent> get events => _controller.stream;

  @override
  Future<void> open(PlaybackSession session) async {
    final failure = PlaybackFailure(
      code: 'backend_unsupported',
      kind: PlaybackFailureKind.unsupported,
      stage: PlaybackErrorStage.player,
      retryable: false,
      shouldRefreshSession: false,
    );
    _controller.add(
      PlaybackEvent(
        sequence: _sequence++,
        state: PlaybackState.failed,
        failure: failure,
      ),
    );
    throw UnsupportedError('$backendId is not available on this platform.');
  }

  @override
  Future<void> pause() async {}

  @override
  Future<void> seek(Duration position) async {
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
  }

  @override
  Future<void> close() async {
    if (!_controller.isClosed) {
      _controller.add(
        PlaybackEvent(sequence: _sequence++, state: PlaybackState.closed),
      );
    }
  }
}

Map<String, Object?> _stringObjectMap(Object? value) {
  if (value is! Map<Object?, Object?>) {
    throw const FormatException('Media3 event must be a map.');
  }
  final result = <String, Object?>{};
  for (final entry in value.entries) {
    final key = entry.key;
    if (key is! String) {
      throw const FormatException('Media3 event keys must be strings.');
    }
    result[key] = entry.value;
  }
  return result;
}

String _requiredString(Map<String, Object?> map, String key) {
  final value = _optionalString(map, key);
  if (value == null) {
    throw FormatException('Media3 event field $key must be a string.');
  }
  return value;
}

String? _optionalString(Map<String, Object?> map, String key) {
  final value = map[key];
  if (value == null) {
    return null;
  }
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('Media3 event field $key must be a string.');
  }
  return value.trim();
}

int _requiredInt(Map<String, Object?> map, String key) {
  final value = _optionalInt(map, key);
  if (value == null) {
    throw FormatException('Media3 event field $key must be an integer.');
  }
  return value;
}

int? _optionalInt(Map<String, Object?> map, String key) {
  final value = map[key];
  if (value == null) {
    return null;
  }
  if (value is int) {
    return value;
  }
  if (value is num && value.isFinite && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw FormatException('Media3 event field $key must be an integer.');
}

PlaybackState _stateFromName(String value) => switch (value) {
  'idle' => PlaybackState.idle,
  'opening' => PlaybackState.opening,
  'buffering' => PlaybackState.buffering,
  'ready' => PlaybackState.ready,
  'playing' => PlaybackState.playing,
  'paused' => PlaybackState.paused,
  'ended' => PlaybackState.ended,
  'closed' => PlaybackState.closed,
  'failed' => PlaybackState.failed,
  _ => throw FormatException('Unknown Media3 state: $value'),
};

bool _isNumericLoopback(Uri uri) =>
    uri.scheme == 'http' &&
    uri.userInfo.isEmpty &&
    uri.hasPort &&
    uri.port > 0 &&
    !uri.hasQuery &&
    !uri.hasFragment &&
    (uri.host == '127.0.0.1' || uri.host == '::1');

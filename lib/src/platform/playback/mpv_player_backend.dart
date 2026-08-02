import 'dart:async';

import 'package:flutter/services.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend_capability.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

abstract interface class MpvPlatformTransport {
  Stream<Map<String, Object?>> get events;

  Future<Map<String, Object?>> probe();

  Future<void> invoke(String method, Map<String, Object?> arguments);
}

final class MethodChannelMpvTransport implements MpvPlatformTransport {
  factory MethodChannelMpvTransport({
    MethodChannel methodChannel = const MethodChannel(
      'io.github.william12233.wynime/mpv',
    ),
    EventChannel eventChannel = const EventChannel(
      'io.github.william12233.wynime/mpv/events',
    ),
  }) => MethodChannelMpvTransport._(methodChannel, eventChannel);

  MethodChannelMpvTransport._(this._methodChannel, this._eventChannel);

  final MethodChannel _methodChannel;
  final EventChannel _eventChannel;
  Stream<Map<String, Object?>>? _events;

  @override
  Stream<Map<String, Object?>> get events => _events ??= _eventChannel
      .receiveBroadcastStream()
      .map(_stringObjectMap)
      .asBroadcastStream();

  @override
  Future<Map<String, Object?>> probe() async => _stringObjectMap(
    await _methodChannel.invokeMethod<Object?>('probe'),
  );

  @override
  Future<void> invoke(String method, Map<String, Object?> arguments) async {
    await _methodChannel.invokeMethod<void>(method, arguments);
  }
}

final class MpvPlayerBackend
    implements PlayerBackend, PlayerBackendCapabilitySource {
  factory MpvPlayerBackend({
    required String backendId,
    MpvPlatformTransport? transport,
    PlaybackErrorClassifier classifier = const PlaybackErrorClassifier(),
  }) => MpvPlayerBackend._(
    _validateBackendId(backendId),
    transport ?? MethodChannelMpvTransport(),
    classifier,
  );

  MpvPlayerBackend._(this.backendId, this._transport, this._classifier) {
    _events = _transport.events.map(_mapEvent).asBroadcastStream();
  }

  @override
  final String backendId;
  final MpvPlatformTransport _transport;
  final PlaybackErrorClassifier _classifier;
  late final Stream<PlaybackEvent> _events;
  int _lastSequence = -1;
  String? _activeSessionId;

  @override
  PlayerBackendKind get kind => PlayerBackendKind.mpv;

  @override
  Stream<PlaybackEvent> get events => _events;

  @override
  Future<PlayerBackendCapability> probe() async {
    final raw = await _transport.probe();
    final reportedBackendId = _requiredString(raw, 'backendId');
    if (reportedBackendId != backendId) {
      throw StateError('mpv capability belongs to another backend.');
    }
    return PlayerBackendCapability(
      backendId: reportedBackendId,
      availability: _availabilityFromName(
        _requiredString(raw, 'availability'),
      ),
      code: _requiredString(raw, 'code'),
      clientApiVersion: _optionalInt(raw, 'clientApiVersion'),
      runtimeVersion: _optionalString(raw, 'runtimeVersion'),
    );
  }

  @override
  Future<void> open(PlaybackSession session) async {
    final uri = session.playbackUri;
    if (uri == null || !_isNumericLoopback(uri)) {
      throw ArgumentError.value(
        uri,
        'session.playbackUri',
        'mpv accepts only a numeric loopback proxy endpoint.',
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
      throw StateError('mpv event sequence must be strictly monotonic.');
    }
    _lastSequence = sequence;
    final state = _stateFromName(_requiredString(raw, 'state'));
    final eventSessionId = _optionalString(raw, 'sessionId');
    final activeSessionId = _activeSessionId;
    if (state != PlaybackState.idle) {
      if (eventSessionId == null) {
        throw const FormatException('Non-idle mpv events require a sessionId.');
      }
      if (activeSessionId != null && eventSessionId != activeSessionId) {
        throw StateError('mpv event belongs to a stale PlaybackSession.');
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

Map<String, Object?> _stringObjectMap(Object? value) {
  if (value is! Map<Object?, Object?>) {
    throw const FormatException('mpv platform payload must be a map.');
  }
  final result = <String, Object?>{};
  for (final entry in value.entries) {
    final key = entry.key;
    if (key is! String) {
      throw const FormatException('mpv payload keys must be strings.');
    }
    result[key] = entry.value;
  }
  return result;
}

String _validateBackendId(String value) {
  final normalized = value.trim().toLowerCase();
  if (normalized.isEmpty ||
      normalized.length > 96 ||
      !RegExp(r'^[a-z0-9][a-z0-9._-]*$').hasMatch(normalized)) {
    throw ArgumentError.value(value, 'backendId', 'Invalid backend identifier.');
  }
  return normalized;
}

String _requiredString(Map<String, Object?> map, String key) {
  final value = _optionalString(map, key);
  if (value == null) {
    throw FormatException('mpv payload field $key must be a string.');
  }
  return value;
}

String? _optionalString(Map<String, Object?> map, String key) {
  final value = map[key];
  if (value == null) {
    return null;
  }
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('mpv payload field $key must be a string.');
  }
  return value.trim();
}

int _requiredInt(Map<String, Object?> map, String key) {
  final value = _optionalInt(map, key);
  if (value == null) {
    throw FormatException('mpv payload field $key must be an integer.');
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
  throw FormatException('mpv payload field $key must be an integer.');
}

PlayerBackendAvailability _availabilityFromName(String value) => switch (value) {
  'available' => PlayerBackendAvailability.available,
  'unavailable' => PlayerBackendAvailability.unavailable,
  'incompatible' => PlayerBackendAvailability.incompatible,
  _ => throw FormatException('Unknown mpv availability: $value'),
};

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
  _ => throw FormatException('Unknown mpv state: $value'),
};

bool _isNumericLoopback(Uri uri) =>
    uri.scheme == 'http' &&
    uri.userInfo.isEmpty &&
    uri.hasPort &&
    uri.port > 0 &&
    !uri.hasQuery &&
    !uri.hasFragment &&
    (uri.host == '127.0.0.1' || uri.host == '::1');

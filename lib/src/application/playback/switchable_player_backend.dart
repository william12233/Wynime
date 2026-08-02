import 'dart:async';

import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_handoff.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend_capability.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

final class SwitchablePlayerBackend implements PlayerBackend {
  SwitchablePlayerBackend({
    required PlayerBackend primary,
    required PlayerBackend fallback,
  }) : _primary = primary,
       _fallback = fallback {
    if (primary.backendId == fallback.backendId) {
      throw ArgumentError('Primary and fallback backend IDs must be distinct.');
    }
    _subscriptions.add(
      primary.events.listen(
        (event) => _handleBackendEvent(primary, event),
        onError: (Object error, StackTrace stackTrace) {
          _events.addError(error, stackTrace);
        },
      ),
    );
    _subscriptions.add(
      fallback.events.listen(
        (event) => _handleBackendEvent(fallback, event),
        onError: (Object error, StackTrace stackTrace) {
          _events.addError(error, stackTrace);
        },
      ),
    );
  }

  final PlayerBackend _primary;
  final PlayerBackend _fallback;
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final List<StreamSubscription<PlaybackEvent>> _subscriptions = [];

  PlayerBackend? _active;
  PlaybackSession? _session;
  Duration _position = Duration.zero;
  Duration _bufferedPosition = Duration.zero;
  PlaybackState _lastState = PlaybackState.idle;
  bool _fallbackUsed = false;
  bool _closed = false;
  int _sequence = 0;
  int _generation = 0;
  Future<void>? _switching;
  PlaybackHandoffSnapshot? _lastHandoff;

  @override
  String get backendId =>
      'switchable-${_primary.backendId}-${_fallback.backendId}';

  @override
  PlayerBackendKind get kind => _active?.kind ?? _primary.kind;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  PlayerBackend? get activeBackend => _active;

  PlaybackHandoffSnapshot? get lastHandoff => _lastHandoff;

  @override
  Future<void> open(PlaybackSession session) async {
    _ensureOpen();
    await _closeActive();
    _session = session;
    _position = Duration.zero;
    _bufferedPosition = Duration.zero;
    _lastState = PlaybackState.opening;
    _fallbackUsed = false;
    _lastHandoff = null;
    final generation = ++_generation;

    final preferred = await _preferredAvailableBackend();
    if (_closed || generation != _generation) {
      throw StateError('Playback open was superseded or backend was closed.');
    }
    _active = preferred;
    try {
      await preferred.open(session);
    } on UnsupportedError {
      if (identical(preferred, _primary)) {
        await _openFallbackAfterUnavailable(session, generation);
        return;
      }
      _active = null;
      rethrow;
    } on Object {
      _active = null;
      rethrow;
    }
  }

  @override
  Future<void> pause() async {
    _ensureActive();
    await _active!.pause();
  }

  @override
  Future<void> seek(Duration position) async {
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
    _ensureActive();
    await _active!.seek(position);
    _position = position;
  }

  @override
  Future<void> close() async {
    if (_closed) {
      return;
    }
    _closed = true;
    ++_generation;
    await _closeActive();
    for (final subscription in _subscriptions) {
      await subscription.cancel();
    }
    await _events.close();
  }

  Future<PlayerBackend> _preferredAvailableBackend() async {
    final primary = _primary;
    if (primary is! PlayerBackendCapabilitySource) {
      return primary;
    }
    final capabilitySource = primary as PlayerBackendCapabilitySource;
    final capability = await capabilitySource.probe();
    if (capability.backendId != primary.backendId) {
      throw StateError(
        'Backend capability identity does not match its backend.',
      );
    }
    if (capability.isAvailable) {
      return primary;
    }
    _fallbackUsed = true;
    return _fallback;
  }

  Future<void> _openFallbackAfterUnavailable(
    PlaybackSession session,
    int generation,
  ) async {
    _fallbackUsed = true;
    final fallback = _fallback;
    await _requireAvailable(fallback);
    if (_closed || generation != _generation) {
      throw StateError('Fallback open was superseded or backend was closed.');
    }
    _active = fallback;
    try {
      await fallback.open(session);
    } on Object {
      _active = null;
      rethrow;
    }
  }

  void _handleBackendEvent(PlayerBackend source, PlaybackEvent event) {
    if (_closed || !identical(source, _active)) {
      return;
    }
    _position = event.position;
    _bufferedPosition = event.bufferedPosition;
    if (event.state != PlaybackState.failed) {
      _lastState = event.state;
    }

    final failure = event.failure;
    if (identical(source, _primary) &&
        !_fallbackUsed &&
        failure != null &&
        _eligibleForFallback(failure)) {
      _fallbackUsed = true;
      _switching ??= _switchToFallback(source).whenComplete(() {
        _switching = null;
      });
      return;
    }
    _emit(event.state, failure: failure);
  }

  Future<void> _switchToFallback(PlayerBackend source) async {
    final session = _session;
    if (session == null || !identical(_active, source)) {
      return;
    }
    final generation = ++_generation;
    final intent = _lastState == PlaybackState.paused
        ? PlaybackIntent.paused
        : PlaybackIntent.playing;
    final snapshot = PlaybackHandoffSnapshot(
      sessionId: session.sessionId,
      timelineMapIdentity: session.timelineMapIdentity,
      position: _position,
      intent: intent,
      sourceBackendId: source.backendId,
      targetBackendId: _fallback.backendId,
    );
    _lastHandoff = snapshot;
    _active = null;
    _emit(PlaybackState.buffering);

    try {
      await source.close();
      if (_closed || generation != _generation) {
        return;
      }
      await _requireAvailable(_fallback);
      if (_closed || generation != _generation) {
        return;
      }
      _active = _fallback;
      await _fallback.open(session);
      if (_closed || generation != _generation) {
        return;
      }
      if (snapshot.position > Duration.zero) {
        await _fallback.seek(snapshot.position);
      }
      if (snapshot.intent == PlaybackIntent.paused) {
        await _fallback.pause();
      }
    } on Object catch (error, stackTrace) {
      if (identical(_active, _fallback)) {
        try {
          await _fallback.close();
        } on Object {
          // Preserve the original handoff failure.
        }
      }
      _active = null;
      _events.addError(error, stackTrace);
      _emit(
        PlaybackState.failed,
        failure: PlaybackFailure(
          code: 'backend_fallback_failed',
          kind: PlaybackFailureKind.unsupported,
          stage: PlaybackErrorStage.player,
          retryable: false,
          shouldRefreshSession: false,
        ),
      );
    }
  }

  Future<void> _requireAvailable(PlayerBackend backend) async {
    if (backend is! PlayerBackendCapabilitySource) {
      return;
    }
    final capabilitySource = backend as PlayerBackendCapabilitySource;
    final capability = await capabilitySource.probe();
    if (capability.backendId != backend.backendId) {
      throw StateError(
        'Backend capability identity does not match its backend.',
      );
    }
    if (!capability.isAvailable) {
      throw UnsupportedError(
        '${backend.backendId} is not available: ${capability.code}',
      );
    }
  }

  bool _eligibleForFallback(PlaybackFailure failure) =>
      failure.kind == PlaybackFailureKind.decoder ||
      failure.kind == PlaybackFailureKind.renderer;

  Future<void> _closeActive() async {
    final active = _active;
    _active = null;
    _session = null;
    _switching = null;
    if (active != null) {
      await active.close();
    }
  }

  void _emit(PlaybackState state, {PlaybackFailure? failure}) {
    if (_events.isClosed) {
      return;
    }
    _events.add(
      PlaybackEvent(
        sequence: _sequence++,
        state: state,
        position: _position,
        bufferedPosition: _bufferedPosition,
        failure: failure,
      ),
    );
  }

  void _ensureOpen() {
    if (_closed) {
      throw StateError('SwitchablePlayerBackend is closed.');
    }
  }

  void _ensureActive() {
    _ensureOpen();
    if (_active == null) {
      throw StateError('No active player backend.');
    }
  }
}

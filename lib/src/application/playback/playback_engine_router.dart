import 'dart:async';

import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

final class PlaybackEngineRouter implements PlayerBackend {
  PlaybackEngineRouter({
    required Map<PlayerBackendKind, PlayerBackend> backends,
    required List<PlayerBackendKind> preference,
    this.automaticFallback = true,
  }) : _backends = Map<PlayerBackendKind, PlayerBackend>.unmodifiable(backends),
       _preference = List<PlayerBackendKind>.unmodifiable(preference) {
    if (_preference.isEmpty) {
      throw ArgumentError.value(preference, 'preference', 'Must not be empty.');
    }
    if (_preference.toSet().length != _preference.length) {
      throw ArgumentError.value(
        preference,
        'preference',
        'Backend preference must not contain duplicates.',
      );
    }
    for (final kind in _preference) {
      if (!_backends.containsKey(kind)) {
        throw ArgumentError.value(
          kind,
          'preference',
          'Every preferred backend requires an implementation.',
        );
      }
    }
  }

  final Map<PlayerBackendKind, PlayerBackend> _backends;
  final List<PlayerBackendKind> _preference;
  final bool automaticFallback;
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final Set<PlayerBackendKind> _attempted = <PlayerBackendKind>{};
  final PlaybackErrorBoundary _errorBoundary = const PlaybackErrorBoundary();

  StreamSubscription<PlaybackEvent>? _backendSubscription;
  PlayerBackend? _activeBackend;
  PlaybackSession? _session;
  PlayerPlaybackState _state = PlayerPlaybackState(isPlaying: false);
  int _eventSequence = 0;
  int _generation = 0;
  int _operation = 0;
  bool _handoffPending = false;
  PlaybackFailure? _pendingHandoffFailure;
  bool _fallbackPending = false;
  int _automaticFallbackCount = 0;

  @override
  String get backendId => 'playback-engine-router';

  @override
  PlayerBackendKind get kind => _activeBackend?.kind ?? _preference.first;

  PlayerBackendKind? get activeKind => _activeBackend?.kind;

  PlayerPlaybackState get state => _state;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async {
    for (final kind in _preference) {
      final backend = _backends[kind]!;
      try {
        final availability = await backend.probe();
        if (availability.isAvailable) {
          return const PlayerBackendAvailability.available(
            'playback-engine-router',
          );
        }
      } on Object {
        // A single backend probe must not prevent probing the remaining order.
      }
    }
    return const PlayerBackendAvailability.unavailable(
      'playback-engine-router',
      reasonCode: 'no_backend_available',
    );
  }

  @override
  Future<void> open(PlaybackSession session) async {
    final operation = ++_operation;
    await _detachActiveBackend();
    _attempted.clear();
    _session = session;
    _state = PlayerPlaybackState(
      isPlaying: true,
      timelineMapIdentity: session.timelineMapIdentity,
    );
    _fallbackPending = false;
    _automaticFallbackCount = 0;
    _pendingHandoffFailure = null;

    try {
      final result = await _openNextAvailable(operation);
      if (operation != _operation || !identical(_session, session)) {
        throw StateError('Playback open was superseded.');
      }
      if (result.failure case final failure?) {
        _session = null;
        _emitFailure(failure, session);
        throw PlaybackOperationException(failure);
      }
      if (!result.opened) {
        _session = null;
        final failure = _backendUnavailableFailure();
        _emitFailure(failure, session);
        throw PlaybackOperationException(failure);
      }
    } on Object {
      if (operation == _operation) {
        _session = null;
        await _detachActiveBackend();
      }
      rethrow;
    }
  }

  Future<void> switchBackend(PlayerBackendKind target) async {
    final session = _requireSession();
    if (_activeBackend?.kind == target) {
      return;
    }
    final backend = _backends[target];
    if (backend == null) {
      throw PlaybackOperationException(
        _backendUnavailableFailure(code: 'backend_not_registered'),
      );
    }
    final availability = await _probeBackend(backend);
    if (!availability.isAvailable) {
      throw PlaybackOperationException(
        _backendUnavailableFailure(code: 'backend_unavailable'),
      );
    }
    final operation = _operation;
    _validateRestoredTracks(session, _state);
    _attempted.add(target);
    await _switchTo(backend, session, operation);
  }

  @override
  Future<void> play() async {
    final backend = _requireBackend();
    await _runBackendOperation(backend.play);
    _state = _state.copyWith(isPlaying: true);
  }

  @override
  Future<void> pause() async {
    final backend = _requireBackend();
    await _runBackendOperation(backend.pause);
    _state = _state.copyWith(isPlaying: false);
  }

  @override
  Future<void> seek(Duration position) async {
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
    final session = _requireSession();
    final backend = _requireBackend();
    await _runBackendOperation(
      () => backend.seek(_toSanitized(session, position)),
    );
    _state = _state.copyWith(position: position);
  }

  @override
  Future<void> setVolume(double volume) async {
    if (!volume.isFinite || volume < 0 || volume > 1) {
      throw ArgumentError.value(volume, 'volume', 'Must be between 0 and 1.');
    }
    final backend = _requireBackend();
    await _runBackendOperation(() => backend.setVolume(volume));
    _state = _state.copyWith(volume: volume);
  }

  @override
  Future<void> setRate(double rate) async {
    if (!rate.isFinite || rate < 0.25 || rate > 4) {
      throw ArgumentError.value(rate, 'rate', 'Must be between 0.25 and 4.');
    }
    final backend = _requireBackend();
    await _runBackendOperation(() => backend.setRate(rate));
    _state = _state.copyWith(rate: rate);
  }

  @override
  Future<void> selectAudioTrack(String? trackId) async {
    final session = _requireSession();
    _verifyTrack(session.audioTracks, trackId, 'audio');
    final backend = _requireBackend();
    await _runBackendOperation(() => backend.selectAudioTrack(trackId));
    _state = _state.copyWith(
      audioTrack: trackId == null
          ? const PlayerTrackSelection.disabled()
          : PlayerTrackSelection.selected(trackId),
    );
  }

  @override
  Future<void> selectSubtitleTrack(String? trackId) async {
    final session = _requireSession();
    _verifyTrack(session.subtitles, trackId, 'subtitle');
    final backend = _requireBackend();
    await _runBackendOperation(() => backend.selectSubtitleTrack(trackId));
    _state = _state.copyWith(
      subtitleTrack: trackId == null
          ? const PlayerTrackSelection.disabled()
          : PlayerTrackSelection.selected(trackId),
    );
  }

  @override
  Future<void> close() async {
    ++_operation;
    _session = null;
    _attempted.clear();
    _fallbackPending = false;
    _automaticFallbackCount = 0;
    _handoffPending = false;
    _pendingHandoffFailure = null;
    _state = PlayerPlaybackState(isPlaying: false);
    await _detachActiveBackend();
  }

  Future<_OpenAttemptResult> _openNextAvailable(
    int operation, {
    bool allowAutomaticFallback = true,
  }) async {
    PlaybackFailure? lastFailure;
    var mayFallback = allowAutomaticFallback && _automaticFallbackCount == 0;
    for (final kind in _preference) {
      if (_attempted.contains(kind)) {
        continue;
      }
      final backend = _backends[kind]!;
      _attempted.add(kind);
      PlayerBackendAvailability availability;
      try {
        availability = await backend.probe();
      } on Object {
        continue;
      }
      if (!availability.isAvailable) {
        continue;
      }
      try {
        await _switchTo(backend, _requireSession(), operation);
        return const _OpenAttemptResult.opened();
      } on Object catch (error) {
        await _detachActiveBackend();
        if (operation != _operation) {
          return const _OpenAttemptResult.unavailable();
        }
        final stableError = _errorBoundary.exceptionFrom(
          error,
          stage: PlaybackErrorStage.player,
        );
        lastFailure = stableError.failure;
        if (!mayFallback || !_mayAutomaticallyFallback(stableError.failure)) {
          return _OpenAttemptResult.failed(stableError.failure);
        }
        _automaticFallbackCount += 1;
        mayFallback = false;
      }
    }
    return lastFailure == null
        ? const _OpenAttemptResult.unavailable()
        : _OpenAttemptResult.failed(lastFailure);
  }

  Future<void> _switchTo(
    PlayerBackend backend,
    PlaybackSession session,
    int operation,
  ) async {
    _validateRestoredTracks(session, _state);
    await _detachActiveBackend();
    if (operation != _operation || !identical(_session, session)) {
      throw StateError('Playback engine switch was superseded.');
    }

    final generation = ++_generation;
    _activeBackend = backend;
    _handoffPending = true;
    _pendingHandoffFailure = null;
    _backendSubscription = backend.events.listen(
      (event) => _onBackendEvent(event, generation, operation),
      onError: (Object error, StackTrace _) {
        _onBackendStreamError(error, generation, operation);
      },
    );

    try {
      await backend.open(session);
      if (operation != _operation ||
          generation != _generation ||
          !identical(_activeBackend, backend)) {
        throw StateError('Playback engine switch was superseded.');
      }
      if (_state.timelineMapIdentity != session.timelineMapIdentity) {
        throw StateError('Playback timeline identity changed during switch.');
      }
      await _applyStateToBackend(backend, session, _state);
      _handoffPending = false;
      final handoffFailure = _pendingHandoffFailure;
      _pendingHandoffFailure = null;
      if (handoffFailure != null) {
        throw PlaybackOperationException(handoffFailure);
      }
      _emit(
        PlaybackEvent(
          sequence: 0,
          state: _state.isPlaying
              ? PlaybackState.playing
              : PlaybackState.paused,
          position: _state.position,
          bufferedPosition: _state.bufferedPosition,
          volume: _state.volume,
          rate: _state.rate,
          audioTrackId: _state.audioTrack.id,
          subtitleTrackId: _state.subtitleTrack.id,
          timelineMapIdentity: session.timelineMapIdentity,
        ),
      );
    } on Object catch (error) {
      _handoffPending = false;
      _pendingHandoffFailure = null;
      await _detachActiveBackend();
      throw _errorBoundary.exceptionFrom(
        error,
        stage: PlaybackErrorStage.player,
      );
    }
  }

  Future<void> _applyStateToBackend(
    PlayerBackend backend,
    PlaybackSession session,
    PlayerPlaybackState state,
  ) async {
    _validateRestoredTracks(session, state);
    if (state.position > Duration.zero) {
      await backend.seek(_toSanitized(session, state.position));
    }
    await backend.setVolume(state.volume);
    await backend.setRate(state.rate);
    if (state.audioTrack.isSpecified) {
      await backend.selectAudioTrack(state.audioTrack.id);
    }
    if (state.subtitleTrack.isSpecified) {
      await backend.selectSubtitleTrack(state.subtitleTrack.id);
    }
    if (state.isPlaying) {
      await backend.play();
    } else {
      await backend.pause();
    }
  }

  void _onBackendEvent(PlaybackEvent event, int generation, int operation) {
    if (generation != _generation || operation != _operation) {
      return;
    }
    final session = _session;
    if (session == null || _activeBackend == null) {
      return;
    }
    final eventIdentity = event.timelineMapIdentity;
    if (event.state == PlaybackState.idle && eventIdentity == null) {
      return;
    }
    if (eventIdentity != session.timelineMapIdentity) {
      _emit(
        PlaybackEvent(
          sequence: 0,
          state: PlaybackState.failed,
          failure: PlaybackFailure(
            code: 'timeline_identity_mismatch',
            kind: PlaybackFailureKind.manifest,
            stage: PlaybackErrorStage.player,
            retryable: false,
            shouldRefreshSession: false,
          ),
          position: _state.position,
          bufferedPosition: _state.bufferedPosition,
          timelineMapIdentity: session.timelineMapIdentity,
        ),
      );
      unawaited(_detachActiveBackend().catchError((_) {}));
      return;
    }
    final trackFailure = _trackIdentityFailure(session, event);
    if (trackFailure != null) {
      if (_handoffPending) {
        _pendingHandoffFailure ??= trackFailure;
        return;
      }
      _state = _state.copyWith(isPlaying: false);
      _emitFailure(trackFailure, session);
      unawaited(_detachActiveBackend().catchError((_) {}));
      return;
    }
    if (_handoffPending) {
      if (event.failure case final failure?) {
        _pendingHandoffFailure ??= failure;
      }
      return;
    }

    final failure = event.failure;
    final willFallback = failure != null && _mayAutomaticallyFallback(failure);
    final originalPosition = _toOriginal(session, event.position);
    final originalBuffered = _toOriginal(session, event.bufferedPosition);
    _state = _state.copyWith(
      position: originalPosition,
      bufferedPosition: originalBuffered,
      isPlaying: switch (event.state) {
        PlaybackState.playing => true,
        PlaybackState.paused ||
        PlaybackState.ended ||
        PlaybackState.closed => false,
        PlaybackState.failed => willFallback ? _state.isPlaying : false,
        _ => _state.isPlaying,
      },
      volume: event.volume,
      rate: event.rate,
      audioTrack: event.audioTrackId == null
          ? null
          : PlayerTrackSelection.selected(event.audioTrackId!),
      subtitleTrack: event.subtitleTrackId == null
          ? null
          : PlayerTrackSelection.selected(event.subtitleTrackId!),
    );

    if (failure != null && willFallback) {
      if (!_fallbackPending) {
        _automaticFallbackCount += 1;
        _fallbackPending = true;
        unawaited(_fallback(operation, failure));
      }
      return;
    }

    _emit(
      PlaybackEvent(
        sequence: 0,
        state: event.state,
        position: originalPosition,
        bufferedPosition: originalBuffered,
        failure: failure,
        volume: event.volume,
        rate: event.rate,
        audioTrackId: event.audioTrackId,
        subtitleTrackId: event.subtitleTrackId,
        timelineMapIdentity: session.timelineMapIdentity,
      ),
    );
  }

  void _onBackendStreamError(Object error, int generation, int operation) {
    if (generation != _generation || operation != _operation) {
      return;
    }
    final session = _session;
    if (session == null) {
      return;
    }
    final failure = _errorBoundary.failureFrom(
      error,
      stage: PlaybackErrorStage.player,
    );
    if (_handoffPending) {
      _pendingHandoffFailure ??= failure;
      return;
    }
    final willFallback = _mayAutomaticallyFallback(failure);
    _state = _state.copyWith(
      isPlaying: willFallback ? _state.isPlaying : false,
    );
    if (willFallback) {
      _automaticFallbackCount += 1;
      _fallbackPending = true;
      unawaited(_fallback(operation, failure));
      return;
    }
    _emitFailure(failure, session);
    unawaited(_detachActiveBackend().catchError((_) {}));
  }

  void _emitFailure(PlaybackFailure failure, PlaybackSession session) {
    _emit(
      PlaybackEvent(
        sequence: 0,
        state: PlaybackState.failed,
        position: _state.position,
        bufferedPosition: _state.bufferedPosition,
        failure: failure,
        timelineMapIdentity: session.timelineMapIdentity,
      ),
    );
  }

  Future<void> _fallback(int operation, PlaybackFailure original) async {
    try {
      if (operation != _operation || _session == null) {
        return;
      }
      await _detachActiveBackend();
      if (operation != _operation || _session == null) {
        return;
      }
      final result = await _openNextAvailable(
        operation,
        allowAutomaticFallback: false,
      );
      if (!result.opened && operation == _operation && _session != null) {
        _emitFailure(result.failure ?? original, _session!);
      }
    } on Object catch (error) {
      if (operation == _operation && _session != null) {
        _emitFailure(
          _errorBoundary.failureFrom(error, stage: PlaybackErrorStage.player),
          _session!,
        );
      }
    } finally {
      if (operation == _operation) {
        _fallbackPending = false;
      }
    }
  }

  bool _mayAutomaticallyFallback(PlaybackFailure failure) =>
      automaticFallback &&
      _automaticFallbackCount == 0 &&
      !_fallbackPending &&
      (failure.kind == PlaybackFailureKind.decoder ||
          failure.kind == PlaybackFailureKind.renderer ||
          failure.kind == PlaybackFailureKind.unsupported);

  void _emit(PlaybackEvent event) {
    if (_events.isClosed) {
      return;
    }
    _events.add(
      PlaybackEvent(
        sequence: _eventSequence++,
        state: event.state,
        position: event.position,
        bufferedPosition: event.bufferedPosition,
        failure: event.failure,
        volume: event.volume,
        rate: event.rate,
        audioTrackId: event.audioTrackId,
        subtitleTrackId: event.subtitleTrackId,
        timelineMapIdentity: event.timelineMapIdentity,
      ),
    );
  }

  Future<void> _detachActiveBackend() async {
    final subscription = _backendSubscription;
    final backend = _activeBackend;
    _backendSubscription = null;
    _activeBackend = null;
    _handoffPending = false;
    _pendingHandoffFailure = null;
    ++_generation;
    await subscription?.cancel();
    if (backend != null) {
      await _runBackendOperation(backend.close);
    }
  }

  PlaybackSession _requireSession() {
    final session = _session;
    if (session == null) {
      throw StateError('No active PlaybackSession.');
    }
    return session;
  }

  PlayerBackend _requireBackend() {
    final backend = _activeBackend;
    if (backend == null) {
      throw StateError('No active playback backend.');
    }
    return backend;
  }

  Future<PlayerBackendAvailability> _probeBackend(PlayerBackend backend) async {
    try {
      return await backend.probe();
    } on Object catch (error) {
      throw _errorBoundary.exceptionFrom(
        error,
        stage: PlaybackErrorStage.player,
      );
    }
  }

  Future<void> _runBackendOperation(Future<void> Function() operation) async {
    try {
      await operation();
    } on Object catch (error) {
      throw _errorBoundary.exceptionFrom(
        error,
        stage: PlaybackErrorStage.player,
      );
    }
  }
}

final class _OpenAttemptResult {
  const _OpenAttemptResult.opened() : opened = true, failure = null;

  const _OpenAttemptResult.unavailable() : opened = false, failure = null;

  const _OpenAttemptResult.failed(this.failure) : opened = false;

  final bool opened;
  final PlaybackFailure? failure;
}

void _validateRestoredTracks(
  PlaybackSession session,
  PlayerPlaybackState state,
) {
  _validateRestoredTrack(session.audioTracks, state.audioTrack, 'audio');
  _validateRestoredTrack(session.subtitles, state.subtitleTrack, 'subtitle');
}

void _validateRestoredTrack(
  Iterable<MediaTrack> tracks,
  PlayerTrackSelection selection,
  String type,
) {
  if (!selection.isSpecified || selection.id == null) {
    return;
  }
  if (!tracks.any((track) => track.id == selection.id && track.uri == null)) {
    throw StateError('Cannot restore an authoritative $type track.');
  }
}

void _verifyTrack(Iterable<MediaTrack> tracks, String? id, String type) {
  if (id == null) {
    return;
  }
  if (!tracks.any((track) => track.id == id && track.uri == null)) {
    throw StateError('Requested $type track is unavailable.');
  }
}

PlaybackFailure? _trackIdentityFailure(
  PlaybackSession session,
  PlaybackEvent event,
) {
  if (!_isAuthoritativeTrackId(session.audioTracks, event.audioTrackId) ||
      !_isAuthoritativeTrackId(session.subtitles, event.subtitleTrackId)) {
    return PlaybackFailure(
      code: 'track_identity_mismatch',
      kind: PlaybackFailureKind.unknown,
      stage: PlaybackErrorStage.player,
      retryable: false,
      shouldRefreshSession: false,
    );
  }
  return null;
}

bool _isAuthoritativeTrackId(Iterable<MediaTrack> tracks, String? id) =>
    id == null || tracks.any((track) => track.id == id && track.uri == null);

Duration _toSanitized(PlaybackSession session, Duration original) {
  final timeline = session.adRemovalPlan.timeline;
  if (timeline.originalDuration == Duration.zero &&
      timeline.sanitizedDuration == Duration.zero) {
    return original;
  }
  return timeline.toSanitized(original);
}

Duration _toOriginal(PlaybackSession session, Duration sanitized) {
  final timeline = session.adRemovalPlan.timeline;
  if (timeline.originalDuration == Duration.zero &&
      timeline.sanitizedDuration == Duration.zero) {
    return sanitized;
  }
  final bounded = sanitized > timeline.sanitizedDuration
      ? timeline.sanitizedDuration
      : sanitized;
  return timeline.toOriginal(bounded);
}

PlaybackFailure _backendUnavailableFailure({
  String code = 'backend_unavailable',
}) => PlaybackFailure(
  code: code,
  kind: PlaybackFailureKind.unsupported,
  stage: PlaybackErrorStage.player,
  retryable: false,
  shouldRefreshSession: false,
);

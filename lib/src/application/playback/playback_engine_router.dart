import 'dart:async';

import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
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

    final opened = await _openNextAvailable(operation);
    if (!opened) {
      _session = null;
      final failure = _backendUnavailableFailure();
      _emit(
        PlaybackEvent(
          sequence: 0,
          state: PlaybackState.failed,
          failure: failure,
          timelineMapIdentity: session.timelineMapIdentity,
        ),
      );
      throw UnsupportedError('No playback backend is available.');
    }
  }

  Future<void> switchBackend(PlayerBackendKind target) async {
    final session = _requireSession();
    if (_activeBackend?.kind == target) {
      return;
    }
    final backend = _backends[target];
    if (backend == null) {
      throw UnsupportedError('Requested playback backend is not registered.');
    }
    final availability = await backend.probe();
    if (!availability.isAvailable) {
      throw UnsupportedError('Requested playback backend is unavailable.');
    }
    final operation = _operation;
    _attempted.add(target);
    await _switchTo(backend, session, operation);
  }

  @override
  Future<void> play() async {
    final backend = _requireBackend();
    await backend.play();
    _state = _state.copyWith(isPlaying: true);
  }

  @override
  Future<void> pause() async {
    final backend = _requireBackend();
    await backend.pause();
    _state = _state.copyWith(isPlaying: false);
  }

  @override
  Future<void> seek(Duration position) async {
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
    final session = _requireSession();
    final backend = _requireBackend();
    await backend.seek(_toSanitized(session, position));
    _state = _state.copyWith(position: position);
  }

  @override
  Future<void> setVolume(double volume) async {
    if (!volume.isFinite || volume < 0 || volume > 1) {
      throw ArgumentError.value(volume, 'volume', 'Must be between 0 and 1.');
    }
    final backend = _requireBackend();
    await backend.setVolume(volume);
    _state = _state.copyWith(volume: volume);
  }

  @override
  Future<void> setRate(double rate) async {
    if (!rate.isFinite || rate < 0.25 || rate > 4) {
      throw ArgumentError.value(rate, 'rate', 'Must be between 0.25 and 4.');
    }
    final backend = _requireBackend();
    await backend.setRate(rate);
    _state = _state.copyWith(rate: rate);
  }

  @override
  Future<void> selectAudioTrack(String? trackId) async {
    final session = _requireSession();
    _verifyTrack(session.audioTracks, trackId, 'audio');
    final backend = _requireBackend();
    await backend.selectAudioTrack(trackId);
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
    await backend.selectSubtitleTrack(trackId);
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

  Future<bool> _openNextAvailable(int operation) async {
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
        return true;
      } on Object {
        await _detachActiveBackend();
        if (operation != _operation) {
          return false;
        }
      }
    }
    return false;
  }

  Future<void> _switchTo(
    PlayerBackend backend,
    PlaybackSession session,
    int operation,
  ) async {
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
      onError: (Object error, StackTrace stackTrace) {
        if (generation == _generation && operation == _operation) {
          _events.addError(StateError('Playback backend event stream failed.'));
        }
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
        if (_mayAutomaticallyFallback(handoffFailure)) {
          _automaticFallbackCount += 1;
          _fallbackPending = true;
          unawaited(_fallback(operation, handoffFailure));
          return;
        }
        _emitFailure(handoffFailure, session);
        return;
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
    } on Object {
      _handoffPending = false;
      _pendingHandoffFailure = null;
      await _detachActiveBackend();
      rethrow;
    }
  }

  Future<void> _applyStateToBackend(
    PlayerBackend backend,
    PlaybackSession session,
    PlayerPlaybackState state,
  ) async {
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
      unawaited(_detachActiveBackend());
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
      final opened = await _openNextAvailable(operation);
      if (!opened && operation == _operation && _session != null) {
        _emit(
          PlaybackEvent(
            sequence: 0,
            state: PlaybackState.failed,
            position: _state.position,
            bufferedPosition: _state.bufferedPosition,
            failure: original,
            timelineMapIdentity: _session!.timelineMapIdentity,
          ),
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
      await backend.close();
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
}

void _verifyTrack(Iterable<MediaTrack> tracks, String? id, String type) {
  if (id == null) {
    return;
  }
  if (!tracks.any((track) => track.id == id)) {
    throw StateError('Requested $type track is unavailable.');
  }
}

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

PlaybackFailure _backendUnavailableFailure() => PlaybackFailure(
  code: 'backend_unavailable',
  kind: PlaybackFailureKind.unsupported,
  stage: PlaybackErrorStage.player,
  retryable: false,
  shouldRefreshSession: false,
);

// Public constructor names intentionally differ from private implementation
// fields so the service remains readable to callers.
// ignore_for_file: prefer_initializing_formals

import 'dart:async';

import '../../domain/models/bangumi_episode_target.dart';
import '../../domain/models/playback_events.dart';
import '../../domain/models/playback_session.dart';
import '../../domain/models/source_identity.dart';
import '../../domain/models/watch_progress.dart';
import '../../domain/models/bangumi_sync_models.dart';
import '../../domain/repositories/bangumi_local_store.dart';
import '../../domain/repositories/watch_history_repository.dart';
import '../../domain/services/watch_progress_policy.dart';

/// Connects one authoritative PlaybackSession event stream to local progress.
///
/// The service is deliberately above the player ports. A player/backend only
/// emits typed events; this class owns throttling, completion and the existing
/// Bangumi queue handoff.
final class PlaybackProgressService {
  PlaybackProgressService({
    required WatchHistoryRepository history,
    this.bangumi,
    this.policy = const WatchProgressPolicy(),
    DateTime Function()? clock,
    this.checkpointInterval = const Duration(seconds: 10),
    this.checkpointDelta = const Duration(seconds: 5),
  }) : _history = history,
       _clock = clock ?? DateTime.now {
    if (checkpointInterval.isNegative || checkpointDelta.isNegative) {
      throw ArgumentError('Progress checkpoint bounds must not be negative.');
    }
  }

  final WatchHistoryRepository _history;
  final BangumiLocalStore? bangumi;
  final WatchProgressPolicy policy;
  final DateTime Function() _clock;
  final Duration checkpointInterval;
  final Duration checkpointDelta;

  PlaybackProgressBinding? _active;

  Future<PlaybackProgressBinding> bind({
    required PlaybackSession session,
    required Stream<PlaybackEvent> events,
    Duration? duration,
    String? playerBackendId,
    BangumiEpisodeTarget? bangumiEpisode,
  }) async {
    if (duration?.isNegative == true) {
      throw ArgumentError.value(duration, 'duration', 'Must not be negative.');
    }
    await _active?.close();
    final existing = await _history.findByIdentity(session.episode);
    final binding = PlaybackProgressBinding._(
      service: this,
      session: session,
      history: _history,
      bangumi: bangumi,
      policy: policy,
      clock: _clock,
      checkpointInterval: checkpointInterval,
      checkpointDelta: checkpointDelta,
      durationHint: duration,
      playerBackendId: playerBackendId,
      bangumiEpisode: bangumiEpisode,
      existing: existing,
    );
    _active = binding;
    await binding._attach(events);
    return binding;
  }

  Future<Duration> resumePosition(SourceEpisodeIdentity identity) async {
    final progress = await _history.findByIdentity(identity);
    return policy.resumePosition(progress);
  }

  Future<void> close() async {
    await _active?.close();
  }

  void _released(PlaybackProgressBinding binding) {
    if (identical(_active, binding)) _active = null;
  }
}

final class PlaybackProgressBinding {
  PlaybackProgressBinding._({
    required PlaybackProgressService service,
    required PlaybackSession session,
    required WatchHistoryRepository history,
    required BangumiLocalStore? bangumi,
    required WatchProgressPolicy policy,
    required DateTime Function() clock,
    required Duration checkpointInterval,
    required Duration checkpointDelta,
    required Duration? durationHint,
    required String? playerBackendId,
    required BangumiEpisodeTarget? bangumiEpisode,
    required WatchProgress? existing,
  }) : _service = service,
       session = session,
       _history = history,
       _bangumi = bangumi,
       _policy = policy,
       _clock = clock,
       _checkpointInterval = checkpointInterval,
       _checkpointDelta = checkpointDelta,
       _durationHint = durationHint,
       _playerBackendId = playerBackendId,
       _bangumiEpisode = bangumiEpisode,
       _existing = existing,
       _persisted = existing,
       _latest = existing,
       _accountAtOpen = bangumi?.activeAccountId,
       _progressId = _progressIdFor(session.episode);

  final PlaybackProgressService _service;
  final PlaybackSession session;
  final WatchHistoryRepository _history;
  final BangumiLocalStore? _bangumi;
  final WatchProgressPolicy _policy;
  final DateTime Function() _clock;
  final Duration _checkpointInterval;
  final Duration _checkpointDelta;
  final Duration? _durationHint;
  final String? _playerBackendId;
  final BangumiEpisodeTarget? _bangumiEpisode;
  final WatchProgress? _existing;
  final String? _accountAtOpen;
  final String _progressId;

  StreamSubscription<PlaybackEvent>? _subscription;
  Future<void> _eventTail = Future<void>.value();
  WatchProgress? _persisted;
  WatchProgress? _latest;
  DateTime? _lastPersistedAt;
  Duration? _lastPersistedPosition;
  int? _lastAcceptedSequence;
  bool _closed = false;
  bool _completionIntentAttempted = false;
  bool _completionIntentQueued = false;
  String? _lastPersistenceErrorCode;
  String? _lastBangumiErrorCode;

  Duration get initialResumePosition =>
      _policy.resumePosition(_progressForPolicy(_existing));

  WatchProgress? get latestProgress => _latest;

  String? get lastPersistenceErrorCode => _lastPersistenceErrorCode;

  String? get lastBangumiErrorCode => _lastBangumiErrorCode;

  /// True means a local queue intent is known to exist, not that Bangumi has
  /// already confirmed the mutation remotely.
  bool get completionIntentQueued => _completionIntentQueued;

  Future<void> _attach(Stream<PlaybackEvent> events) async {
    _persisted = _existing;
    _lastPersistedAt = _existing?.updatedAt;
    _lastPersistedPosition = _existing?.position;
    _subscription = events.listen(
      _receiveEvent,
      onError: (Object _, StackTrace _) {
        _lastPersistenceErrorCode = 'playback_event_stream_failed';
      },
    );
    // Local completion and the Bangumi queue are durable in separate stores.
    // If the process stopped after the local write but before the queue write,
    // binding the same episode must re-materialize the missing watched intent.
    // This is deliberately performed before the next player event so replay
    // and reconnect can repair the interrupted handoff without a second sync
    // engine or a watched=false side effect.
    if (_existing?.isCompleted == true) {
      await _queueCompletedBangumiIntent();
    }
  }

  void _receiveEvent(PlaybackEvent event) {
    if (_closed) return;
    _eventTail = _eventTail.then((_) async {
      try {
        await _consumeEvent(event);
      } on Object {
        // Player progress must not crash playback. The stable code is
        // inspectable by the application/UI without leaking storage details.
        _lastPersistenceErrorCode = 'watch_history_persist_failed';
      }
    });
  }

  Future<void> _consumeEvent(PlaybackEvent event) async {
    if (_closed ||
        event.sessionId == null ||
        event.sessionId != session.sessionId ||
        event.timelineMapIdentity != session.timelineMapIdentity) {
      return;
    }
    final previousSequence = _lastAcceptedSequence;
    if (previousSequence != null && event.sequence <= previousSequence) {
      return;
    }
    _lastAcceptedSequence = event.sequence;

    final previous = _latest;
    final wasCompleted = previous?.isCompleted ?? false;
    final duration = _effectiveDuration(event.duration);
    final position = _policy.clampPosition(event.position, duration);
    final isCompleted =
        wasCompleted ||
        _policy.isAtCompletionThreshold(position: position, duration: duration);
    final candidate = WatchProgress.sanitized(
      progressId: _progressId,
      sourceId: session.episode.sourceId,
      lineId: session.episode.lineId,
      subjectId: session.episode.subjectId,
      episodeId: session.episode.episodeId,
      position: position,
      duration: duration,
      isCompleted: isCompleted,
      updatedAt: _clock().toUtc(),
      playerBackendId: _playerBackendId,
      timelineMapId: session.timelineMapIdentity,
    );
    _latest = candidate;

    final completedTransition = !wasCompleted && isCompleted;
    final lifecycleCheckpoint = switch (event.state) {
      PlaybackState.paused ||
      PlaybackState.ended ||
      PlaybackState.closed ||
      PlaybackState.failed => true,
      _ => false,
    };
    if (!_shouldPersist(
      candidate,
      lifecycleCheckpoint: lifecycleCheckpoint,
      completedTransition: completedTransition,
    )) {
      return;
    }

    final saved = await _persist(candidate);
    if (!saved || !completedTransition) return;
    await _queueCompletedBangumiIntent();
  }

  bool _shouldPersist(
    WatchProgress candidate, {
    required bool lifecycleCheckpoint,
    required bool completedTransition,
  }) {
    if (_persisted?.isCompleted == true && candidate.isCompleted) {
      return false;
    }
    if (_persisted == null) {
      return completedTransition ||
          lifecycleCheckpoint &&
              _policy.isMeaningful(
                position: candidate.position,
                duration: candidate.duration,
              ) ||
          _policy.isMeaningful(
            position: candidate.position,
            duration: candidate.duration,
          );
    }
    if (_sameProgress(_persisted!, candidate)) return false;
    if (lifecycleCheckpoint || completedTransition) return true;

    final previousPosition = _lastPersistedPosition;
    if (previousPosition != null &&
        _absoluteDifference(previousPosition, candidate.position) >=
            _checkpointDelta) {
      return true;
    }
    final previousAt = _lastPersistedAt;
    if (previousAt != null) {
      final now = candidate.updatedAt;
      if (!now.isBefore(previousAt.add(_checkpointInterval))) return true;
    }
    return false;
  }

  Future<bool> _persist(WatchProgress candidate) async {
    final safe = WatchProgress.sanitized(
      progressId: candidate.progressId,
      sourceId: candidate.sourceId,
      lineId: candidate.lineId,
      subjectId: candidate.subjectId,
      episodeId: candidate.episodeId,
      position: candidate.position,
      duration: candidate.duration,
      isCompleted: candidate.isCompleted,
      updatedAt: _clock().toUtc(),
      playerBackendId: candidate.playerBackendId,
      timelineMapId: candidate.timelineMapId,
    );
    try {
      await _history.save(safe);
    } on Object {
      _lastPersistenceErrorCode = 'watch_history_persist_failed';
      return false;
    }
    _persisted = safe;
    _latest = safe;
    _lastPersistedAt = safe.updatedAt;
    _lastPersistedPosition = safe.position;
    return true;
  }

  Future<void> _queueCompletedBangumiIntent() async {
    if (_completionIntentAttempted) return;
    _completionIntentAttempted = true;
    final store = _bangumi;
    if (store == null) return;

    final target = _bangumiEpisode;
    if (target == null) {
      _lastBangumiErrorCode = 'subject_mapping_required';
      return;
    }
    final accountId = _accountAtOpen;
    if (accountId == null || store.activeAccountId != accountId) {
      _lastBangumiErrorCode = 'account_changed';
      return;
    }

    try {
      final pending = await store.pendingOperations(forceRetry: true);
      if (_hasWatchedIntent(pending, target, accountId)) {
        _completionIntentQueued = true;
        return;
      }
      final conflicts = await store.conflictOperations();
      if (_hasWatchedIntent(conflicts, target, accountId)) {
        _lastBangumiErrorCode = 'remote_revision_conflict';
        return;
      }
      final localEpisodeState = await store.loadEpisodeProgress(
        target.subjectId,
      );
      if (localEpisodeState?.watchedEpisodeIds.contains(target.episodeId) ==
          true) {
        _completionIntentQueued = true;
        return;
      }
      await store.setEpisodeWatched(target.subjectId, target.episodeId, true);
      _completionIntentQueued = true;
    } on Object catch (error) {
      _lastBangumiErrorCode = _stableBangumiErrorCode(error);
    }
  }

  bool _hasWatchedIntent(
    Iterable<BangumiPendingOperation> operations,
    BangumiEpisodeTarget target,
    String accountId,
  ) {
    for (final operation in operations) {
      if (operation.accountId == accountId &&
          operation.subjectId == target.subjectId &&
          operation.episodeId == target.episodeId &&
          operation.watched == true) {
        return true;
      }
    }
    return false;
  }

  /// Allows an authenticated/mapped application flow to retry a durable
  /// watched intent without making player callbacks a second sync engine.
  Future<void> retryBangumiIntent() async {
    if (_closed || _latest?.isCompleted != true) return;
    _completionIntentAttempted = false;
    await _queueCompletedBangumiIntent();
  }

  Future<void> flush() async {
    while (true) {
      final pending = _eventTail;
      await pending;
      if (identical(pending, _eventTail)) return;
    }
  }

  Future<void> close() async {
    if (_closed) return;
    await flush();
    final latest = _latest;
    final canCreateRow =
        latest != null &&
        (_persisted != null ||
            latest.isCompleted ||
            _policy.isMeaningful(
              position: latest.position,
              duration: latest.duration,
            ));
    if (latest != null &&
        canCreateRow &&
        !_sameProgress(_persisted, latest) &&
        !(_persisted?.isCompleted == true && latest.isCompleted)) {
      final saved = await _persist(latest);
      if (saved && latest.isCompleted && !_completionIntentAttempted) {
        await _queueCompletedBangumiIntent();
      }
    }
    _closed = true;
    await _subscription?.cancel();
    _subscription = null;
    _service._released(this);
  }

  Duration _effectiveDuration(Duration? eventDuration) {
    if (eventDuration != null && eventDuration != Duration.zero) {
      return _policy.sanitizeDuration(eventDuration);
    }
    final hint = _durationHint;
    if (hint != null && hint != Duration.zero) {
      return _policy.sanitizeDuration(hint);
    }
    return _policy.sanitizeDuration(_latest?.duration ?? Duration.zero);
  }

  WatchProgress? _progressForPolicy(WatchProgress? value) {
    if (value == null) return null;
    return WatchProgress.sanitized(
      progressId: value.progressId,
      sourceId: value.sourceId,
      lineId: value.lineId,
      subjectId: value.subjectId,
      episodeId: value.episodeId,
      position: value.position,
      duration: _durationHint == null || _durationHint == Duration.zero
          ? value.duration
          : _durationHint,
      isCompleted: value.isCompleted,
      updatedAt: value.updatedAt,
      playerBackendId: value.playerBackendId,
      timelineMapId: value.timelineMapId,
    );
  }

  static String _stableBangumiErrorCode(Object error) {
    final text = error.toString();
    if (text.contains('episode_not_found')) return 'episode_not_found';
    if (text.contains('subject_mapping_required')) {
      return 'subject_mapping_required';
    }
    if (text.contains('reauth_required')) return 'reauth_required';
    if (text.contains('account_mismatch')) return 'account_mismatch';
    return 'bangumi_intent_failed';
  }

  static bool _sameProgress(WatchProgress? left, WatchProgress right) {
    if (left == null) return false;
    return left.sourceId == right.sourceId &&
        left.lineId == right.lineId &&
        left.subjectId == right.subjectId &&
        left.episodeId == right.episodeId &&
        left.position == right.position &&
        left.duration == right.duration &&
        left.isCompleted == right.isCompleted &&
        left.playerBackendId == right.playerBackendId &&
        left.timelineMapId == right.timelineMapId;
  }

  static Duration _absoluteDifference(Duration left, Duration right) {
    final value = left - right;
    return value.isNegative ? -value : value;
  }

  static String _progressIdFor(SourceEpisodeIdentity identity) =>
      'progress:${identity.sourceId}:${identity.lineId}:'
      '${identity.subjectId}:${identity.episodeId}';
}

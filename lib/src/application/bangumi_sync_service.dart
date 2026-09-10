// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'dart:math';

import '../domain/models/bangumi_models.dart';
import '../domain/models/bangumi_sync_models.dart';
import '../domain/repositories/bangumi_local_store.dart';
import '../domain/services/bangumi_ports.dart';

final class BangumiSyncResult {
  const BangumiSyncResult({
    required this.processed,
    required this.conflicts,
    this.retryWaiting = 0,
    this.blocked = 0,
    this.errorCode,
  });

  final int processed;
  final int conflicts;
  final int retryWaiting;
  final int blocked;
  final String? errorCode;

  /// Compatibility alias for callers written against the v1.0.6 result.
  /// Recoverable errors are no longer reported as terminal failures.
  @Deprecated('Use blocked instead.')
  int get failed => blocked;
}

final class BangumiSyncService {
  BangumiSyncService({
    required BangumiClient client,
    required BangumiLocalStore store,
    required BangumiAuthSession Function() sessionProvider,
    DateTime Function()? clock,
    this.maxAttempts = 4,
    Future<void> Function(Duration)? delay,
    double Function()? retryJitter,
  }) : _client = client,
       _store = store,
       _sessionProvider = sessionProvider,
       _clock = clock ?? DateTime.now,
       _delay = delay ?? ((duration) => Future<void>.delayed(duration)),
       _retryJitter = retryJitter;

  final BangumiClient _client;
  final BangumiLocalStore _store;
  final BangumiAuthSession Function() _sessionProvider;
  final DateTime Function() _clock;
  final Future<void> Function(Duration) _delay;
  final double Function()? _retryJitter;
  final Random _random = Random.secure();

  /// Kept as a foreground-execution tuning knob for API compatibility. It no
  /// longer turns a retryable operation into a terminal queue row.
  final int maxAttempts;

  static const _verificationDelays = <Duration>[
    Duration(milliseconds: 250),
    Duration(milliseconds: 500),
    Duration(seconds: 1),
    Duration(seconds: 2),
  ];

  Future<BangumiSyncResult>? _singleFlight;

  /// Manual synchronization forces retryWaiting rows to be considered even
  /// before their automatic backoff deadline. Automatic callers can pass
  /// [forceRetry] as false to select only due work.
  Future<BangumiSyncResult> syncNow({bool forceRetry = true}) {
    final existing = _singleFlight;
    if (existing != null) return existing;
    final future = _sync(forceRetry: forceRetry);
    _singleFlight = future;
    future.then(
      (_) {
        if (identical(_singleFlight, future)) _singleFlight = null;
      },
      onError: (Object error, StackTrace stackTrace) {
        if (identical(_singleFlight, future)) _singleFlight = null;
      },
    );
    return future;
  }

  Future<BangumiSyncResult> _sync({required bool forceRetry}) async {
    final session = _sessionProvider();
    final active = await _store.loadActiveAccount();
    if (active == null || active.accountId != session.accountId) {
      throw const BangumiApiException(code: 'account_mismatch');
    }
    final blockedBefore = await _store.blockedCount();
    final operations = await _store.pendingOperations(
      now: _clock(),
      forceRetry: forceRetry,
    );
    var processed = 0;
    var conflicts = 0;
    var retryWaiting = 0;
    var blocked = max(0, (await _store.blockedCount()) - blockedBefore);
    String? lastErrorCode;
    final rebasedRevisions = <String, String>{};

    for (final operation in operations) {
      BangumiRemoteState? latestRemote;
      try {
        latestRemote = await _readRemote(
          operation.subjectId,
          session.accountId,
        );
        final baseRevision =
            rebasedRevisions[operation.subjectId] ??
            operation.baseRemoteRevision;

        // The remote may have completed this exact desired change already,
        // including after an earlier mutation response was lost. Complete it
        // without issuing a duplicate mutation.
        if (_matchesDesired(latestRemote, operation)) {
          await _completeConfirmed(operation, latestRemote);
          rebasedRevisions[operation.subjectId] = latestRemote.remoteRevision;
          processed++;
          continue;
        }

        // A revision change is safe to merge only when the field owned by this
        // operation is still at its captured base value. A changed target
        // field is a real conflict; unrelated remote changes can be preserved.
        if (_hasIncompatibleRemoteChange(
          operation,
          latestRemote,
          baseRevision,
        )) {
          await _recordConflict(operation, latestRemote);
          conflicts++;
          continue;
        }

        await _apply(operation);
        final verified = await _verifyDesiredState(
          operation,
          session.accountId,
          initialRemote: latestRemote,
        );
        if (!_matchesDesired(verified, operation)) {
          await _recordConflict(operation, verified);
          conflicts++;
          continue;
        }

        await _completeConfirmed(operation, verified);
        rebasedRevisions[operation.subjectId] = verified.remoteRevision;
        processed++;
      } on BangumiApiException catch (error) {
        if (_isAuthenticationError(error.code)) {
          // Keep an auth failure retryable and visible. Once the user
          // re-authenticates, the same local mutation can be replayed.
          await _store.markRetry(
            operation,
            error.code,
            nextAttemptAt: _clock().toUtc(),
            statusCode: error.statusCode,
          );
          rethrow;
        }

        lastErrorCode = error.code;
        if (_isBlockedError(error)) {
          await _store.markBlocked(
            operation,
            error.code,
            statusCode: error.statusCode,
          );
          if (latestRemote != null) {
            // A blocked operation must stop acting as local-first state.
            await _store.applyRemoteState(latestRemote);
          }
          blocked++;
        } else {
          await _store.markRetry(
            operation,
            error.code,
            nextAttemptAt: _nextAttemptAt(operation.attempts + 1),
            statusCode: error.statusCode,
          );
          retryWaiting++;
        }
      } on BangumiPayloadException catch (error) {
        // Provider payload failures can be temporary (or fixed by a later
        // API response). They must not poison the queue permanently.
        lastErrorCode = error.code;
        await _store.markRetry(
          operation,
          error.code,
          nextAttemptAt: _nextAttemptAt(operation.attempts + 1),
        );
        retryWaiting++;
      } on Object {
        // Unknown failures are treated as bounded transient failures. The
        // diagnostic is deliberately stable and contains no exception text.
        lastErrorCode = 'sync_unknown_error';
        await _store.markRetry(
          operation,
          'sync_unknown_error',
          nextAttemptAt: _nextAttemptAt(operation.attempts + 1),
        );
        retryWaiting++;
      }
    }

    return BangumiSyncResult(
      processed: processed,
      conflicts: conflicts,
      retryWaiting: retryWaiting,
      blocked: blocked,
      errorCode: lastErrorCode,
    );
  }

  Future<BangumiRemoteState> _readRemote(
    String subjectId,
    String accountId,
  ) async {
    final remote = await _client.remoteState(subjectId);
    if (remote.accountId != accountId) {
      throw const BangumiApiException(code: 'account_mismatch');
    }
    if (remote.subjectId != subjectId) {
      throw const BangumiApiException(
        code: 'remote_state_mismatch',
        retryable: true,
      );
    }
    return remote;
  }

  Future<BangumiRemoteState> _verifyDesiredState(
    BangumiPendingOperation operation,
    String accountId, {
    required BangumiRemoteState initialRemote,
  }) async {
    late BangumiRemoteState last;
    for (var attempt = 0; attempt <= _verificationDelays.length; attempt++) {
      if (attempt > 0) await _delay(_verificationDelays[attempt - 1]);
      last = await _readRemote(operation.subjectId, accountId);
      if (_matchesDesired(last, operation)) return last;
      if (attempt == 0 && _targetChanged(initialRemote, last, operation)) {
        return last;
      }
    }
    return last;
  }

  static bool _targetChanged(
    BangumiRemoteState before,
    BangumiRemoteState after,
    BangumiPendingOperation operation,
  ) {
    switch (operation.kind) {
      case BangumiSyncOperationKind.collectionStatus:
        return before.status != after.status;
      case BangumiSyncOperationKind.episodeWatched:
        final episodeId = operation.episodeId;
        if (episodeId == null) return true;
        return before.watchedEpisodeIds.contains(episodeId) !=
            after.watchedEpisodeIds.contains(episodeId);
    }
  }

  bool _hasIncompatibleRemoteChange(
    BangumiPendingOperation operation,
    BangumiRemoteState remote,
    String? baseRevision,
  ) {
    if (baseRevision == null || baseRevision == remote.remoteRevision) {
      return false;
    }
    switch (operation.kind) {
      case BangumiSyncOperationKind.collectionStatus:
        final baseStatus = operation.baseCollectionStatus;
        return baseStatus == null || remote.status != baseStatus;
      case BangumiSyncOperationKind.episodeWatched:
        final episodeId = operation.episodeId;
        final baseWatched = operation.baseWatched;
        if (episodeId == null || baseWatched == null) return true;
        return remote.watchedEpisodeIds.contains(episodeId) != baseWatched;
    }
  }

  static bool _matchesDesired(
    BangumiRemoteState remote,
    BangumiPendingOperation operation,
  ) {
    switch (operation.kind) {
      case BangumiSyncOperationKind.collectionStatus:
        return operation.collectionStatus != null &&
            remote.status == operation.collectionStatus;
      case BangumiSyncOperationKind.episodeWatched:
        final episodeId = operation.episodeId;
        final watched = operation.watched;
        return episodeId != null &&
            watched != null &&
            remote.watchedEpisodeIds.contains(episodeId) == watched;
    }
  }

  static bool _isAuthenticationError(String code) =>
      code == 'auth_required' ||
      code == 'account_mismatch' ||
      code == 'reauth_required';

  static bool _isBlockedError(BangumiApiException error) {
    if (error.code == 'operation_payload_invalid' ||
        error.code == 'subject_mapping_required' ||
        error.code == 'episode_not_found' ||
        error.code == 'not_found' ||
        error.code == 'unsupported_request' ||
        error.code == 'response_too_large') {
      return true;
    }
    if (error.code.startsWith('http_4')) {
      return error.code != 'http_408' &&
          error.code != 'http_409' &&
          error.code != 'http_425';
    }
    return false;
  }

  DateTime _nextAttemptAt(int attempt) {
    final exponent = min(max(attempt - 1, 0), 8);
    final baseMillis = min(5 * 60 * 1000, 1000 * (1 << exponent));
    final requestedJitter = _retryJitter?.call() ?? _random.nextDouble();
    final jitter = requestedJitter.clamp(0.0, 1.0).toDouble();
    return _clock().toUtc().add(
      Duration(milliseconds: baseMillis + (baseMillis * jitter).round()),
    );
  }

  Future<void> _completeConfirmed(
    BangumiPendingOperation operation,
    BangumiRemoteState remote,
  ) async {
    await _store.reconcileAfterMutation(
      remote,
      completedOperationId: operation.operationId,
    );
    await _store.complete(operation);
  }

  Future<void> _recordConflict(
    BangumiPendingOperation operation,
    BangumiRemoteState remote,
  ) async {
    await _store.applyRemoteState(remote);
    await _store.markConflict(operation, remote);
  }

  Future<void> _apply(BangumiPendingOperation operation) async {
    switch (operation.kind) {
      case BangumiSyncOperationKind.collectionStatus:
        final status = operation.collectionStatus;
        if (status == null) {
          throw const BangumiApiException(code: 'operation_payload_invalid');
        }
        await _client.setCollectionStatus(operation.subjectId, status);
      case BangumiSyncOperationKind.episodeWatched:
        final episodeId = operation.episodeId;
        final watched = operation.watched;
        if (episodeId == null || watched == null) {
          throw const BangumiApiException(code: 'operation_payload_invalid');
        }
        await _client.setEpisodeWatched(
          operation.subjectId,
          episodeId,
          watched,
        );
    }
  }

  Future<void> adoptRemote(BangumiConflict conflict) =>
      _store.adoptRemote(conflict);

  Future<void> requeueLocal(BangumiConflict conflict) =>
      _store.requeueAgainstRevision(
        conflict.operation,
        conflict.remoteState.remoteRevision,
      );
}

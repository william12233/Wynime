// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import '../domain/models/bangumi_models.dart';
import '../domain/models/bangumi_sync_models.dart';
import '../domain/repositories/bangumi_local_store.dart';
import '../domain/services/bangumi_ports.dart';

final class BangumiSyncResult {
  const BangumiSyncResult({
    required this.processed,
    required this.conflicts,
    required this.failed,
    this.errorCode,
  });

  final int processed;
  final int conflicts;
  final int failed;
  final String? errorCode;
}

final class BangumiSyncService {
  BangumiSyncService({
    required BangumiClient client,
    required BangumiLocalStore store,
    required BangumiAuthSession Function() sessionProvider,
    DateTime Function()? clock,
    this.maxAttempts = 4,
  }) : _client = client,
       _store = store,
       _sessionProvider = sessionProvider,
       _clock = clock ?? DateTime.now;

  final BangumiClient _client;
  final BangumiLocalStore _store;
  final BangumiAuthSession Function() _sessionProvider;
  final DateTime Function() _clock;
  final int maxAttempts;
  Future<BangumiSyncResult>? _singleFlight;

  Future<BangumiSyncResult> syncNow() {
    final existing = _singleFlight;
    if (existing != null) return existing;
    final future = _sync();
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

  Future<BangumiSyncResult> _sync() async {
    final session = _sessionProvider();
    final active = await _store.loadActiveAccount();
    if (active == null || active.accountId != session.accountId) {
      throw const BangumiApiException(code: 'account_mismatch');
    }
    final operations = await _store.pendingOperations(now: _clock());
    var processed = 0;
    var conflicts = 0;
    var failed = 0;
    String? lastErrorCode;
    final rebasedRevisions = <String, String>{};
    for (final operation in operations) {
      try {
        final remote = await _client.remoteState(operation.subjectId);
        if (remote.accountId != session.accountId) {
          throw const BangumiApiException(code: 'account_mismatch');
        }
        final base =
            rebasedRevisions[operation.subjectId] ??
            operation.baseRemoteRevision;
        if (base != null && base != remote.remoteRevision) {
          await _store.markConflict(operation, remote);
          conflicts++;
          continue;
        }
        await _apply(operation);
        final refreshed = await _client.remoteState(operation.subjectId);
        if (refreshed.accountId != session.accountId ||
            refreshed.subjectId != operation.subjectId) {
          throw const BangumiApiException(code: 'account_mismatch');
        }
        final expected = _expectedState(remote, operation);
        if (refreshed.remoteRevision != expected.remoteRevision) {
          await _store.markConflict(operation, refreshed);
          conflicts++;
          continue;
        }
        await _store.reconcileAfterMutation(
          refreshed,
          completedOperationId: operation.operationId,
        );
        await _store.complete(operation);
        rebasedRevisions[operation.subjectId] = refreshed.remoteRevision;
        processed++;
      } on BangumiApiException catch (error) {
        if (_isAuthenticationError(error.code)) {
          // Keep an auth failure retryable and visible. Once the user
          // re-authenticates, the same local mutation can be replayed.
          await _store.markRetry(
            operation,
            error.code,
            exhausted: false,
            nextAttemptAt: _clock().toUtc(),
          );
          rethrow;
        }
        final attempt = operation.attempts + 1;
        final exhausted = !error.retryable || attempt >= maxAttempts;
        final delay = Duration(seconds: 1 << (attempt.clamp(0, 5)));
        await _store.markRetry(
          operation,
          error.code,
          exhausted: exhausted,
          nextAttemptAt: _clock().toUtc().add(delay),
        );
        lastErrorCode = error.code;
        failed++;
      } on BangumiPayloadException catch (error) {
        await _store.markRetry(
          operation,
          error.code,
          exhausted: true,
          nextAttemptAt: _clock().toUtc(),
        );
        lastErrorCode = error.code;
        failed++;
      } on Object {
        await _store.markRetry(
          operation,
          'sync_failed',
          exhausted: true,
          nextAttemptAt: _clock().toUtc(),
        );
        lastErrorCode = 'sync_failed';
        failed++;
      }
    }
    return BangumiSyncResult(
      processed: processed,
      conflicts: conflicts,
      failed: failed,
      errorCode: lastErrorCode,
    );
  }

  static bool _isAuthenticationError(String code) =>
      code == 'auth_required' || code == 'account_mismatch';

  static BangumiRemoteState _expectedState(
    BangumiRemoteState remote,
    BangumiPendingOperation operation,
  ) {
    var status = remote.status;
    final watched = {...remote.watchedEpisodeIds};
    switch (operation.kind) {
      case BangumiSyncOperationKind.collectionStatus:
        status = operation.collectionStatus;
      case BangumiSyncOperationKind.episodeWatched:
        final episodeId = operation.episodeId;
        final value = operation.watched;
        if (episodeId != null && value != null) {
          if (value) {
            watched.add(episodeId);
          } else {
            watched.remove(episodeId);
          }
        }
    }
    return BangumiRemoteState(
      accountId: remote.accountId,
      subjectId: remote.subjectId,
      status: status,
      watchedEpisodeIds: Set.unmodifiable(watched),
      remoteRevision: BangumiRemoteState.fingerprint(
        subjectId: remote.subjectId,
        status: status,
        watchedEpisodeIds: watched,
      ),
    );
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

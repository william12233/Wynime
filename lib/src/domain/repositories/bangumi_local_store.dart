import '../models/bangumi_models.dart';
import '../models/bangumi_sync_models.dart';
import 'bangumi_repository.dart' show BangumiEpisodeProgress;

abstract interface class BangumiLocalStore {
  String? get activeAccountId;

  Future<void> saveAccount(BangumiUserIdentity identity, {bool active = true});

  Future<BangumiAccountSnapshot?> loadActiveAccount();

  Future<void> setActiveAccount(String accountId);

  Future<void> deactivateActiveAccount();

  Future<void> cacheSchedule(List<BangumiScheduleEntry> entries);

  Future<List<BangumiScheduleEntry>> cachedSchedule();

  Future<DateTime?> cachedScheduleUpdatedAt();

  Future<void> cacheSubject(BangumiSubject subject);

  Future<void> cacheCollection(BangumiCollectionEntry collection);

  /// Reconciles one complete remote collection snapshot atomically.
  ///
  /// Rows absent from [collections] are removed from the cached collection
  /// view unless an unfinished local collection operation still owns that
  /// subject's local-first projection. Subject and episode metadata remain
  /// cached for detail and playback use.
  Future<void> reconcileRemoteCollections(
    List<BangumiCollectionEntry> collections,
  );

  Future<List<BangumiCollectionEntry>> cachedCollections();

  /// Returns collection states with an unfinished local-first operation,
  /// including subjects that are not yet present in remote membership.
  Future<List<BangumiCollectionEntry>> localFirstCollections();

  Future<void> cacheEpisodes(BangumiEpisodePage page);

  Future<void> cacheSubjectDetail(BangumiSubjectDetailSnapshot snapshot);

  Future<BangumiSubjectDetailSnapshot?> cachedSubjectDetail(String subjectId);

  Future<void> saveCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  );

  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  );

  Future<BangumiEpisodeProgress?> loadEpisodeProgress(String subjectId);

  Future<List<BangumiPendingOperation>> pendingOperations({
    DateTime? now,
    bool forceRetry = false,
  });

  Future<List<BangumiPendingOperation>> conflictOperations();

  Future<int> pendingCount();

  Future<int> blockedCount();

  /// Legacy compatibility count. New blocked records are exposed by
  /// [blockedCount], while v1.0.6 `failed` rows are migrated on open.
  Future<int> failedCount();

  Future<void> applyRemoteState(BangumiRemoteState state);

  /// Applies the observed remote state and rebases still-pending local
  /// operations after one mutation has been confirmed. This keeps multiple
  /// local-first changes for one subject from conflicting with themselves.
  Future<void> reconcileAfterMutation(
    BangumiRemoteState state, {
    required BangumiPendingOperation completedOperation,
  });

  Future<void> markConflict(
    BangumiPendingOperation operation,
    BangumiRemoteState remoteState,
  );

  Future<void> adoptRemote(BangumiConflict conflict);

  Future<void> requeueAgainstRevision(
    BangumiPendingOperation operation,
    String remoteRevision,
  );

  Future<void> markRetry(
    BangumiPendingOperation operation,
    String errorCode, {
    required DateTime nextAttemptAt,
    int? statusCode,
  });

  Future<void> markBlocked(
    BangumiPendingOperation operation,
    String errorCode, {
    int? statusCode,
  });

  /// Returns whether the operation still represented the same local intent
  /// when it was completed. A false result means a newer coalesced intent won
  /// the race and must remain queued.
  Future<bool> complete(BangumiPendingOperation operation);

  /// Returns valid, active-account operations blocked specifically by HTTP
  /// 415. Other blocked reasons are intentionally excluded.
  Future<List<BangumiPendingOperation>> recoverableHttp415Operations();

  /// Requeues one structurally valid HTTP-415 operation. Returns false when
  /// the persisted row is no longer eligible or belongs to another account.
  Future<bool> requeueHttp415(BangumiPendingOperation operation);

  Future<List<BangumiConflict>> conflicts();
}

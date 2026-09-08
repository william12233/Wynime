import '../models/bangumi_models.dart';
import '../models/bangumi_sync_models.dart';

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

  Future<List<BangumiCollectionEntry>> cachedCollections();

  /// Returns collection states with an unfinished local-first operation,
  /// including subjects that are not yet present in remote membership.
  Future<List<BangumiCollectionEntry>> localFirstCollections();

  Future<void> cacheEpisodes(BangumiEpisodePage page);

  Future<void> saveCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  );

  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  );

  Future<List<BangumiPendingOperation>> pendingOperations({DateTime? now});

  Future<List<BangumiPendingOperation>> conflictOperations();

  Future<int> pendingCount();

  Future<int> failedCount();

  Future<void> applyRemoteState(BangumiRemoteState state);

  /// Applies the observed remote state and rebases still-pending local
  /// operations after one mutation has been confirmed. This keeps multiple
  /// local-first changes for one subject from conflicting with themselves.
  Future<void> reconcileAfterMutation(
    BangumiRemoteState state, {
    required String completedOperationId,
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
    required bool exhausted,
    required DateTime nextAttemptAt,
  });

  Future<void> complete(BangumiPendingOperation operation);

  Future<List<BangumiConflict>> conflicts();
}

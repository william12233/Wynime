import 'bangumi_models.dart';

enum BangumiSyncOperationKind { collectionStatus, episodeWatched }

enum BangumiSyncOperationState { pending, retryWaiting, conflict, failed }

final class BangumiPendingOperation {
  const BangumiPendingOperation({
    required this.operationId,
    required this.accountId,
    required this.subjectId,
    required this.kind,
    required this.state,
    required this.attempts,
    required this.baseRemoteRevision,
    required this.createdAt,
    required this.updatedAt,
    this.episodeId,
    this.collectionStatus,
    this.watched,
    this.nextAttemptAt,
    this.lastErrorCode,
  });

  final String operationId;
  final String accountId;
  final String subjectId;
  final String? episodeId;
  final BangumiSyncOperationKind kind;
  final BangumiCollectionStatus? collectionStatus;
  final bool? watched;
  final String? baseRemoteRevision;
  final BangumiSyncOperationState state;
  final int attempts;
  final DateTime? nextAttemptAt;
  final String? lastErrorCode;
  final DateTime createdAt;
  final DateTime updatedAt;
}

final class BangumiConflict {
  const BangumiConflict({required this.operation, required this.remoteState});

  final BangumiPendingOperation operation;
  final BangumiRemoteState remoteState;
}

final class BangumiAccountSnapshot {
  const BangumiAccountSnapshot({
    required this.accountId,
    required this.username,
    this.nickname,
    this.avatarUrl,
    this.scheduleUpdatedAt,
    required this.isActive,
    required this.lastSeenAt,
  });

  final String accountId;
  final String username;
  final String? nickname;
  final Uri? avatarUrl;
  final DateTime? scheduleUpdatedAt;
  final bool isActive;
  final DateTime lastSeenAt;
}

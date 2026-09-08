// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'dart:convert';
import 'dart:math';

import 'package:drift/drift.dart';

import '../../domain/models/bangumi_models.dart';
import '../../domain/models/bangumi_sync_models.dart';
import '../../domain/repositories/bangumi_local_store.dart';
import '../../domain/repositories/bangumi_repository.dart';
import '../database/wynime_database.dart';

final class DriftBangumiLocalStore
    implements BangumiLocalStore, BangumiRepository {
  DriftBangumiLocalStore(
    this._database, {
    String? Function()? activeAccountProvider,
    DateTime Function()? clock,
  }) : _activeAccountProvider = activeAccountProvider,
       _clock = clock ?? DateTime.now;

  final WynimeDatabase _database;
  final String? Function()? _activeAccountProvider;
  final DateTime Function() _clock;
  final Random _random = Random.secure();
  String? _activeAccountOverride;

  @override
  String? get activeAccountId =>
      _activeAccountOverride ?? _activeAccountProvider?.call();

  @override
  Future<void> saveAccount(
    BangumiUserIdentity identity, {
    bool active = true,
  }) async {
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        if (active) {
          await (_database.update(_database.bangumiAccounts)..where(
                (table) => table.accountId.isNotIn(<String>[identity.id]),
              ))
              .write(const BangumiAccountsCompanion(isActive: Value(false)));
        }
        await _database
            .into(_database.bangumiAccounts)
            .insertOnConflictUpdate(
              BangumiAccountsCompanion(
                accountId: Value(identity.id),
                username: Value(identity.username),
                nickname: Value(identity.nickname),
                avatarUrl: Value(identity.avatarUrl?.toString()),
                isActive: Value(active),
                lastSeenAt: Value(now),
              ),
            );
      }),
    );
    if (active) _activeAccountOverride = identity.id;
  }

  @override
  Future<BangumiAccountSnapshot?> loadActiveAccount() async {
    final row = await (_database.select(
      _database.bangumiAccounts,
    )..where((table) => table.isActive.equals(true))).getSingleOrNull();
    if (row == null) {
      _activeAccountOverride = null;
      return null;
    }
    _activeAccountOverride = row.accountId;
    return BangumiAccountSnapshot(
      accountId: row.accountId,
      username: row.username,
      nickname: row.nickname,
      avatarUrl: row.avatarUrl == null ? null : Uri.tryParse(row.avatarUrl!),
      scheduleUpdatedAt: row.scheduleUpdatedAt?.toUtc(),
      isActive: row.isActive,
      lastSeenAt: row.lastSeenAt,
    );
  }

  @override
  Future<void> setActiveAccount(String accountId) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.update(
          _database.bangumiAccounts,
        )).write(const BangumiAccountsCompanion(isActive: Value(false)));
        final changed =
            await (_database.update(_database.bangumiAccounts)
                  ..where((table) => table.accountId.equals(accountId)))
                .write(const BangumiAccountsCompanion(isActive: Value(true)));
        if (changed != 1) {
          throw const BangumiApiException(code: 'account_not_found');
        }
      }),
    );
    _activeAccountOverride = accountId;
  }

  @override
  Future<void> deactivateActiveAccount() async {
    await _database.runWrite(
      () => (_database.update(
        _database.bangumiAccounts,
      )).write(const BangumiAccountsCompanion(isActive: Value(false))),
    );
    _activeAccountOverride = null;
  }

  @override
  Future<void> cacheSchedule(List<BangumiScheduleEntry> entries) async {
    final accountId = _requireAccount();
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.delete(
          _database.bangumiSchedules,
        )..where((table) => table.accountId.equals(accountId))).go();
        for (final entry in entries) {
          await _database
              .into(_database.bangumiSchedules)
              .insert(
                BangumiSchedulesCompanion(
                  accountId: Value(accountId),
                  entryId: Value(entry.id),
                  subjectId: Value(entry.subjectId),
                  subjectName: Value(entry.subjectName),
                  airWeekday: Value(entry.airWeekday),
                  airDate: Value(entry.airDate),
                  episodeNumber: Value(entry.episodeNumber),
                  imageUrl: Value(entry.imageUrl?.toString()),
                  updatedAt: Value(now),
                ),
              );
        }
        await (_database.update(_database.bangumiAccounts)
              ..where((table) => table.accountId.equals(accountId)))
            .write(BangumiAccountsCompanion(scheduleUpdatedAt: Value(now)));
      }),
    );
  }

  @override
  Future<List<BangumiScheduleEntry>> cachedSchedule() async {
    final accountId = activeAccountId;
    if (accountId == null) return const <BangumiScheduleEntry>[];
    final rows =
        await (_database.select(_database.bangumiSchedules)
              ..where((table) => table.accountId.equals(accountId))
              ..orderBy([
                (table) => OrderingTerm.asc(table.airWeekday),
                (table) => OrderingTerm.asc(table.airDate),
                (table) => OrderingTerm.asc(table.entryId),
              ]))
            .get();
    return List.unmodifiable(
      rows
          .map(
            (row) => BangumiScheduleEntry(
              id: row.entryId,
              subjectId: row.subjectId,
              subjectName: row.subjectName,
              airWeekday: row.airWeekday,
              airDate: row.airDate?.toUtc(),
              episodeNumber: row.episodeNumber,
              imageUrl: _safeCachedUri(row.imageUrl),
            ),
          )
          .toList(growable: false),
    );
  }

  @override
  Future<DateTime?> cachedScheduleUpdatedAt() async {
    final accountId = activeAccountId;
    if (accountId == null) return null;
    final row = await (_database.select(
      _database.bangumiAccounts,
    )..where((table) => table.accountId.equals(accountId))).getSingleOrNull();
    return row?.scheduleUpdatedAt?.toUtc();
  }

  @override
  Future<void> cacheSubject(BangumiSubject subject) async {
    await _database.runWrite(
      () => _database
          .into(_database.bangumiSubjects)
          .insertOnConflictUpdate(
            BangumiSubjectsCompanion(
              subjectId: Value(subject.id),
              name: Value(subject.name),
              nameCn: Value(subject.nameCn),
              summary: Value(subject.summary),
              imageUrl: Value(subject.imageUrl?.toString()),
              eps: Value(subject.eps),
              updatedAt: Value(_clock().toUtc()),
            ),
          ),
    );
  }

  @override
  Future<void> cacheCollection(BangumiCollectionEntry collection) async {
    final accountId = _requireAccount();
    final now = _clock().toUtc();
    await _database.runWrite(() async {
      final existing =
          await (_database.select(_database.bangumiCollections)..where(
                (table) =>
                    table.accountId.equals(accountId) &
                    table.subjectId.equals(collection.subjectId),
              ))
              .getSingleOrNull();
      final queuedRows =
          await (_database.select(_database.bangumiSyncOperations)
                ..where(
                  (table) =>
                      table.accountId.equals(accountId) &
                      table.subjectId.equals(collection.subjectId) &
                      table.kind.equals(
                        BangumiSyncOperationKind.collectionStatus.name,
                      ) &
                      table.collectionStatus.isNotNull() &
                      table.state.isIn(<String>[
                        BangumiSyncOperationState.pending.name,
                        BangumiSyncOperationState.retryWaiting.name,
                        BangumiSyncOperationState.conflict.name,
                        BangumiSyncOperationState.failed.name,
                      ]),
                )
                ..orderBy([(table) => OrderingTerm.desc(table.updatedAt)]))
              .get();
      final localStatus = queuedRows.isEmpty
          ? collection.status.apiType
          : queuedRows.first.collectionStatus!;
      await _database
          .into(_database.bangumiCollections)
          .insertOnConflictUpdate(
            BangumiCollectionsCompanion(
              accountId: Value(accountId),
              subjectId: Value(collection.subjectId),
              status: Value(localStatus),
              remoteRevision: Value(existing?.remoteRevision),
              localUpdatedAt: Value(existing?.localUpdatedAt ?? now),
              remoteUpdatedAt: Value(now),
            ),
          );
    });
  }

  @override
  Future<List<BangumiCollectionEntry>> cachedCollections() async {
    final accountId = activeAccountId;
    if (accountId == null) return const <BangumiCollectionEntry>[];
    final rows =
        await (_database.select(_database.bangumiCollections)
              ..where((table) => table.accountId.equals(accountId))
              ..orderBy([(table) => OrderingTerm.asc(table.localUpdatedAt)]))
            .get();
    final entries = <BangumiCollectionEntry>[];
    for (final row in rows) {
      final subject =
          await (_database.select(_database.bangumiSubjects)
                ..where((table) => table.subjectId.equals(row.subjectId)))
              .getSingleOrNull();
      final status = row.status == null
          ? null
          : BangumiCollectionStatus.fromApiType(row.status!);
      if (status == null) continue;
      entries.add(
        BangumiCollectionEntry(
          subjectId: row.subjectId,
          status: status,
          name: subject?.name,
          nameCn: subject?.nameCn,
        ),
      );
    }
    return List.unmodifiable(entries);
  }

  @override
  Future<List<BangumiCollectionEntry>> localFirstCollections() async {
    final accountId = activeAccountId;
    if (accountId == null) return const <BangumiCollectionEntry>[];
    final rows =
        await (_database.select(_database.bangumiSyncOperations)
              ..where(
                (table) =>
                    table.accountId.equals(accountId) &
                    table.kind.equals(
                      BangumiSyncOperationKind.collectionStatus.name,
                    ) &
                    table.collectionStatus.isNotNull() &
                    table.state.isIn(<String>[
                      BangumiSyncOperationState.pending.name,
                      BangumiSyncOperationState.retryWaiting.name,
                      BangumiSyncOperationState.conflict.name,
                      BangumiSyncOperationState.failed.name,
                    ]),
              )
              ..orderBy([
                (table) => OrderingTerm.desc(table.updatedAt),
                (table) => OrderingTerm.desc(table.createdAt),
              ]))
            .get();
    final latestBySubject = <String, BangumiSyncOperationRecord>{};
    for (final row in rows) {
      latestBySubject.putIfAbsent(row.subjectId, () => row);
    }
    final entries = <BangumiCollectionEntry>[];
    for (final row in latestBySubject.values) {
      final subject =
          await (_database.select(_database.bangumiSubjects)
                ..where((table) => table.subjectId.equals(row.subjectId)))
              .getSingleOrNull();
      entries.add(
        BangumiCollectionEntry(
          subjectId: row.subjectId,
          status: BangumiCollectionStatus.fromApiType(row.collectionStatus!),
          name: subject?.name,
          nameCn: subject?.nameCn,
        ),
      );
    }
    return List.unmodifiable(entries);
  }

  @override
  Future<void> cacheEpisodes(BangumiEpisodePage page) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        for (final episode in page.episodes) {
          await _database
              .into(_database.bangumiEpisodes)
              .insertOnConflictUpdate(
                BangumiEpisodesCompanion(
                  episodeId: Value(episode.id),
                  subjectId: Value(episode.subjectId),
                  name: Value(episode.name),
                  nameCn: Value(episode.nameCn),
                  sort: Value(episode.sort),
                  type: Value(episode.type),
                  duration: Value(episode.duration),
                  updatedAt: Value(_clock().toUtc()),
                ),
              );
        }
      }),
    );
  }

  @override
  Future<void> saveCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) async {
    final accountId = _requireAccount();
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        await _requireSubject(subjectId);
        final existing =
            await (_database.select(_database.bangumiCollections)..where(
                  (table) =>
                      table.accountId.equals(accountId) &
                      table.subjectId.equals(subjectId),
                ))
                .getSingleOrNull();
        await _database
            .into(_database.bangumiCollections)
            .insertOnConflictUpdate(
              BangumiCollectionsCompanion(
                accountId: Value(accountId),
                subjectId: Value(subjectId),
                status: Value(status.apiType),
                remoteRevision: Value(existing?.remoteRevision),
                localUpdatedAt: Value(now),
                remoteUpdatedAt: Value(existing?.remoteUpdatedAt),
              ),
            );
        await _enqueue(
          accountId: accountId,
          subjectId: subjectId,
          kind: BangumiSyncOperationKind.collectionStatus,
          collectionStatus: status,
          baseRemoteRevision: existing?.remoteRevision,
          now: now,
        );
      }),
    );
  }

  @override
  Future<void> markEpisodeWatched(String subjectId, String episodeId) =>
      setEpisodeWatched(subjectId, episodeId, true);

  @override
  Future<BangumiEpisodeProgress?> loadEpisodeProgress(String subjectId) async {
    final accountId = activeAccountId;
    if (accountId == null) return null;
    final episodeRows = await (_database.select(
      _database.bangumiEpisodes,
    )..where((table) => table.subjectId.equals(subjectId))).get();
    if (episodeRows.isEmpty) return null;
    final episodeIds = episodeRows.map((row) => row.episodeId).toSet();
    final watchedRows =
        await (_database.select(_database.bangumiEpisodeCollections)..where(
              (table) =>
                  table.accountId.equals(accountId) &
                  table.watched.equals(true),
            ))
            .get();
    final watched = watchedRows
        .map((row) => row.episodeId)
        .where(episodeIds.contains)
        .toSet();
    return BangumiEpisodeProgress(
      subjectId: subjectId,
      watchedEpisodeIds: Set.unmodifiable(watched),
    );
  }

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {
    final accountId = _requireAccount();
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        final episode =
            await (_database.select(_database.bangumiEpisodes)..where(
                  (table) =>
                      table.episodeId.equals(episodeId) &
                      table.subjectId.equals(subjectId),
                ))
                .getSingleOrNull();
        if (episode == null) {
          throw const BangumiApiException(code: 'episode_not_found');
        }
        final existing =
            await (_database.select(_database.bangumiEpisodeCollections)..where(
                  (table) =>
                      table.accountId.equals(accountId) &
                      table.episodeId.equals(episodeId),
                ))
                .getSingleOrNull();
        await _database
            .into(_database.bangumiEpisodeCollections)
            .insertOnConflictUpdate(
              BangumiEpisodeCollectionsCompanion(
                accountId: Value(accountId),
                episodeId: Value(episodeId),
                watched: Value(watched),
                remoteRevision: Value(existing?.remoteRevision),
                localUpdatedAt: Value(now),
                remoteUpdatedAt: Value(existing?.remoteUpdatedAt),
              ),
            );
        await _enqueue(
          accountId: accountId,
          subjectId: subjectId,
          episodeId: episodeId,
          kind: BangumiSyncOperationKind.episodeWatched,
          watched: watched,
          baseRemoteRevision: existing?.remoteRevision,
          now: now,
        );
      }),
    );
  }

  @override
  Future<List<BangumiPendingOperation>> pendingOperations({
    DateTime? now,
  }) async {
    final current = (now ?? _clock()).toUtc();
    final accountId = activeAccountId;
    if (accountId == null) return const <BangumiPendingOperation>[];
    final rows =
        await (_database.select(_database.bangumiSyncOperations)
              ..where(
                (table) =>
                    table.accountId.equals(accountId) &
                    (table.state.equals(
                          BangumiSyncOperationState.pending.name,
                        ) |
                        (table.state.equals(
                              BangumiSyncOperationState.retryWaiting.name,
                            ) &
                            (table.nextAttemptAt.isNull() |
                                table.nextAttemptAt.isSmallerOrEqualValue(
                                  current,
                                )))),
              )
              ..orderBy([(table) => OrderingTerm.asc(table.createdAt)]))
            .get();
    return rows.map(_mapOperation).toList(growable: false);
  }

  @override
  Future<List<BangumiPendingOperation>> conflictOperations() async {
    final accountId = activeAccountId;
    if (accountId == null) return const <BangumiPendingOperation>[];
    final rows =
        await (_database.select(_database.bangumiSyncOperations)..where(
              (table) =>
                  table.accountId.equals(accountId) &
                  table.state.equals(BangumiSyncOperationState.conflict.name),
            ))
            .get();
    return rows.map(_mapOperation).toList(growable: false);
  }

  @override
  Future<int> pendingCount() async {
    final accountId = activeAccountId;
    if (accountId == null) return 0;
    final count = _database.bangumiSyncOperations.operationId.count();
    final query = _database.selectOnly(_database.bangumiSyncOperations)
      ..addColumns([count])
      ..where(
        _database.bangumiSyncOperations.accountId.equals(accountId) &
            _database.bangumiSyncOperations.state.isIn(<String>[
              BangumiSyncOperationState.pending.name,
              BangumiSyncOperationState.retryWaiting.name,
            ]),
      );
    return (await query.getSingle()).read(count) ?? 0;
  }

  @override
  Future<int> failedCount() async {
    final accountId = activeAccountId;
    if (accountId == null) return 0;
    final count = _database.bangumiSyncOperations.operationId.count();
    final query = _database.selectOnly(_database.bangumiSyncOperations)
      ..addColumns([count])
      ..where(
        _database.bangumiSyncOperations.accountId.equals(accountId) &
            _database.bangumiSyncOperations.state.equals(
              BangumiSyncOperationState.failed.name,
            ),
      );
    return (await query.getSingle()).read(count) ?? 0;
  }

  @override
  Future<List<BangumiConflict>> conflicts() async {
    final accountId = activeAccountId;
    if (accountId == null) return const <BangumiConflict>[];
    final operations = await conflictOperations();
    final result = <BangumiConflict>[];
    for (final operation in operations) {
      final snapshot =
          await (_database.select(_database.bangumiConflictSnapshots)..where(
                (table) =>
                    table.operationId.equals(operation.operationId) &
                    table.accountId.equals(accountId),
              ))
              .getSingleOrNull();
      if (snapshot == null || snapshot.subjectId != operation.subjectId) {
        continue;
      }
      dynamic decoded;
      try {
        decoded = jsonDecode(snapshot.watchedEpisodeIds);
      } on Object {
        // A damaged conflict snapshot must not prevent the remaining queue
        // from being displayed or resolved.
        continue;
      }
      if (decoded is! List || decoded.any((value) => value is! String)) {
        continue;
      }
      BangumiCollectionStatus? status;
      if (snapshot.status != null) {
        try {
          status = BangumiCollectionStatus.fromApiType(snapshot.status!);
        } on BangumiPayloadException {
          continue;
        }
      }
      result.add(
        BangumiConflict(
          operation: operation,
          remoteState: BangumiRemoteState(
            accountId: snapshot.accountId,
            subjectId: snapshot.subjectId,
            status: status,
            watchedEpisodeIds: Set.unmodifiable(decoded.cast<String>().toSet()),
            remoteRevision: snapshot.remoteRevision,
          ),
        ),
      );
    }
    return List.unmodifiable(result);
  }

  @override
  Future<void> applyRemoteState(BangumiRemoteState state) async {
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        await _applyRemoteState(state, now);
      }),
    );
  }

  @override
  Future<void> reconcileAfterMutation(
    BangumiRemoteState state, {
    required String completedOperationId,
  }) async {
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        await _applyRemoteState(state, now);
        await (_database.update(_database.bangumiSyncOperations)..where(
              (table) =>
                  table.accountId.equals(state.accountId) &
                  table.subjectId.equals(state.subjectId) &
                  table.operationId.isNotIn(<String>[completedOperationId]) &
                  table.state.isIn(<String>[
                    BangumiSyncOperationState.pending.name,
                    BangumiSyncOperationState.retryWaiting.name,
                  ]),
            ))
            .write(
              BangumiSyncOperationsCompanion(
                baseRemoteRevision: Value(state.remoteRevision),
                updatedAt: Value(now),
              ),
            );
      }),
    );
  }

  @override
  Future<void> adoptRemote(BangumiConflict conflict) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.delete(_database.bangumiConflictSnapshots)..where(
              (table) =>
                  table.operationId.equals(conflict.operation.operationId),
            ))
            .go();
        await (_database.delete(_database.bangumiSyncOperations)..where(
              (table) =>
                  table.operationId.equals(conflict.operation.operationId),
            ))
            .go();
        // Remove the adopted operation before applying the remote state so
        // it cannot be mistaken for a still-active local-first overlay.
        await _applyRemoteState(conflict.remoteState, _clock().toUtc());
      }),
    );
  }

  @override
  Future<void> requeueAgainstRevision(
    BangumiPendingOperation operation,
    String remoteRevision,
  ) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.update(_database.bangumiSyncOperations)..where(
              (table) => table.operationId.equals(operation.operationId),
            ))
            .write(
              BangumiSyncOperationsCompanion(
                state: Value(BangumiSyncOperationState.pending.name),
                baseRemoteRevision: Value(remoteRevision),
                attempts: const Value(0),
                nextAttemptAt: const Value(null),
                lastErrorCode: const Value(null),
                updatedAt: Value(_clock().toUtc()),
              ),
            );
        await (_database.delete(_database.bangumiConflictSnapshots)..where(
              (table) => table.operationId.equals(operation.operationId),
            ))
            .go();
      }),
    );
  }

  @override
  Future<void> markRetry(
    BangumiPendingOperation operation,
    String errorCode, {
    required bool exhausted,
    required DateTime nextAttemptAt,
  }) async {
    await _database.runWrite(
      () =>
          (_database.update(_database.bangumiSyncOperations)..where(
                (table) => table.operationId.equals(operation.operationId),
              ))
              .write(
                BangumiSyncOperationsCompanion(
                  state: Value(
                    exhausted
                        ? BangumiSyncOperationState.failed.name
                        : BangumiSyncOperationState.retryWaiting.name,
                  ),
                  attempts: Value(operation.attempts + 1),
                  nextAttemptAt: Value(exhausted ? null : nextAttemptAt),
                  lastErrorCode: Value(errorCode),
                  updatedAt: Value(_clock().toUtc()),
                ),
              ),
    );
  }

  @override
  Future<void> complete(BangumiPendingOperation operation) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.delete(_database.bangumiConflictSnapshots)..where(
              (table) => table.operationId.equals(operation.operationId),
            ))
            .go();
        await (_database.delete(_database.bangumiSyncOperations)..where(
              (table) => table.operationId.equals(operation.operationId),
            ))
            .go();
      }),
    );
  }

  @override
  Future<void> markConflict(
    BangumiPendingOperation operation,
    BangumiRemoteState remoteState,
  ) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.update(_database.bangumiSyncOperations)..where(
              (table) => table.operationId.equals(operation.operationId),
            ))
            .write(
              BangumiSyncOperationsCompanion(
                state: Value(BangumiSyncOperationState.conflict.name),
                lastErrorCode: const Value('remote_revision_conflict'),
                updatedAt: Value(_clock().toUtc()),
              ),
            );
        final watched = remoteState.watchedEpisodeIds.toList()..sort();
        await _database
            .into(_database.bangumiConflictSnapshots)
            .insertOnConflictUpdate(
              BangumiConflictSnapshotsCompanion(
                operationId: Value(operation.operationId),
                accountId: Value(remoteState.accountId),
                subjectId: Value(remoteState.subjectId),
                status: Value(remoteState.status?.apiType),
                watchedEpisodeIds: Value(jsonEncode(watched)),
                remoteRevision: Value(remoteState.remoteRevision),
                capturedAt: Value(_clock().toUtc()),
              ),
            );
      }),
    );
  }

  Future<void> _enqueue({
    required String accountId,
    required String subjectId,
    String? episodeId,
    required BangumiSyncOperationKind kind,
    BangumiCollectionStatus? collectionStatus,
    bool? watched,
    String? baseRemoteRevision,
    required DateTime now,
  }) async {
    final existing =
        await (_database.select(_database.bangumiSyncOperations)..where(
              (table) =>
                  table.accountId.equals(accountId) &
                  table.subjectId.equals(subjectId) &
                  table.kind.equals(kind.name) &
                  (episodeId == null
                      ? table.episodeId.isNull()
                      : table.episodeId.equals(episodeId)) &
                  table.state.isIn(<String>[
                    BangumiSyncOperationState.pending.name,
                    BangumiSyncOperationState.retryWaiting.name,
                  ]),
            ))
            .getSingleOrNull();
    final operationId = existing?.operationId ?? _newOperationId();
    await _database
        .into(_database.bangumiSyncOperations)
        .insertOnConflictUpdate(
          BangumiSyncOperationsCompanion(
            operationId: Value(operationId),
            accountId: Value(accountId),
            subjectId: Value(subjectId),
            episodeId: Value(episodeId),
            kind: Value(kind.name),
            collectionStatus: Value(collectionStatus?.apiType),
            watched: Value(watched),
            baseRemoteRevision: Value(baseRemoteRevision),
            state: Value(BangumiSyncOperationState.pending.name),
            attempts: const Value(0),
            nextAttemptAt: const Value(null),
            lastErrorCode: const Value(null),
            createdAt: Value(existing?.createdAt ?? now),
            updatedAt: Value(now),
          ),
        );
  }

  Future<void> _applyRemoteState(BangumiRemoteState state, DateTime now) async {
    await _requireSubject(state.subjectId);
    final pendingRows =
        await (_database.select(_database.bangumiSyncOperations)
              ..where(
                (table) =>
                    table.accountId.equals(state.accountId) &
                    table.subjectId.equals(state.subjectId) &
                    table.state.isIn(<String>[
                      BangumiSyncOperationState.pending.name,
                      BangumiSyncOperationState.retryWaiting.name,
                      BangumiSyncOperationState.conflict.name,
                      BangumiSyncOperationState.failed.name,
                    ]),
              )
              ..orderBy([
                (table) => OrderingTerm.asc(table.updatedAt),
                (table) => OrderingTerm.asc(table.createdAt),
              ]))
            .get();
    final collectionPending = pendingRows
        .where(
          (row) =>
              row.kind == BangumiSyncOperationKind.collectionStatus.name &&
              row.collectionStatus != null,
        )
        .toList(growable: false);
    final episodePending = <String, bool>{
      for (final row in pendingRows)
        if (row.kind == BangumiSyncOperationKind.episodeWatched.name &&
            row.episodeId != null &&
            row.watched != null)
          row.episodeId!: row.watched!,
    };
    final existingCollection =
        await (_database.select(_database.bangumiCollections)..where(
              (table) =>
                  table.accountId.equals(state.accountId) &
                  table.subjectId.equals(state.subjectId),
            ))
            .getSingleOrNull();
    final localStatus = collectionPending.isEmpty
        ? state.status?.apiType
        : collectionPending.last.collectionStatus;
    await _database
        .into(_database.bangumiCollections)
        .insertOnConflictUpdate(
          BangumiCollectionsCompanion(
            accountId: Value(state.accountId),
            subjectId: Value(state.subjectId),
            status: Value(localStatus),
            remoteRevision: Value(state.remoteRevision),
            localUpdatedAt: Value(existingCollection?.localUpdatedAt ?? now),
            remoteUpdatedAt: Value(now),
          ),
        );
    final episodeRows = await (_database.select(
      _database.bangumiEpisodes,
    )..where((table) => table.subjectId.equals(state.subjectId))).get();
    for (final episode in episodeRows) {
      final existing =
          await (_database.select(_database.bangumiEpisodeCollections)..where(
                (table) =>
                    table.accountId.equals(state.accountId) &
                    table.episodeId.equals(episode.episodeId),
              ))
              .getSingleOrNull();
      await _database
          .into(_database.bangumiEpisodeCollections)
          .insertOnConflictUpdate(
            BangumiEpisodeCollectionsCompanion(
              accountId: Value(state.accountId),
              episodeId: Value(episode.episodeId),
              watched: Value(
                episodePending[episode.episodeId] ??
                    state.watchedEpisodeIds.contains(episode.episodeId),
              ),
              remoteRevision: Value(state.remoteRevision),
              localUpdatedAt: Value(existing?.localUpdatedAt ?? now),
              remoteUpdatedAt: Value(now),
            ),
          );
    }
  }

  Future<void> _requireSubject(String subjectId) async {
    final row = await (_database.select(
      _database.bangumiSubjects,
    )..where((table) => table.subjectId.equals(subjectId))).getSingleOrNull();
    if (row == null) {
      throw const BangumiApiException(code: 'subject_mapping_required');
    }
  }

  static Uri? _safeCachedUri(String? value) {
    if (value == null || value.length > 4096) return null;
    final uri = Uri.tryParse(value);
    if (uri == null ||
        uri.scheme != 'https' ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty) {
      return null;
    }
    return uri;
  }

  String _requireAccount() {
    final value = activeAccountId;
    if (value == null || value.isEmpty) {
      throw const BangumiApiException(code: 'reauth_required');
    }
    return value;
  }

  String _newOperationId() =>
      '${_clock().microsecondsSinceEpoch}-${_random.nextInt(1 << 32)}';

  BangumiPendingOperation _mapOperation(BangumiSyncOperationRecord row) {
    return BangumiPendingOperation(
      operationId: row.operationId,
      accountId: row.accountId,
      subjectId: row.subjectId,
      episodeId: row.episodeId,
      kind: BangumiSyncOperationKind.values.byName(row.kind),
      collectionStatus: row.collectionStatus == null
          ? null
          : BangumiCollectionStatus.fromApiType(row.collectionStatus!),
      watched: row.watched,
      baseRemoteRevision: row.baseRemoteRevision,
      state: BangumiSyncOperationState.values.byName(row.state),
      attempts: row.attempts,
      nextAttemptAt: row.nextAttemptAt,
      lastErrorCode: row.lastErrorCode,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    );
  }
}

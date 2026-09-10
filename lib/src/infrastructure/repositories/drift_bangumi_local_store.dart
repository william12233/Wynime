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
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _cacheSubjectRow(subject, now: now, detailUpdatedAt: now),
    );
  }

  @override
  Future<void> cacheSubjectDetail(BangumiSubjectDetailSnapshot snapshot) async {
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        await _cacheSubjectRow(
          snapshot.subject,
          now: now,
          detailUpdatedAt: snapshot.cachedAt ?? now,
        );
        await _cacheEpisodeRows(snapshot.episodes, now: now);

        await (_database.delete(
          _database.bangumiSubjectCharacters,
        )..where((table) => table.subjectId.equals(snapshot.subject.id))).go();
        for (var index = 0; index < snapshot.characters.length; index++) {
          await _database
              .into(_database.bangumiSubjectCharacters)
              .insert(
                BangumiSubjectCharactersCompanion.insert(
                  subjectId: snapshot.subject.id,
                  ordinal: index,
                  payloadJson: jsonEncode(
                    _characterToJson(snapshot.characters[index]),
                  ),
                  updatedAt: now,
                ),
              );
        }

        await (_database.delete(
          _database.bangumiSubjectPersons,
        )..where((table) => table.subjectId.equals(snapshot.subject.id))).go();
        for (var index = 0; index < snapshot.persons.length; index++) {
          await _database
              .into(_database.bangumiSubjectPersons)
              .insert(
                BangumiSubjectPersonsCompanion.insert(
                  subjectId: snapshot.subject.id,
                  ordinal: index,
                  payloadJson: jsonEncode(
                    _personToJson(snapshot.persons[index]),
                  ),
                  updatedAt: now,
                ),
              );
        }

        await (_database.delete(
          _database.bangumiSubjectRelations,
        )..where((table) => table.subjectId.equals(snapshot.subject.id))).go();
        for (var index = 0; index < snapshot.relations.length; index++) {
          await _database
              .into(_database.bangumiSubjectRelations)
              .insert(
                BangumiSubjectRelationsCompanion.insert(
                  subjectId: snapshot.subject.id,
                  ordinal: index,
                  payloadJson: jsonEncode(
                    _relationToJson(snapshot.relations[index]),
                  ),
                  updatedAt: now,
                ),
              );
        }
      }),
    );
  }

  @override
  Future<BangumiSubjectDetailSnapshot?> cachedSubjectDetail(
    String subjectId,
  ) async {
    final subjectRow = await (_database.select(
      _database.bangumiSubjects,
    )..where((table) => table.subjectId.equals(subjectId))).getSingleOrNull();
    if (subjectRow == null) return null;

    final episodeRows =
        await (_database.select(_database.bangumiEpisodes)
              ..where((table) => table.subjectId.equals(subjectId))
              ..orderBy([
                (table) => OrderingTerm.asc(table.sort),
                (table) => OrderingTerm.asc(table.episodeId),
              ]))
            .get();
    final episodes = BangumiEpisodePage(
      episodes: List.unmodifiable(
        episodeRows
            .map(
              (row) => BangumiEpisode(
                id: row.episodeId,
                subjectId: row.subjectId,
                name: row.name,
                nameCn: row.nameCn,
                sort: row.sort,
                type: row.type,
                duration: row.duration,
              ),
            )
            .toList(growable: false),
      ),
      offset: 0,
      limit: episodeRows.isEmpty ? 100 : episodeRows.length,
      total: _maxEpisodeTotal(
        subjectRow.totalEpisodes ?? subjectRow.eps ?? episodeRows.length,
        episodeRows.length,
      ),
    );
    final accountId = activeAccountId;
    BangumiCollectionStatus? collectionStatus;
    int? epStatus;
    final watchedEpisodeIds = <String>{};
    if (accountId != null) {
      final collection =
          await (_database.select(_database.bangumiCollections)..where(
                (table) =>
                    table.accountId.equals(accountId) &
                    table.subjectId.equals(subjectId),
              ))
              .getSingleOrNull();
      if (collection?.status != null) {
        collectionStatus = BangumiCollectionStatus.fromApiType(
          collection!.status!,
        );
      }
      epStatus = collection?.epStatus;
      final episodeIds = episodeRows.map((row) => row.episodeId).toSet();
      final watchedRows =
          await (_database.select(_database.bangumiEpisodeCollections)..where(
                (table) =>
                    table.accountId.equals(accountId) &
                    table.watched.equals(true),
              ))
              .get();
      watchedEpisodeIds.addAll(
        watchedRows.map((row) => row.episodeId).where(episodeIds.contains),
      );
    }

    return BangumiSubjectDetailSnapshot(
      subject: _subjectFromRecord(subjectRow),
      episodes: episodes,
      characters: _decodeCharacters(
        await (_database.select(_database.bangumiSubjectCharacters)
              ..where((table) => table.subjectId.equals(subjectId))
              ..orderBy([(table) => OrderingTerm.asc(table.ordinal)]))
            .get(),
      ),
      persons: _decodePersons(
        await (_database.select(_database.bangumiSubjectPersons)
              ..where((table) => table.subjectId.equals(subjectId))
              ..orderBy([(table) => OrderingTerm.asc(table.ordinal)]))
            .get(),
      ),
      relations: _decodeRelations(
        await (_database.select(_database.bangumiSubjectRelations)
              ..where((table) => table.subjectId.equals(subjectId))
              ..orderBy([(table) => OrderingTerm.asc(table.ordinal)]))
            .get(),
      ),
      collectionStatus: collectionStatus,
      epStatus: epStatus,
      watchedEpisodeIds: Set.unmodifiable(watchedEpisodeIds),
      cachedAt:
          subjectRow.detailUpdatedAt?.toUtc() ?? subjectRow.updatedAt.toUtc(),
    );
  }

  @override
  Future<void> cacheCollection(BangumiCollectionEntry collection) async {
    final accountId = _requireAccount();
    final now = _clock().toUtc();
    await _database.runWrite(() async {
      await _ensureSubjectForCollection(collection, now);
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
                      ]),
                )
                ..orderBy([(table) => OrderingTerm.desc(table.updatedAt)]))
              .get();
      final validQueuedRows = queuedRows
          .where(_isStructurallyValidSyncOperation)
          .toList(growable: false);
      final localStatus = validQueuedRows.isEmpty
          ? collection.status.apiType
          : validQueuedRows.first.collectionStatus!;
      await _database
          .into(_database.bangumiCollections)
          .insertOnConflictUpdate(
            BangumiCollectionsCompanion(
              accountId: Value(accountId),
              subjectId: Value(collection.subjectId),
              status: Value(localStatus),
              epStatus: Value(collection.epStatus ?? existing?.epStatus),
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
          imageUrl: _safeCachedUri(subject?.imageUrl),
          totalEpisodes: subject?.totalEpisodes ?? subject?.eps,
          epStatus: row.epStatus,
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
                    ]),
              )
              ..orderBy([
                (table) => OrderingTerm.desc(table.updatedAt),
                (table) => OrderingTerm.desc(table.createdAt),
              ]))
            .get();
    final latestBySubject = <String, BangumiSyncOperationRecord>{};
    for (final row in rows) {
      if (!_isStructurallyValidSyncOperation(row)) {
        await _blockMalformedOperation(row, 'operation_payload_invalid');
        continue;
      }
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
          imageUrl: _safeCachedUri(subject?.imageUrl),
          totalEpisodes: subject?.totalEpisodes ?? subject?.eps,
          epStatus:
              (await (_database.select(_database.bangumiCollections)..where(
                        (table) =>
                            table.accountId.equals(accountId) &
                            table.subjectId.equals(row.subjectId),
                      ))
                      .getSingleOrNull())
                  ?.epStatus,
        ),
      );
    }
    return List.unmodifiable(entries);
  }

  @override
  Future<void> cacheEpisodes(BangumiEpisodePage page) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await _cacheEpisodeRows(page, now: _clock().toUtc());
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
          baseCollectionStatus: existing?.status == null
              ? null
              : BangumiCollectionStatus.fromApiType(existing!.status!),
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
          baseWatched: existing?.watched,
          now: now,
        );
      }),
    );
  }

  Future<void> _cacheSubjectRow(
    BangumiSubject subject, {
    required DateTime now,
    required DateTime detailUpdatedAt,
  }) async {
    await _database
        .into(_database.bangumiSubjects)
        .insertOnConflictUpdate(
          BangumiSubjectsCompanion(
            subjectId: Value(subject.id),
            name: Value(subject.name),
            nameCn: Value(subject.nameCn),
            summary: Value(subject.summary),
            imageUrl: Value(subject.imageUrl?.toString()),
            eps: Value(subject.eps),
            totalEpisodes: Value(subject.totalEpisodes),
            volumes: Value(subject.volumes),
            airDate: Value(subject.airDate),
            platform: Value(subject.platform),
            rank: Value(subject.rank),
            ratingJson: Value(
              subject.rating == null
                  ? null
                  : jsonEncode(_ratingToJson(subject.rating!)),
            ),
            collectionStatsJson: Value(
              subject.collectionStats == null
                  ? null
                  : jsonEncode(
                      _collectionStatsToJson(subject.collectionStats!),
                    ),
            ),
            infoboxJson: Value(
              jsonEncode(
                subject.infobox.map(_infoboxToJson).toList(growable: false),
              ),
            ),
            metaTagsJson: Value(jsonEncode(subject.metaTags)),
            tagsJson: Value(
              jsonEncode(subject.tags.map(_tagToJson).toList(growable: false)),
            ),
            detailUpdatedAt: Value(detailUpdatedAt),
            updatedAt: Value(now),
          ),
        );
  }

  Future<void> _ensureSubjectForCollection(
    BangumiCollectionEntry collection,
    DateTime now,
  ) async {
    final existing =
        await (_database.select(_database.bangumiSubjects)
              ..where((table) => table.subjectId.equals(collection.subjectId)))
            .getSingleOrNull();
    if (existing == null) {
      await _database
          .into(_database.bangumiSubjects)
          .insert(
            BangumiSubjectsCompanion.insert(
              subjectId: collection.subjectId,
              name: collection.name ?? collection.subjectId,
              nameCn: collection.nameCn ?? '',
              summary: '',
              imageUrl: Value(collection.imageUrl?.toString()),
              eps: Value(null),
              totalEpisodes: Value(collection.totalEpisodes),
              volumes: const Value(null),
              airDate: const Value(null),
              platform: const Value(null),
              rank: const Value(null),
              ratingJson: const Value(null),
              collectionStatsJson: const Value(null),
              infoboxJson: const Value(null),
              metaTagsJson: const Value(null),
              tagsJson: const Value(null),
              detailUpdatedAt: const Value(null),
              updatedAt: now,
            ),
          );
      return;
    }
    await (_database.update(
      _database.bangumiSubjects,
    )..where((table) => table.subjectId.equals(collection.subjectId))).write(
      BangumiSubjectsCompanion(
        name: Value(collection.name ?? existing.name),
        nameCn: Value(collection.nameCn ?? existing.nameCn),
        imageUrl: Value(collection.imageUrl?.toString() ?? existing.imageUrl),
        totalEpisodes: Value(
          collection.totalEpisodes ?? existing.totalEpisodes,
        ),
        updatedAt: Value(now),
      ),
    );
  }

  Future<void> _cacheEpisodeRows(
    BangumiEpisodePage page, {
    required DateTime now,
  }) async {
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
              updatedAt: Value(now),
            ),
          );
    }
  }

  static BangumiSubject _subjectFromRecord(BangumiSubjectRecord row) {
    return BangumiSubject(
      id: row.subjectId,
      name: row.name,
      nameCn: row.nameCn,
      summary: row.summary,
      eps: row.eps,
      imageUrl: _safeCachedUri(row.imageUrl),
      totalEpisodes: row.totalEpisodes,
      volumes: row.volumes,
      airDate: row.airDate?.toUtc(),
      platform: row.platform,
      rank: row.rank,
      rating: _ratingFromJson(row.ratingJson),
      collectionStats: _collectionStatsFromJson(row.collectionStatsJson),
      infobox: _infoboxFromJson(row.infoboxJson),
      metaTags: _stringListFromJson(row.metaTagsJson),
      tags: _tagsFromJson(row.tagsJson),
    );
  }

  static int _maxEpisodeTotal(int declared, int cachedCount) =>
      declared < cachedCount ? cachedCount : declared;

  static List<BangumiCharacter> _decodeCharacters(
    List<BangumiSubjectCharacterRecord> rows,
  ) {
    final result = <BangumiCharacter>[];
    for (final row in rows) {
      final object = _decodeObject(row.payloadJson);
      if (object == null) continue;
      final id = _idFromJson(object['id']);
      if (id == null) continue;
      final actors = <BangumiActor>[];
      final rawActors = object['actors'];
      if (rawActors is List) {
        for (final rawActor in rawActors) {
          if (rawActor is! Map) continue;
          final actorId = _idFromJson(rawActor['id']);
          if (actorId == null) continue;
          actors.add(
            BangumiActor(
              id: actorId,
              name: _stringFromJson(rawActor['name']) ?? actorId,
              imageUrl: _uriFromJson(rawActor['imageUrl']),
            ),
          );
        }
      }
      result.add(
        BangumiCharacter(
          id: id,
          name: _stringFromJson(object['name']) ?? id,
          summary: _stringFromJson(object['summary']) ?? '',
          relation: _stringFromJson(object['relation']),
          imageUrl: _uriFromJson(object['imageUrl']),
          actors: List.unmodifiable(actors),
        ),
      );
    }
    return List.unmodifiable(result);
  }

  static List<BangumiPersonCredit> _decodePersons(
    List<BangumiSubjectPersonRecord> rows,
  ) {
    final result = <BangumiPersonCredit>[];
    for (final row in rows) {
      final object = _decodeObject(row.payloadJson);
      if (object == null) continue;
      final id = _idFromJson(object['id']);
      if (id == null) continue;
      result.add(
        BangumiPersonCredit(
          id: id,
          name: _stringFromJson(object['name']) ?? id,
          relation: _stringFromJson(object['relation']),
          career: _stringListFromValue(object['career']),
          eps: _stringListFromValue(object['eps']),
          imageUrl: _uriFromJson(object['imageUrl']),
        ),
      );
    }
    return List.unmodifiable(result);
  }

  static List<BangumiSubjectRelation> _decodeRelations(
    List<BangumiSubjectRelationRecord> rows,
  ) {
    final result = <BangumiSubjectRelation>[];
    for (final row in rows) {
      final object = _decodeObject(row.payloadJson);
      if (object == null) continue;
      final id = _idFromJson(object['id']);
      if (id == null) continue;
      result.add(
        BangumiSubjectRelation(
          id: id,
          type: _intFromJson(object['type']),
          name: _stringFromJson(object['name']) ?? id,
          nameCn: _stringFromJson(object['nameCn']) ?? '',
          relation: _stringFromJson(object['relation']),
          imageUrl: _uriFromJson(object['imageUrl']),
        ),
      );
    }
    return List.unmodifiable(result);
  }

  static Map<String, Object?> _characterToJson(BangumiCharacter value) => {
    'id': value.id,
    'name': value.name,
    'summary': value.summary,
    'relation': value.relation,
    'imageUrl': value.imageUrl?.toString(),
    'actors': value.actors.map(_actorToJson).toList(growable: false),
  };

  static Map<String, Object?> _actorToJson(BangumiActor value) => {
    'id': value.id,
    'name': value.name,
    'imageUrl': value.imageUrl?.toString(),
  };

  static Map<String, Object?> _personToJson(BangumiPersonCredit value) => {
    'id': value.id,
    'name': value.name,
    'relation': value.relation,
    'career': value.career,
    'eps': value.eps,
    'imageUrl': value.imageUrl?.toString(),
  };

  static Map<String, Object?> _relationToJson(BangumiSubjectRelation value) => {
    'id': value.id,
    'type': value.type,
    'name': value.name,
    'nameCn': value.nameCn,
    'relation': value.relation,
    'imageUrl': value.imageUrl?.toString(),
  };

  static Map<String, Object?> _ratingToJson(BangumiRating value) => {
    'total': value.total,
    'score': value.score,
    'count': {
      for (final entry in value.count.entries) '${entry.key}': entry.value,
    },
  };

  static Map<String, Object?> _collectionStatsToJson(
    BangumiPublicCollectionStats value,
  ) => {
    'wish': value.wish,
    'completed': value.completed,
    'watching': value.watching,
    'onHold': value.onHold,
    'dropped': value.dropped,
  };

  static Map<String, Object?> _infoboxToJson(BangumiInfoboxItem value) => {
    'key': value.key,
    'values': value.values
        .map((item) => {'key': item.key, 'text': item.text})
        .toList(growable: false),
  };

  static Map<String, Object?> _tagToJson(BangumiTag value) => {
    'name': value.name,
    'count': value.count,
    'totalCount': value.totalCount,
  };

  static Map<String, dynamic>? _decodeObject(String value) {
    try {
      final decoded = jsonDecode(value);
      return decoded is Map ? Map<String, dynamic>.from(decoded) : null;
    } on Object {
      return null;
    }
  }

  static String? _stringFromJson(Object? value) =>
      value is String && value.length <= 8192 ? value : null;

  static int? _intFromJson(Object? value) => value is int
      ? value
      : value is num
      ? value.toInt()
      : value is String
      ? int.tryParse(value)
      : null;

  static String? _idFromJson(Object? value) {
    if (value is int && value > 0) return '$value';
    if (value is String && RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(value)) {
      return value;
    }
    return null;
  }

  static Uri? _uriFromJson(Object? value) =>
      value is String ? _safeCachedUri(value) : null;

  static List<String> _stringListFromJson(String? value) {
    if (value == null) return const <String>[];
    try {
      return _stringListFromValue(jsonDecode(value));
    } on Object {
      return const <String>[];
    }
  }

  static List<String> _stringListFromValue(Object? value) {
    if (value is! List) return const <String>[];
    return List.unmodifiable(value.whereType<String>().take(512));
  }

  static BangumiRating? _ratingFromJson(String? value) {
    final object = value == null ? null : _decodeObject(value);
    if (object == null) return null;
    final total = _intFromJson(object['total']);
    final score = object['score'] is num
        ? (object['score'] as num).toDouble()
        : double.tryParse('${object['score']}');
    if (total == null || score == null) return null;
    final counts = <int, int>{};
    final rawCount = object['count'];
    if (rawCount is Map) {
      rawCount.forEach((key, item) {
        final rating = _intFromJson(key);
        final count = _intFromJson(item);
        if (rating != null && rating >= 1 && rating <= 10 && count != null) {
          counts[rating] = count;
        }
      });
    }
    return BangumiRating(
      total: total,
      score: score,
      count: Map.unmodifiable(counts),
    );
  }

  static BangumiPublicCollectionStats? _collectionStatsFromJson(String? value) {
    final object = value == null ? null : _decodeObject(value);
    if (object == null) return null;
    final values = [
      _intFromJson(object['wish']),
      _intFromJson(object['completed']),
      _intFromJson(object['watching']),
      _intFromJson(object['onHold']),
      _intFromJson(object['dropped']),
    ];
    if (values.any((item) => item == null)) return null;
    return BangumiPublicCollectionStats(
      wish: values[0]!,
      completed: values[1]!,
      watching: values[2]!,
      onHold: values[3]!,
      dropped: values[4]!,
    );
  }

  static List<BangumiInfoboxItem> _infoboxFromJson(String? value) {
    if (value == null) return const <BangumiInfoboxItem>[];
    try {
      final decoded = jsonDecode(value);
      if (decoded is! List) return const <BangumiInfoboxItem>[];
      final result = <BangumiInfoboxItem>[];
      for (final item in decoded) {
        if (item is! Map) continue;
        final key = _stringFromJson(item['key']);
        final rawValues = item['values'];
        if (key == null || rawValues is! List) continue;
        final values = <BangumiInfoboxValue>[];
        for (final rawValue in rawValues) {
          if (rawValue is! Map) continue;
          final text = _stringFromJson(rawValue['text']);
          if (text != null) {
            values.add(
              BangumiInfoboxValue(
                text: text,
                key: _stringFromJson(rawValue['key']),
              ),
            );
          }
        }
        if (values.isNotEmpty) {
          result.add(
            BangumiInfoboxItem(key: key, values: List.unmodifiable(values)),
          );
        }
      }
      return List.unmodifiable(result);
    } on Object {
      return const <BangumiInfoboxItem>[];
    }
  }

  static List<BangumiTag> _tagsFromJson(String? value) {
    if (value == null) return const <BangumiTag>[];
    try {
      final decoded = jsonDecode(value);
      if (decoded is! List) return const <BangumiTag>[];
      final result = <BangumiTag>[];
      for (final item in decoded) {
        if (item is! Map) continue;
        final name = _stringFromJson(item['name']);
        final count = _intFromJson(item['count']);
        if (name == null || count == null) continue;
        result.add(
          BangumiTag(
            name: name,
            count: count,
            totalCount: _intFromJson(item['totalCount']),
          ),
        );
      }
      return List.unmodifiable(result);
    } on Object {
      return const <BangumiTag>[];
    }
  }

  @override
  Future<List<BangumiPendingOperation>> pendingOperations({
    DateTime? now,
    bool forceRetry = false,
  }) async {
    final current = (now ?? _clock()).toUtc();
    final accountId = activeAccountId;
    if (accountId == null) return const <BangumiPendingOperation>[];
    final stateFilter = forceRetry
        ? _database.bangumiSyncOperations.state.isIn(<String>[
            BangumiSyncOperationState.pending.name,
            BangumiSyncOperationState.retryWaiting.name,
          ])
        : (_database.bangumiSyncOperations.state.equals(
                BangumiSyncOperationState.pending.name,
              ) |
              (_database.bangumiSyncOperations.state.equals(
                    BangumiSyncOperationState.retryWaiting.name,
                  ) &
                  (_database.bangumiSyncOperations.nextAttemptAt.isNull() |
                      _database.bangumiSyncOperations.nextAttemptAt
                          .isSmallerOrEqualValue(current))));
    final rows =
        await (_database.select(_database.bangumiSyncOperations)
              ..where(
                (table) => table.accountId.equals(accountId) & stateFilter,
              )
              ..orderBy([(table) => OrderingTerm.asc(table.createdAt)]))
            .get();
    final operations = <BangumiPendingOperation>[];
    for (final row in rows) {
      try {
        operations.add(_mapOperation(row));
      } on BangumiPayloadException catch (error) {
        await _blockMalformedOperation(row, error.code);
      } on Object {
        await _blockMalformedOperation(row, 'legacy_operation_invalid');
      }
    }
    return List.unmodifiable(operations);
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
    final operations = <BangumiPendingOperation>[];
    for (final row in rows) {
      try {
        operations.add(_mapOperation(row));
      } on BangumiPayloadException catch (error) {
        await _blockMalformedOperation(row, error.code);
      } on Object {
        await _blockMalformedOperation(row, 'legacy_operation_invalid');
      }
    }
    return List.unmodifiable(operations);
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
  Future<int> blockedCount() async {
    final accountId = activeAccountId;
    if (accountId == null) return 0;
    final count = _database.bangumiSyncOperations.operationId.count();
    final query = _database.selectOnly(_database.bangumiSyncOperations)
      ..addColumns([count])
      ..where(
        _database.bangumiSyncOperations.accountId.equals(accountId) &
            _database.bangumiSyncOperations.state.equals(
              BangumiSyncOperationState.blocked.name,
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
                statusCode: const Value(null),
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
    required DateTime nextAttemptAt,
    int? statusCode,
  }) async {
    await _database.runWrite(
      () =>
          (_database.update(_database.bangumiSyncOperations)..where(
                (table) => table.operationId.equals(operation.operationId),
              ))
              .write(
                BangumiSyncOperationsCompanion(
                  state: Value(BangumiSyncOperationState.retryWaiting.name),
                  attempts: Value(operation.attempts + 1),
                  nextAttemptAt: Value(nextAttemptAt),
                  lastErrorCode: Value(errorCode),
                  statusCode: Value(statusCode),
                  updatedAt: Value(_clock().toUtc()),
                ),
              ),
    );
  }

  @override
  Future<void> markBlocked(
    BangumiPendingOperation operation,
    String errorCode, {
    int? statusCode,
  }) async {
    await _database.runWrite(
      () =>
          (_database.update(_database.bangumiSyncOperations)..where(
                (table) => table.operationId.equals(operation.operationId),
              ))
              .write(
                BangumiSyncOperationsCompanion(
                  state: Value(BangumiSyncOperationState.blocked.name),
                  attempts: Value(operation.attempts + 1),
                  nextAttemptAt: const Value(null),
                  lastErrorCode: Value(errorCode),
                  statusCode: Value(statusCode),
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
                statusCode: const Value(null),
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
    BangumiCollectionStatus? baseCollectionStatus,
    bool? baseWatched,
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
            baseRemoteRevision: Value(
              existing?.baseRemoteRevision ?? baseRemoteRevision,
            ),
            baseCollectionStatus: Value(
              existing?.baseCollectionStatus ?? baseCollectionStatus?.apiType,
            ),
            baseWatched: Value(existing?.baseWatched ?? baseWatched),
            state: Value(BangumiSyncOperationState.pending.name),
            attempts: const Value(0),
            nextAttemptAt: const Value(null),
            lastErrorCode: const Value(null),
            statusCode: const Value(null),
            createdAt: Value(existing?.createdAt ?? now),
            updatedAt: Value(now),
          ),
        );
  }

  Future<void> _applyRemoteState(BangumiRemoteState state, DateTime now) async {
    if (_requireAccount() != state.accountId) {
      throw const BangumiApiException(code: 'account_mismatch');
    }
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
              _isStructurallyValidSyncOperation(row) &&
              row.kind == BangumiSyncOperationKind.collectionStatus.name &&
              row.collectionStatus != null,
        )
        .toList(growable: false);
    final episodePending = <String, bool>{
      for (final row in pendingRows)
        if (_isStructurallyValidSyncOperation(row) &&
            row.kind == BangumiSyncOperationKind.episodeWatched.name &&
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
    final watched = state.watchedEpisodeIds.toList()..sort();
    for (final row in pendingRows.where(
      (row) => row.state == BangumiSyncOperationState.conflict.name,
    )) {
      await _database
          .into(_database.bangumiConflictSnapshots)
          .insertOnConflictUpdate(
            BangumiConflictSnapshotsCompanion(
              operationId: Value(row.operationId),
              accountId: Value(state.accountId),
              subjectId: Value(state.subjectId),
              status: Value(state.status?.apiType),
              watchedEpisodeIds: Value(jsonEncode(watched)),
              remoteRevision: Value(state.remoteRevision),
              capturedAt: Value(now),
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

  static bool _isStructurallyValidSyncOperation(
    BangumiSyncOperationRecord row,
  ) {
    if (row.accountId.isEmpty || row.subjectId.isEmpty) return false;
    if (!RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(row.subjectId)) {
      return false;
    }
    if (row.kind == BangumiSyncOperationKind.collectionStatus.name) {
      return row.episodeId == null &&
          row.watched == null &&
          row.collectionStatus != null &&
          row.collectionStatus! >= 1 &&
          row.collectionStatus! <= 5;
    }
    if (row.kind == BangumiSyncOperationKind.episodeWatched.name) {
      return row.collectionStatus == null &&
          row.episodeId != null &&
          RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(row.episodeId!) &&
          row.watched != null;
    }
    return false;
  }

  Future<void> _blockMalformedOperation(
    BangumiSyncOperationRecord row,
    String errorCode,
  ) async {
    await _database.runWrite(
      () =>
          (_database.update(
            _database.bangumiSyncOperations,
          )..where((table) => table.operationId.equals(row.operationId))).write(
            BangumiSyncOperationsCompanion(
              state: Value(BangumiSyncOperationState.blocked.name),
              attempts: Value(row.attempts + 1),
              nextAttemptAt: const Value(null),
              lastErrorCode: Value(errorCode),
              statusCode: const Value(null),
              updatedAt: Value(_clock().toUtc()),
            ),
          ),
    );
  }

  BangumiPendingOperation _mapOperation(BangumiSyncOperationRecord row) {
    if (!_isStructurallyValidSyncOperation(row)) {
      throw const BangumiPayloadException('operation_payload_invalid');
    }
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
      baseCollectionStatus: row.baseCollectionStatus == null
          ? null
          : BangumiCollectionStatus.fromApiType(row.baseCollectionStatus!),
      baseWatched: row.baseWatched,
      state: BangumiSyncOperationState.values.byName(row.state),
      attempts: row.attempts,
      nextAttemptAt: row.nextAttemptAt,
      lastErrorCode: row.lastErrorCode,
      statusCode: row.statusCode,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    );
  }
}

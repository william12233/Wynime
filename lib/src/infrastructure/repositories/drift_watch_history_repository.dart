import 'package:drift/drift.dart';

import '../../domain/models/watch_progress.dart';
import '../../domain/repositories/watch_history_repository.dart';
import '../database/wynime_database.dart';

final class DriftWatchHistoryRepository implements WatchHistoryRepository {
  DriftWatchHistoryRepository(this._database);

  final WynimeDatabase _database;

  @override
  Future<void> save(WatchProgress progress) async {
    await _database
        .into(_database.watchHistoryRows)
        .insertOnConflictUpdate(
          WatchHistoryRowsCompanion(
            progressId: Value(progress.progressId),
            sourceId: Value(progress.sourceId),
            lineId: Value(progress.lineId),
            subjectId: Value(progress.subjectId),
            episodeId: Value(progress.episodeId),
            positionMs: Value(progress.position.inMilliseconds),
            durationMs: Value(progress.duration.inMilliseconds),
            isCompleted: Value(progress.isCompleted),
            playerBackendId: Value(progress.playerBackendId),
            timelineMapId: Value(progress.timelineMapId),
            updatedAt: Value(progress.updatedAt),
          ),
        );
  }

  @override
  Future<WatchProgress?> findById(String progressId) async {
    final query = _database.select(_database.watchHistoryRows)
      ..where((table) => table.progressId.equals(progressId));
    final row = await query.getSingleOrNull();
    return row == null ? null : _map(row);
  }

  @override
  Stream<List<WatchProgress>> watchRecent({int limit = 50}) {
    if (limit <= 0) {
      throw ArgumentError.value(limit, 'limit', 'Must be positive.');
    }
    final query = _database.select(_database.watchHistoryRows)
      ..orderBy([(table) => OrderingTerm.desc(table.updatedAt)])
      ..limit(limit);
    return query.watch().map((rows) => rows.map(_map).toList(growable: false));
  }

  @override
  Future<void> remove(String progressId) async {
    await (_database.delete(
      _database.watchHistoryRows,
    )..where((table) => table.progressId.equals(progressId))).go();
  }

  WatchProgress _map(WatchHistoryRecord row) {
    return WatchProgress(
      progressId: row.progressId,
      sourceId: row.sourceId,
      lineId: row.lineId,
      subjectId: row.subjectId,
      episodeId: row.episodeId,
      position: Duration(milliseconds: row.positionMs),
      duration: Duration(milliseconds: row.durationMs),
      isCompleted: row.isCompleted,
      playerBackendId: row.playerBackendId,
      timelineMapId: row.timelineMapId,
      updatedAt: row.updatedAt,
    );
  }
}

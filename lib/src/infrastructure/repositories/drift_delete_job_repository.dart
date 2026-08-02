import 'package:drift/drift.dart';

import '../../domain/models/delete_job.dart';
import '../../domain/repositories/delete_job_repository.dart';
import '../database/wynime_database.dart';

final class DriftDeleteJobRepository implements DeleteJobRepository {
  DriftDeleteJobRepository(this._database);

  static const interruptedFailureCode = 'interrupted';

  final WynimeDatabase _database;

  @override
  Future<void> create(DeleteJob job) async {
    if (job.status != DeleteJobStatus.pending) {
      throw ArgumentError.value(
        job.status,
        'job.status',
        'New DeleteJobs must start in pending state.',
      );
    }
    await _database.into(_database.deleteJobRows).insert(_companion(job));
  }

  @override
  Future<DeleteJob?> findById(String jobId) async {
    final query = _database.select(_database.deleteJobRows)
      ..where((table) => table.jobId.equals(jobId));
    final row = await query.getSingleOrNull();
    return row == null ? null : _map(row);
  }

  @override
  Future<DeleteJob> markRunning(String jobId, DateTime now) {
    return _transition(jobId, DeleteJobStatus.running, now);
  }

  @override
  Future<DeleteJob> markFailed(
    String jobId,
    DateTime now, {
    required String failureCode,
  }) {
    return _transition(
      jobId,
      DeleteJobStatus.failed,
      now,
      failureCode: failureCode,
    );
  }

  @override
  Future<DeleteJob> retry(String jobId, DateTime now) {
    return _transition(jobId, DeleteJobStatus.pending, now);
  }

  @override
  Future<DeleteJob> markCompleted(String jobId, DateTime now) {
    return _transition(jobId, DeleteJobStatus.completed, now);
  }

  @override
  Future<List<DeleteJob>> recoverInterrupted(DateTime now) {
    return _database.transaction(() async {
      final query = _database.select(_database.deleteJobRows)
        ..where((table) => table.status.equals(DeleteJobStatus.running.name));
      final running = await query.get();
      final recovered = <DeleteJob>[];
      for (final row in running) {
        final job = _map(row).transitionTo(
          DeleteJobStatus.failed,
          now: now,
          failureCode: interruptedFailureCode,
        );
        await _write(job);
        recovered.add(job);
      }
      return List<DeleteJob>.unmodifiable(recovered);
    });
  }

  @override
  Stream<List<DeleteJob>> watchOutstanding() {
    final query = _database.select(_database.deleteJobRows)
      ..where(
        (table) => table.status.isNotValue(DeleteJobStatus.completed.name),
      )
      ..orderBy([(table) => OrderingTerm.asc(table.createdAt)]);
    return query.watch().map((rows) => rows.map(_map).toList(growable: false));
  }

  Future<DeleteJob> _transition(
    String jobId,
    DeleteJobStatus target,
    DateTime now, {
    String? failureCode,
  }) {
    return _database.transaction(() async {
      final current = await _require(jobId);
      final updated = current.transitionTo(
        target,
        now: now,
        failureCode: failureCode,
      );
      await _write(updated);
      return updated;
    });
  }

  Future<DeleteJob> _require(String jobId) async {
    final job = await findById(jobId);
    if (job == null) {
      throw StateError('DeleteJob not found: $jobId');
    }
    return job;
  }

  Future<void> _write(DeleteJob job) async {
    final count = await (_database.update(
      _database.deleteJobRows,
    )..where((table) => table.jobId.equals(job.jobId))).write(_companion(job));
    if (count != 1) {
      throw StateError('DeleteJob update affected $count rows: ${job.jobId}');
    }
  }

  DeleteJobRowsCompanion _companion(DeleteJob job) {
    return DeleteJobRowsCompanion(
      jobId: Value(job.jobId),
      artifactManifestId: Value(job.artifactManifestId),
      status: Value(job.status.name),
      attempts: Value(job.attempts),
      failureCode: Value(job.failureCode),
      createdAt: Value(job.createdAt),
      updatedAt: Value(job.updatedAt),
    );
  }

  DeleteJob _map(DeleteJobRecord row) {
    return DeleteJob(
      jobId: row.jobId,
      artifactManifestId: row.artifactManifestId,
      status: DeleteJobStatus.values.byName(row.status),
      attempts: row.attempts,
      failureCode: row.failureCode,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    );
  }
}

import '../models/delete_job.dart';

abstract interface class DeleteJobRepository {
  Future<void> create(DeleteJob job);

  Future<DeleteJob?> findById(String jobId);

  Future<DeleteJob> markRunning(String jobId, DateTime now);

  Future<DeleteJob> markFailed(
    String jobId,
    DateTime now, {
    required String failureCode,
  });

  Future<DeleteJob> retry(String jobId, DateTime now);

  Future<DeleteJob> markCompleted(String jobId, DateTime now);

  Future<List<DeleteJob>> recoverInterrupted(DateTime now);

  Stream<List<DeleteJob>> watchOutstanding();
}

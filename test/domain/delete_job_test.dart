import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/delete_job.dart';

void main() {
  final createdAt = DateTime.utc(2026, 8, 2, 1);

  DeleteJob pending() => DeleteJob(
    jobId: 'job-1',
    artifactManifestId: 'manifest-1',
    status: DeleteJobStatus.pending,
    createdAt: createdAt,
    updatedAt: createdAt,
  );

  test('delete job accepts only the explicit state machine', () {
    final running = pending().transitionTo(
      DeleteJobStatus.running,
      now: createdAt.add(const Duration(minutes: 1)),
    );
    expect(running.attempts, 1);

    final failed = running.transitionTo(
      DeleteJobStatus.failed,
      now: createdAt.add(const Duration(minutes: 2)),
      failureCode: 'io_error',
    );
    final retried = failed.transitionTo(
      DeleteJobStatus.pending,
      now: createdAt.add(const Duration(minutes: 3)),
    );
    expect(retried.failureCode, isNull);
  });

  test('pending jobs cannot be marked completed', () {
    expect(
      () => pending().transitionTo(
        DeleteJobStatus.completed,
        now: createdAt.add(const Duration(minutes: 1)),
      ),
      throwsStateError,
    );
  });

  test('failed jobs require a non-empty failure code', () {
    expect(
      () => DeleteJob(
        jobId: 'job-1',
        artifactManifestId: 'manifest-1',
        status: DeleteJobStatus.failed,
        createdAt: createdAt,
        updatedAt: createdAt,
      ),
      throwsArgumentError,
    );
  });
}

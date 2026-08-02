enum DeleteJobStatus { pending, running, failed, completed }

final class DeleteJob {
  DeleteJob({
    required this.jobId,
    required this.artifactManifestId,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
    this.attempts = 0,
    this.failureCode,
  }) : assert(jobId.trim().isNotEmpty, 'jobId must not be empty.'),
       assert(
         artifactManifestId.trim().isNotEmpty,
         'artifactManifestId must not be empty.',
       ),
       assert(attempts >= 0, 'attempts must not be negative.'),
       assert(
         status == DeleteJobStatus.failed
             ? failureCode != null && failureCode.trim().isNotEmpty
             : failureCode == null,
         'failureCode must exist only for failed jobs.',
       ),
       assert(
         !updatedAt.isBefore(createdAt),
         'updatedAt must not be before createdAt.',
       );

  final String jobId;
  final String artifactManifestId;
  final DeleteJobStatus status;
  final int attempts;
  final String? failureCode;
  final DateTime createdAt;
  final DateTime updatedAt;

  bool canTransitionTo(DeleteJobStatus target) {
    return switch ((status, target)) {
      (DeleteJobStatus.pending, DeleteJobStatus.running) => true,
      (DeleteJobStatus.running, DeleteJobStatus.failed) => true,
      (DeleteJobStatus.running, DeleteJobStatus.completed) => true,
      (DeleteJobStatus.failed, DeleteJobStatus.pending) => true,
      _ => false,
    };
  }

  DeleteJob transitionTo(
    DeleteJobStatus target, {
    required DateTime now,
    String? failureCode,
  }) {
    if (!canTransitionTo(target)) {
      throw StateError('Illegal DeleteJob transition: $status -> $target');
    }

    return DeleteJob(
      jobId: jobId,
      artifactManifestId: artifactManifestId,
      status: target,
      attempts: target == DeleteJobStatus.running ? attempts + 1 : attempts,
      failureCode: target == DeleteJobStatus.failed ? failureCode : null,
      createdAt: createdAt,
      updatedAt: now,
    );
  }
}

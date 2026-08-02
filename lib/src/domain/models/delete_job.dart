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
  }) {
    if (jobId.trim().isEmpty) {
      throw ArgumentError.value(jobId, 'jobId', 'Must not be empty.');
    }
    if (artifactManifestId.trim().isEmpty) {
      throw ArgumentError.value(
        artifactManifestId,
        'artifactManifestId',
        'Must not be empty.',
      );
    }
    if (attempts < 0) {
      throw ArgumentError.value(attempts, 'attempts', 'Must not be negative.');
    }
    final hasFailureCode = failureCode != null && failureCode!.trim().isNotEmpty;
    if ((status == DeleteJobStatus.failed) != hasFailureCode) {
      throw ArgumentError(
        'failureCode must be non-empty exactly when status is failed.',
      );
    }
    if (updatedAt.isBefore(createdAt)) {
      throw ArgumentError('updatedAt must not be before createdAt.');
    }
  }

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
    if (now.isBefore(updatedAt)) {
      throw ArgumentError.value(now, 'now', 'Must not move time backwards.');
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

enum DeleteJobStatus { pending, running, failed, completed }

final class DeleteJob {
  DeleteJob({
    required this.jobId,
    required this.artifactManifestId,
    required this.status,
    this.attempts = 0,
    this.failureCode,
  }) : assert(jobId.trim().isNotEmpty, 'jobId must not be empty.'),
       assert(
         artifactManifestId.trim().isNotEmpty,
         'artifactManifestId must not be empty.',
       ),
       assert(attempts >= 0, 'attempts must not be negative.'),
       assert(
         status == DeleteJobStatus.failed || failureCode == null,
         'failureCode is only valid for failed jobs.',
       );

  final String jobId;
  final String artifactManifestId;
  final DeleteJobStatus status;
  final int attempts;
  final String? failureCode;
}

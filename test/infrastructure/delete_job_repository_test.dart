import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/delete_job.dart';
import 'package:wynime/src/domain/models/download_artifact_manifest.dart';
import 'package:wynime/src/infrastructure/repositories/drift_artifact_manifest_repository.dart';
import 'package:wynime/src/infrastructure/repositories/drift_delete_job_repository.dart';

import '../helpers/test_database.dart';

void main() {
  final createdAt = DateTime.utc(2026, 8, 2);

  test('delete jobs require an existing authoritative manifest', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final repository = DriftDeleteJobRepository(database);

    await expectLater(
      repository.create(
        DeleteJob(
          jobId: 'job-1',
          artifactManifestId: 'missing',
          status: DeleteJobStatus.pending,
          createdAt: createdAt,
          updatedAt: createdAt,
        ),
      ),
      throwsA(anything),
    );
  });

  test('running jobs recover as failed without pretending deletion succeeded', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final manifests = DriftArtifactManifestRepository(database);
    final jobs = DriftDeleteJobRepository(database);
    await manifests.create(
      DownloadArtifactManifest(
        manifestId: 'manifest-1',
        downloadId: 'download-1',
        createdAt: createdAt,
        artifacts: [
          DownloadArtifact(
            artifactId: 'artifact-1',
            kind: DownloadArtifactKind.finalVideo,
            fileUri: Uri.file('/downloads/episode.mp4', windows: false),
          ),
        ],
      ),
    );
    await jobs.create(
      DeleteJob(
        jobId: 'job-1',
        artifactManifestId: 'manifest-1',
        status: DeleteJobStatus.pending,
        createdAt: createdAt,
        updatedAt: createdAt,
      ),
    );
    await jobs.markRunning(
      'job-1',
      createdAt.add(const Duration(minutes: 1)),
    );

    final recovered = await jobs.recoverInterrupted(
      createdAt.add(const Duration(minutes: 2)),
    );

    expect(recovered, hasLength(1));
    expect(recovered.single.status, DeleteJobStatus.failed);
    expect(
      recovered.single.failureCode,
      DriftDeleteJobRepository.interruptedFailureCode,
    );
    expect((await jobs.findById('job-1'))!.status, DeleteJobStatus.failed);
  });
}

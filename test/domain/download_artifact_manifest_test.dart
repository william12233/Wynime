import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/delete_job.dart';
import 'package:wynime/src/domain/models/download_artifact_manifest.dart';

void main() {
  test('artifact manifest keeps the exact registered file inventory', () {
    final manifest = DownloadArtifactManifest(
      manifestId: 'manifest-1',
      downloadId: 'download-1',
      createdAt: DateTime.utc(2026),
      artifacts: [
        DownloadArtifact(
          artifactId: 'video',
          kind: DownloadArtifactKind.finalVideo,
          fileUri: Uri.file('/downloads/video.mp4'),
        ),
        DownloadArtifact(
          artifactId: 'subtitle',
          kind: DownloadArtifactKind.subtitle,
          fileUri: Uri.file('/downloads/video.zh-Hant.ass'),
        ),
      ],
    );

    expect(manifest.artifacts.map((artifact) => artifact.fileUri.path), [
      '/downloads/video.mp4',
      '/downloads/video.zh-Hant.ass',
    ]);
    expect(
      () => manifest.artifacts.add(
        DownloadArtifact(
          artifactId: 'unexpected',
          kind: DownloadArtifactKind.diagnosticLog,
          fileUri: Uri.file('/downloads/unexpected.log'),
        ),
      ),
      throwsUnsupportedError,
    );
  });

  test('duplicate physical paths are rejected', () {
    expect(
      () => DownloadArtifactManifest(
        manifestId: 'manifest-1',
        downloadId: 'download-1',
        createdAt: DateTime.utc(2026),
        artifacts: [
          DownloadArtifact(
            artifactId: 'first',
            kind: DownloadArtifactKind.finalVideo,
            fileUri: Uri.file('/downloads/video.mp4'),
          ),
          DownloadArtifact(
            artifactId: 'second',
            kind: DownloadArtifactKind.remuxTemporaryFile,
            fileUri: Uri.file('/downloads/video.mp4'),
          ),
        ],
      ),
      throwsArgumentError,
    );
  });

  test('delete job references the authoritative artifact manifest', () {
    final now = DateTime.utc(2026);
    final job = DeleteJob(
      jobId: 'delete-1',
      artifactManifestId: 'manifest-1',
      status: DeleteJobStatus.pending,
      createdAt: now,
      updatedAt: now,
    );

    expect(job.artifactManifestId, 'manifest-1');
  });
}

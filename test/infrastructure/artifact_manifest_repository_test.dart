import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/download_artifact_manifest.dart';
import 'package:wynime/src/infrastructure/repositories/drift_artifact_manifest_repository.dart';

import '../helpers/test_database.dart';

void main() {
  DownloadArtifactManifest manifest({
    required String manifestId,
    required String downloadId,
    required String artifactId,
    required String path,
  }) {
    return DownloadArtifactManifest(
      manifestId: manifestId,
      downloadId: downloadId,
      createdAt: DateTime.utc(2026, 8, 2),
      artifacts: [
        DownloadArtifact(
          artifactId: artifactId,
          kind: DownloadArtifactKind.finalVideo,
          fileUri: Uri.file(path, windows: false),
        ),
      ],
    );
  }

  test(
    'manifest and artifact inventory persist as one authoritative unit',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftArtifactManifestRepository(database);
      final value = manifest(
        manifestId: 'manifest-1',
        downloadId: 'download-1',
        artifactId: 'artifact-1',
        path: '/downloads/episode-1.mp4',
      );

      await repository.create(value);
      final loaded = await repository.findById(value.manifestId);

      expect(loaded, isNotNull);
      expect(loaded!.downloadId, value.downloadId);
      expect(loaded.artifacts.single.fileUri, value.artifacts.single.fileUri);
    },
  );

  test(
    'artifact conflict rolls back the whole new manifest transaction',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftArtifactManifestRepository(database);
      await repository.create(
        manifest(
          manifestId: 'manifest-1',
          downloadId: 'download-1',
          artifactId: 'artifact-1',
          path: '/downloads/shared.mp4',
        ),
      );

      await expectLater(
        repository.create(
          manifest(
            manifestId: 'manifest-2',
            downloadId: 'download-2',
            artifactId: 'artifact-2',
            path: '/downloads/shared.mp4',
          ),
        ),
        throwsA(anything),
      );

      expect(await repository.findById('manifest-2'), isNull);
    },
  );
}

import 'package:drift/drift.dart';

import '../../domain/models/download_artifact_manifest.dart';
import '../../domain/repositories/artifact_manifest_repository.dart';
import '../database/wynime_database.dart';

final class DriftArtifactManifestRepository
    implements ArtifactManifestRepository {
  DriftArtifactManifestRepository(this._database);

  final WynimeDatabase _database;

  @override
  Future<void> create(DownloadArtifactManifest manifest) {
    return _database.transaction(() async {
      await _database.into(_database.artifactManifests).insert(
        ArtifactManifestsCompanion(
          manifestId: Value(manifest.manifestId),
          downloadId: Value(manifest.downloadId),
          createdAt: Value(manifest.createdAt),
        ),
      );
      if (manifest.artifacts.isNotEmpty) {
        await _database.batch((batch) {
          batch.insertAll(
            _database.artifactRows,
            manifest.artifacts
                .map(
                  (artifact) => ArtifactRowsCompanion(
                    artifactId: Value(artifact.artifactId),
                    manifestId: Value(manifest.manifestId),
                    kind: Value(artifact.kind.name),
                    fileUri: Value(artifact.fileUri.toString()),
                  ),
                )
                .toList(growable: false),
          );
        });
      }
    });
  }

  @override
  Future<DownloadArtifactManifest?> findById(String manifestId) async {
    final manifestQuery = _database.select(_database.artifactManifests)
      ..where((table) => table.manifestId.equals(manifestId));
    final manifest = await manifestQuery.getSingleOrNull();
    if (manifest == null) {
      return null;
    }

    final artifactQuery = _database.select(_database.artifactRows)
      ..where((table) => table.manifestId.equals(manifestId))
      ..orderBy([(table) => OrderingTerm.asc(table.artifactId)]);
    final artifacts = await artifactQuery.get();

    return DownloadArtifactManifest(
      manifestId: manifest.manifestId,
      downloadId: manifest.downloadId,
      createdAt: manifest.createdAt,
      artifacts: artifacts.map(
        (artifact) => DownloadArtifact(
          artifactId: artifact.artifactId,
          kind: DownloadArtifactKind.values.byName(artifact.kind),
          fileUri: Uri.parse(artifact.fileUri),
        ),
      ),
    );
  }
}

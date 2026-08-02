import '../models/download_artifact_manifest.dart';

abstract interface class ArtifactManifestRepository {
  Future<void> create(DownloadArtifactManifest manifest);

  Future<DownloadArtifactManifest?> findById(String manifestId);
}

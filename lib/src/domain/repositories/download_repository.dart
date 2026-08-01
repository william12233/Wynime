import 'package:wynime/src/domain/models/delete_job.dart';
import 'package:wynime/src/domain/models/download_artifact_manifest.dart';
import 'package:wynime/src/domain/models/playback_session.dart';

abstract interface class DownloadRepository {
  Future<String> enqueue(PlaybackSession session);

  Future<DownloadArtifactManifest?> findArtifactManifest(String downloadId);

  Future<DeleteJob> requestDeletion(String artifactManifestId);
}

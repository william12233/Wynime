import 'dart:collection';

enum DownloadArtifactKind {
  finalVideo,
  temporarySegment,
  manifestSnapshot,
  subtitle,
  audioTrack,
  cover,
  adRemovalPlan,
  timelineMap,
  remuxTemporaryFile,
  diagnosticLog,
  recoveryState,
}

final class DownloadArtifact {
  DownloadArtifact({
    required this.artifactId,
    required this.kind,
    required this.fileUri,
  }) : assert(artifactId.trim().isNotEmpty, 'artifactId must not be empty.'),
       assert(fileUri.scheme == 'file', 'fileUri must use the file scheme.');

  final String artifactId;
  final DownloadArtifactKind kind;
  final Uri fileUri;
}

final class DownloadArtifactManifest {
  DownloadArtifactManifest({
    required this.manifestId,
    required this.downloadId,
    required this.createdAt,
    required Iterable<DownloadArtifact> artifacts,
  }) : assert(manifestId.trim().isNotEmpty, 'manifestId must not be empty.'),
       assert(downloadId.trim().isNotEmpty, 'downloadId must not be empty.'),
       artifacts = UnmodifiableListView(_validateArtifacts(artifacts));

  final String manifestId;
  final String downloadId;
  final DateTime createdAt;
  final UnmodifiableListView<DownloadArtifact> artifacts;

  static List<DownloadArtifact> _validateArtifacts(
    Iterable<DownloadArtifact> artifacts,
  ) {
    final result = List<DownloadArtifact>.unmodifiable(artifacts);
    final artifactIds = <String>{};
    final fileUris = <Uri>{};

    for (final artifact in result) {
      if (!artifactIds.add(artifact.artifactId)) {
        throw ArgumentError.value(
          artifact.artifactId,
          'artifacts',
          'Artifact IDs must be unique.',
        );
      }
      if (!fileUris.add(artifact.fileUri)) {
        throw ArgumentError.value(
          artifact.fileUri,
          'artifacts',
          'Every physical artifact path must be registered only once.',
        );
      }
    }

    return result;
  }
}

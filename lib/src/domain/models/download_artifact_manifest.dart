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
  }) {
    if (artifactId.trim().isEmpty) {
      throw ArgumentError.value(artifactId, 'artifactId', 'Must not be empty.');
    }
    if (fileUri.scheme != 'file' || !fileUri.isAbsolute) {
      throw ArgumentError.value(
        fileUri,
        'fileUri',
        'Must be an absolute file URI.',
      );
    }
  }

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
  }) : artifacts = UnmodifiableListView(_validateArtifacts(artifacts)) {
    if (manifestId.trim().isEmpty) {
      throw ArgumentError.value(manifestId, 'manifestId', 'Must not be empty.');
    }
    if (downloadId.trim().isEmpty) {
      throw ArgumentError.value(downloadId, 'downloadId', 'Must not be empty.');
    }
  }

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
          'Every physical artifact URI must be registered only once.',
        );
      }
    }

    return result;
  }
}

enum SoftwareUpdatePlatform { android, windows, unsupported }

final class SemanticVersion implements Comparable<SemanticVersion> {
  const SemanticVersion(this.major, this.minor, this.patch);

  factory SemanticVersion.parse(String value) {
    final match = RegExp(r'^v?(\d+)\.(\d+)\.(\d+)$').firstMatch(value.trim());
    if (match == null) {
      throw const FormatException('invalid_version');
    }
    return SemanticVersion(
      int.parse(match.group(1)!),
      int.parse(match.group(2)!),
      int.parse(match.group(3)!),
    );
  }

  final int major;
  final int minor;
  final int patch;

  @override
  int compareTo(SemanticVersion other) {
    final majorResult = major.compareTo(other.major);
    if (majorResult != 0) return majorResult;
    final minorResult = minor.compareTo(other.minor);
    if (minorResult != 0) return minorResult;
    return patch.compareTo(other.patch);
  }

  @override
  bool operator ==(Object other) =>
      other is SemanticVersion && compareTo(other) == 0;

  @override
  int get hashCode => Object.hash(major, minor, patch);

  @override
  String toString() => '$major.$minor.$patch';
}

final class AppVersionInfo {
  const AppVersionInfo({
    required this.version,
    required this.buildNumber,
    required this.platform,
    required this.architecture,
  });

  final String version;
  final String buildNumber;
  final SoftwareUpdatePlatform platform;
  final String architecture;

  SemanticVersion get semanticVersion => SemanticVersion.parse(version);

  String get displayVersion => buildNumber.isEmpty || buildNumber == '0'
      ? version
      : '$version+$buildNumber';
}

final class ReleaseAsset {
  const ReleaseAsset({
    required this.name,
    required this.downloadUrl,
    required this.size,
    this.digest,
    this.checksumAsset,
  });

  factory ReleaseAsset.fromJson(Map<String, dynamic> json) {
    final name = json['name'];
    final downloadUrl = json['browser_download_url'];
    final size = json['size'];
    if (name is! String || downloadUrl is! String || size is! int || size < 0) {
      throw const FormatException('invalid_release_asset');
    }
    return ReleaseAsset(
      name: name,
      downloadUrl: Uri.tryParse(downloadUrl),
      size: size,
      digest: json['digest'] is String ? json['digest'] as String : null,
    );
  }

  final String name;
  final Uri? downloadUrl;
  final int size;
  final String? digest;
  final ReleaseAsset? checksumAsset;

  String? get sha256 {
    final value = digest?.trim().toLowerCase();
    if (value == null || value.isEmpty) return null;
    final normalized = value.startsWith('sha256:')
        ? value.substring('sha256:'.length)
        : value;
    return RegExp(r'^[0-9a-f]{64}$').hasMatch(normalized) ? normalized : null;
  }
}

final class SoftwareRelease {
  const SoftwareRelease({
    required this.tagName,
    required this.version,
    required this.assets,
  });

  factory SoftwareRelease.fromJson(Map<String, dynamic> json) {
    final tagName = json['tag_name'];
    final assets = json['assets'];
    if (tagName is! String || assets is! List) {
      throw const FormatException('invalid_release_metadata');
    }
    if (json['draft'] == true || json['prerelease'] == true) {
      throw const SoftwareUpdateException(
        UpdateFailureReason.releaseUnavailable,
      );
    }
    return SoftwareRelease(
      tagName: tagName,
      version: SemanticVersion.parse(tagName),
      assets: List.unmodifiable(
        assets.whereType<Map>().map(
          (value) => ReleaseAsset.fromJson(Map<String, dynamic>.from(value)),
        ),
      ),
    );
  }

  final String tagName;
  final SemanticVersion version;
  final List<ReleaseAsset> assets;
}

enum UpdateStatus {
  idle,
  checking,
  upToDate,
  updateAvailable,
  unavailable,
  downloading,
  verifying,
  handingOff,
  manualUpdateRequired,
  failed,
  notSupported,
}

enum UpdateFailureReason {
  network,
  releaseUnavailable,
  assetUnavailable,
  invalidMetadata,
  download,
  integrity,
  unsafeArchive,
  installer,
  databaseRecovery,
  notSupported,
}

final class SoftwareUpdateException implements Exception {
  const SoftwareUpdateException(this.reason, [this.code = 'update_failed']);

  final UpdateFailureReason reason;
  final String code;

  @override
  String toString() => 'SoftwareUpdateException($reason, $code)';
}

final class SoftwareUpdateResult {
  const SoftwareUpdateResult({
    required this.status,
    required this.current,
    this.release,
    this.asset,
    this.error,
  });

  final UpdateStatus status;
  final AppVersionInfo current;
  final SoftwareRelease? release;
  final ReleaseAsset? asset;
  final SoftwareUpdateException? error;

  bool get hasUpdate => status == UpdateStatus.updateAvailable;
}

final class DownloadedUpdate {
  const DownloadedUpdate({
    required this.filePath,
    required this.stagingDirectoryPath,
    required this.release,
    required this.asset,
    this.extractedDirectoryPath,
  });

  final String filePath;
  final String stagingDirectoryPath;
  final SoftwareRelease release;
  final ReleaseAsset asset;
  final String? extractedDirectoryPath;
}

final class SoftwareInstallResult {
  const SoftwareInstallResult({
    required this.started,
    this.requiresUserAction = false,
    this.message,
  });

  final bool started;
  final bool requiresUserAction;
  final String? message;
}

abstract interface class SoftwareUpdateServicePort {
  Future<AppVersionInfo> currentVersion();

  Future<SoftwareUpdateResult> checkForUpdates();

  Future<DownloadedUpdate> download(
    SoftwareUpdateResult result, {
    UpdateProgressCallback? onProgress,
  });

  void dispose();
}

typedef UpdateProgressCallback = void Function(int received, int total);

abstract interface class SoftwareUpdateInstallerPort {
  Future<SoftwareInstallResult> install(DownloadedUpdate update);
}

abstract interface class AppVersionProvider {
  Future<AppVersionInfo> load();
}

abstract interface class DatabaseRecoveryPort {
  Future<DatabaseRecoveryPoint> createRecoveryPoint();

  /// Creates a consistent snapshot only after all admitted application writes
  /// have drained, then keeps future writes blocked until the handoff is
  /// either completed by process exit or explicitly aborted.
  Future<DatabaseRecoveryPoint> createRecoveryPointAndQuiesce();

  Future<void> resumeAfterAbortedHandoff(DatabaseRecoveryPoint point);

  Future<void> discardRecoveryPoint(DatabaseRecoveryPoint point);

  Future<void> restoreRecoveryPoint(DatabaseRecoveryPoint point);
}

final class DatabaseRecoveryPoint {
  const DatabaseRecoveryPoint({required this.identifier, this.databasePath});

  final String identifier;
  final String? databasePath;
}

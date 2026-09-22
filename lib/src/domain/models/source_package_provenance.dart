import 'package:pub_semver/pub_semver.dart';

/// The package identity used to validate persisted source-to-Bangumi links.
///
/// This contains no network location or request material. A mapping is valid
/// only when all three values exactly match the currently installed package.
final class SourcePackageProvenance {
  SourcePackageProvenance({
    required this.packageId,
    required this.version,
    required String revisionSha256,
  }) : revisionSha256 = _normalizeDigest(revisionSha256);

  final String packageId;
  final Version version;
  final String revisionSha256;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SourcePackageProvenance &&
          packageId == other.packageId &&
          version == other.version &&
          revisionSha256 == other.revisionSha256;

  @override
  int get hashCode => Object.hash(packageId, version, revisionSha256);

  static String _normalizeDigest(String value) {
    final normalized = value.toLowerCase();
    if (value != value.trim() ||
        !RegExp(r'^[0-9a-f]{64}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        'revisionSha256',
        'Must be a SHA-256 hexadecimal digest.',
      );
    }
    return normalized;
  }
}

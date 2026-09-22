import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

/// Immutable metadata for one declarative source package in a registry
/// snapshot.
///
/// [packagePath] is relative to the index's [SourceRegistryIndex.sourceRoot].
/// The normal layout starts with [packageId]. A bounded flat `packageId.*.json`
/// artifact is also accepted for the repository's existing package layout.
final class SourceRegistryEntry {
  factory SourceRegistryEntry({
    required String packageId,
    required Version version,
    required String packagePath,
    required String sha256,
  }) {
    final normalizedPackageId = _normalizePackageId(packageId);
    if (!isStrictSourceRegistrySemVer(version)) {
      throw ArgumentError.value(
        version,
        'version',
        'Must be a strict Semantic Versioning 2.0.0 value.',
      );
    }
    return SourceRegistryEntry._(
      packageId: normalizedPackageId,
      version: version,
      packagePath: _normalizePackagePath(packagePath, normalizedPackageId),
      sha256: _normalizeSha256(sha256),
    );
  }

  const SourceRegistryEntry._({
    required this.packageId,
    required this.version,
    required this.packagePath,
    required this.sha256,
  });

  final String packageId;
  final Version version;
  final String packagePath;
  final String sha256;

  static String _normalizePackageId(String value) {
    final normalized = value;
    if (normalized.length > 128 ||
        !RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        'packageId',
        'Must be a lower-case package identifier.',
      );
    }
    return normalized;
  }

  static String _normalizePackagePath(String value, String packageId) {
    final normalized = value;
    final segments = normalized.split('/');
    final validSegment = RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$');
    final isFlatPackageArtifact =
        segments.length == 1 &&
        segments.first.startsWith('$packageId.') &&
        segments.first.endsWith('.json');
    if (normalized.isEmpty ||
        normalized.length > 256 ||
        normalized.contains('\\') ||
        normalized.contains(':') ||
        normalized.contains('?') ||
        normalized.contains('#') ||
        _containsControl(normalized) ||
        (!isFlatPackageArtifact && segments.length < 2) ||
        segments.length > 8 ||
        (!isFlatPackageArtifact && segments.first != packageId) ||
        !segments.every(validSegment.hasMatch) ||
        !segments.last.endsWith('.json')) {
      throw ArgumentError.value(
        value,
        'packagePath',
        'Must be a bounded relative JSON path below the package ID directory.',
      );
    }
    return normalized;
  }

  static String _normalizeSha256(String value) {
    final normalized = value.toLowerCase();
    if (value != value.trim() ||
        !RegExp(r'^[0-9a-f]{64}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        'sha256',
        'Must be a 64-character SHA-256 hexadecimal digest.',
      );
    }
    return normalized;
  }
}

/// A bounded, strict registry snapshot. It describes package locations and
/// integrity metadata only; it does not fetch, execute or trust a package.
final class SourceRegistryIndex {
  static const maxIndexBytes = 64 * 1024;
  static const maxEntries = 256;

  factory SourceRegistryIndex({
    required int schemaVersion,
    required String sourceRoot,
    required String revision,
    required Iterable<SourceRegistryEntry> packages,
  }) {
    if (schemaVersion != 1) {
      throw ArgumentError.value(
        schemaVersion,
        'schemaVersion',
        'Only source registry schema version 1 is supported.',
      );
    }
    final normalizedRoot = _normalizeRelativeDirectory(sourceRoot);
    final normalizedRevision = _normalizeRevision(revision);
    final snapshot = packages.toList(growable: false);
    if (snapshot.isEmpty || snapshot.length > maxEntries) {
      throw ArgumentError.value(
        packages,
        'packages',
        'Must contain between 1 and $maxEntries packages.',
      );
    }
    final packageIds = <String>{};
    final packagePaths = <String>{};
    for (final package in snapshot) {
      if (!packageIds.add(package.packageId)) {
        throw ArgumentError.value(
          package.packageId,
          'packages',
          'Package IDs must be unique.',
        );
      }
      if (!packagePaths.add(package.packagePath)) {
        throw ArgumentError.value(
          package.packagePath,
          'packages',
          'Package paths must be unique.',
        );
      }
    }
    final sorted = List<SourceRegistryEntry>.from(snapshot)
      ..sort((left, right) {
        final byId = left.packageId.compareTo(right.packageId);
        if (byId != 0) return byId;
        return left.version.compareTo(right.version);
      });
    return SourceRegistryIndex._(
      schemaVersion: schemaVersion,
      sourceRoot: normalizedRoot,
      revision: normalizedRevision,
      packages: sorted,
    );
  }

  SourceRegistryIndex._({
    required this.schemaVersion,
    required this.sourceRoot,
    required this.revision,
    required List<SourceRegistryEntry> packages,
  }) : packages = UnmodifiableListView(packages);

  final int schemaVersion;
  final String sourceRoot;
  final String revision;
  final UnmodifiableListView<SourceRegistryEntry> packages;

  SourceRegistryEntry? packageById(String packageId) {
    for (final package in packages) {
      if (package.packageId == packageId) return package;
    }
    return null;
  }

  /// Returns the normalized relative path a future repository adapter may
  /// resolve under its separately authorized checkout or remote root.
  String relativePathFor(SourceRegistryEntry package) {
    if (packageById(package.packageId) != package) {
      throw ArgumentError.value(
        package,
        'package',
        'The package entry does not belong to this registry snapshot.',
      );
    }
    return '$sourceRoot/${package.packagePath}';
  }

  static String _normalizeRelativeDirectory(String value) {
    final normalized = value;
    final segments = normalized.split('/');
    final validSegment = RegExp(r'^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$');
    if (normalized.isEmpty ||
        normalized.length > 128 ||
        normalized.contains('\\') ||
        normalized.contains(':') ||
        _containsControl(normalized) ||
        segments.length > 4 ||
        !segments.every(validSegment.hasMatch)) {
      throw ArgumentError.value(
        value,
        'sourceRoot',
        'Must be a bounded relative directory path.',
      );
    }
    return normalized;
  }

  static String _normalizeRevision(String value) {
    final normalized = value;
    if (normalized.isEmpty ||
        normalized.length > 128 ||
        _containsControl(normalized) ||
        !RegExp(r'^[A-Za-z0-9][A-Za-z0-9._/-]{0,127}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        'revision',
        'Must be a bounded registry revision identifier.',
      );
    }
    return normalized;
  }
}

bool isStrictSourceRegistrySemVer(Version version) {
  return _strictSemVer.hasMatch(version.toString());
}

final class SourceRegistryIndexFormatException implements Exception {
  const SourceRegistryIndexFormatException(this.path, this.message);

  final String path;
  final String message;

  @override
  String toString() => 'SourceRegistryIndexFormatException($path): $message';
}

final class SourceRegistryPackageVerificationException implements Exception {
  const SourceRegistryPackageVerificationException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() =>
      'SourceRegistryPackageVerificationException($code): $message';
}

bool _containsControl(String value) {
  return value.codeUnits.any(
    (code) => code <= 0x1f || code == 0x7f || (code >= 0x80 && code <= 0x9f),
  );
}

final _strictSemVer = RegExp(
  r'^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
  r'(?:-(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'
  r'(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*)?'
  r'(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$',
);

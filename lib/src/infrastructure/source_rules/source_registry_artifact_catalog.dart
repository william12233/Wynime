import 'dart:collection';

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_registry_models.dart';
import 'source_registry_index_decoder.dart';
import 'source_registry_package_verifier.dart';

/// One package that has been verified against a registry snapshot.
///
/// The raw package bytes are deliberately not retained here. They are an
/// input to verification, while the decoded manifest is the only package
/// data exposed to the next typed boundary.
final class SourceRegistryCatalogPackage {
  const SourceRegistryCatalogPackage({
    required this.entry,
    required this.package,
  });

  final SourceRegistryEntry entry;
  final SourcePackageManifest package;
}

/// An immutable, all-or-nothing catalog for one registry snapshot.
///
/// This model contains verified package metadata only. Its constructor is
/// private to this library so callers must use the loader's verification path.
final class SourceRegistryCatalog {
  SourceRegistryCatalog._({
    required this.index,
    required Iterable<SourceRegistryCatalogPackage> packages,
  }) : packages = UnmodifiableListView(
         List<SourceRegistryCatalogPackage>.unmodifiable(packages),
       );

  final SourceRegistryIndex index;
  final UnmodifiableListView<SourceRegistryCatalogPackage> packages;

  SourceRegistryCatalogPackage? packageById(String packageId) {
    for (final package in packages) {
      if (package.entry.packageId == packageId) return package;
    }
    return null;
  }
}

/// Stable, secret-safe failure from the registry artifact catalog boundary.
final class SourceRegistryCatalogException implements Exception {
  const SourceRegistryCatalogException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'SourceRegistryCatalogException($code): $message';
}

/// Composes one registry index with the exact package artifacts it describes.
///
/// The input represents bytes already supplied by a future transport adapter.
/// This class performs no network, filesystem or repository work. A catalog is
/// returned only after every indexed artifact is present, every supplied key is
/// indexed and every package passes the existing byte-level integrity and
/// identity verifier.
final class SourceRegistryArtifactCatalogLoader {
  const SourceRegistryArtifactCatalogLoader({
    this._indexDecoder = const SourceRegistryIndexDecoder(),
    this._packageVerifier = const SourceRegistryPackageVerifier(),
  });

  final SourceRegistryIndexDecoder _indexDecoder;
  final SourceRegistryPackageVerifier _packageVerifier;

  SourceRegistryCatalog load({
    required List<int> indexBytes,
    required Map<String, List<int>> packageBytesByPath,
  }) {
    final SourceRegistryIndex index;
    try {
      index = _indexDecoder.decodeBytes(indexBytes);
    } on SourceRegistryIndexFormatException {
      throw const SourceRegistryCatalogException(
        'index_format_invalid',
        'The source registry index failed schema validation.',
      );
    } on Object {
      throw const SourceRegistryCatalogException(
        'index_format_invalid',
        'The source registry index failed schema validation.',
      );
    }

    final expectedPaths = <String>{
      for (final entry in index.packages) index.relativePathFor(entry),
    };
    if (packageBytesByPath.length > expectedPaths.length) {
      throw const SourceRegistryCatalogException(
        'artifact_unexpected',
        'The supplied package set contains an unindexed artifact.',
      );
    }
    for (final path in packageBytesByPath.keys) {
      if (!expectedPaths.contains(path)) {
        throw const SourceRegistryCatalogException(
          'artifact_unexpected',
          'The supplied package set contains an unindexed artifact.',
        );
      }
    }

    final verified = <SourceRegistryCatalogPackage>[];
    for (final entry in index.packages) {
      final path = index.relativePathFor(entry);
      final packageBytes = packageBytesByPath[path];
      if (packageBytes == null) {
        throw const SourceRegistryCatalogException(
          'artifact_missing',
          'The registry snapshot is missing an indexed package artifact.',
        );
      }
      try {
        final package = _packageVerifier.verifyBytes(
          entry: entry,
          packageBytes: packageBytes,
        );
        verified.add(
          SourceRegistryCatalogPackage(entry: entry, package: package),
        );
      } on SourceRegistryPackageVerificationException catch (error) {
        throw SourceRegistryCatalogException(
          error.code,
          'The indexed source package failed integrity or identity verification.',
        );
      } on Object {
        throw const SourceRegistryCatalogException(
          'artifact_verification_failed',
          'The indexed source package failed integrity or identity verification.',
        );
      }
    }

    return SourceRegistryCatalog._(index: index, packages: verified);
  }
}

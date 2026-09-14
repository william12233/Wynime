import 'dart:convert';

import 'package:crypto/crypto.dart';

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_registry_models.dart';
import 'source_package_decoder.dart';

/// Verifies the integrity and identity binding of one package body against an
/// already decoded registry entry.
///
/// This is intentionally a SHA-256 integrity check, not publisher signature
/// verification. A returned manifest still requires the normal package
/// manager consent and security-policy checks before activation.
final class SourceRegistryPackageVerifier {
  const SourceRegistryPackageVerifier({
    this._decoder = const SourcePackageDecoder(),
  });

  final SourcePackageDecoder _decoder;

  SourcePackageManifest verify({
    required SourceRegistryEntry entry,
    required String packageSource,
  }) {
    return verifyBytes(entry: entry, packageBytes: utf8.encode(packageSource));
  }

  /// Verifies the exact bytes supplied by a repository or transport adapter.
  ///
  /// The digest is computed before decoding so a registry entry is bound to
  /// the bytes actually received, not to a re-encoded Dart string.
  SourcePackageManifest verifyBytes({
    required SourceRegistryEntry entry,
    required List<int> packageBytes,
  }) {
    if (packageBytes.length > SourcePackageDecoder.maxPackageBytes) {
      throw const SourceRegistryPackageVerificationException(
        'package_too_large',
        'The source package exceeds its byte limit.',
      );
    }
    final actualDigest = sha256.convert(packageBytes).toString();
    if (actualDigest != entry.sha256) {
      throw const SourceRegistryPackageVerificationException(
        'package_integrity_mismatch',
        'The source package digest does not match the registry entry.',
      );
    }

    final String packageSource;
    try {
      packageSource = utf8.decode(packageBytes, allowMalformed: false);
    } on FormatException {
      throw const SourceRegistryPackageVerificationException(
        'package_format_invalid',
        'The source package failed UTF-8 validation.',
      );
    }
    final SourcePackageManifest package;
    try {
      package = _decoder.decode(packageSource);
    } on SourcePackageFormatException {
      throw const SourceRegistryPackageVerificationException(
        'package_format_invalid',
        'The source package failed schema validation.',
      );
    } on Object {
      throw const SourceRegistryPackageVerificationException(
        'package_format_invalid',
        'The source package failed schema validation.',
      );
    }
    if (package.packageId != entry.packageId ||
        package.version.toString() != entry.version.toString()) {
      throw const SourceRegistryPackageVerificationException(
        'package_metadata_mismatch',
        'The source package identity does not match the registry entry.',
      );
    }
    return package;
  }
}

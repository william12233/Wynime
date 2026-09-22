import 'dart:convert';

import 'package:crypto/crypto.dart';

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_package_provenance.dart';
import 'source_package_encoder.dart';

/// Computes the stable semantic revision used by source playback mappings.
///
/// The signature payload is authoritative: signature metadata, registry index
/// bytes and transport URLs are deliberately excluded from this identity.
final class SourcePackageRevisionCalculator {
  const SourcePackageRevisionCalculator({
    this.encoder = const SourcePackageEncoder(),
  });

  final SourcePackageEncoder encoder;

  SourcePackageProvenance calculate(SourcePackageManifest package) {
    final payload = encoder.encodeSignaturePayload(package);
    final digest = sha256.convert(utf8.encode(payload)).toString();
    return SourcePackageProvenance(
      packageId: package.packageId,
      version: package.version,
      revisionSha256: digest,
    );
  }
}

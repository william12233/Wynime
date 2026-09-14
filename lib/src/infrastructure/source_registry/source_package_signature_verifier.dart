import 'dart:async';
import 'dart:convert';

import 'package:cryptography/cryptography.dart';

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_package_signature_models.dart';
import '../source_rules/source_package_encoder.dart';

// Public constructor names intentionally remain stable while implementation
// fields stay private.
// ignore_for_file: prefer_initializing_formals

/// Verifies optional Ed25519 package signatures against an external trust
/// resolver. A verified result is identity evidence only; it does not change
/// package allowlists, permissions, consent or lifecycle authority.
final class SourcePackageSignatureVerifier {
  SourcePackageSignatureVerifier({
    required SourcePackageSignatureKeyResolver keyResolver,
    SourcePackageEncoder encoder = const SourcePackageEncoder(),
    Ed25519? algorithm,
    this.keyLookupTimeout = const Duration(seconds: 5),
  }) : _keyResolver = keyResolver,
       _encoder = encoder,
       _algorithm = algorithm ?? Ed25519() {
    if (keyLookupTimeout <= Duration.zero) {
      throw ArgumentError.value(
        keyLookupTimeout,
        'keyLookupTimeout',
        'Must be positive.',
      );
    }
  }

  final SourcePackageSignatureKeyResolver _keyResolver;
  final SourcePackageEncoder _encoder;
  final Ed25519 _algorithm;
  final Duration keyLookupTimeout;

  Future<SourcePackageSignatureVerification> verify(
    SourcePackageManifest package,
  ) async {
    final metadata = package.signatureMetadata;
    if (metadata == null) {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.unsigned,
        code: 'unsigned',
      );
    }
    if (metadata.algorithm != 'ed25519') {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.failed,
        code: 'signature_algorithm_unsupported',
      );
    }

    final TrustedSourcePackageSigningKey? key;
    try {
      key = await _keyResolver
          .resolve(
            packageId: package.packageId,
            keyId: metadata.keyId,
            signerId: metadata.declaredSignerId,
          )
          .timeout(keyLookupTimeout);
    } on TimeoutException {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.failed,
        code: 'signature_key_lookup_timeout',
      );
    } on Object {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.failed,
        code: 'signature_key_lookup_failed',
      );
    }
    if (key == null ||
        key.packageId != package.packageId ||
        key.keyId != metadata.keyId) {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.keyNotTrusted,
        code: 'signature_key_not_trusted',
      );
    }
    if (key.signerId != metadata.declaredSignerId) {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.signerMismatch,
        code: 'signature_signer_mismatch',
      );
    }

    final List<int> signatureBytes;
    try {
      signatureBytes = base64Decode(metadata.signatureBase64);
      if (signatureBytes.length != 64) {
        throw const FormatException('Invalid signature length.');
      }
    } on Object {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.invalid,
        code: 'signature_invalid',
      );
    }

    final List<int> payload;
    try {
      payload = utf8.encode(_encoder.encodeSignaturePayload(package));
    } on Object {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.failed,
        code: 'signature_payload_invalid',
      );
    }

    try {
      final valid = await _algorithm.verify(
        payload,
        signature: Signature(
          signatureBytes,
          publicKey: SimplePublicKey(
            key.publicKeyBytes,
            type: KeyPairType.ed25519,
          ),
        ),
      );
      return valid
          ? SourcePackageSignatureVerification(
              status: SourcePackageSignatureStatus.verified,
              code: 'signature_verified',
            )
          : SourcePackageSignatureVerification(
              status: SourcePackageSignatureStatus.invalid,
              code: 'signature_invalid',
            );
    } on Object {
      return SourcePackageSignatureVerification(
        status: SourcePackageSignatureStatus.failed,
        code: 'signature_verification_failed',
      );
    }
  }
}

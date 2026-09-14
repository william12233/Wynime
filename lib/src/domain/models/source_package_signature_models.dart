/// An Ed25519 public key explicitly trusted by the application boundary.
///
/// The package cannot provide or select this key. The key resolver supplies a
/// package-scoped record, so a signature's declared identity is checked before
/// any cryptographic result is reported.
final class TrustedSourcePackageSigningKey {
  TrustedSourcePackageSigningKey({
    required String packageId,
    required String keyId,
    required String signerId,
    required List<int> publicKeyBytes,
  }) : packageId = _requirePackageId(packageId),
       keyId = _requireIdentifier(keyId, 'keyId'),
       signerId = _requireIdentifier(signerId, 'signerId'),
       publicKeyBytes = List<int>.unmodifiable(publicKeyBytes) {
    if (this.publicKeyBytes.length != 32 ||
        this.publicKeyBytes.any((byte) => byte < 0 || byte > 255)) {
      throw ArgumentError.value(
        publicKeyBytes,
        'publicKeyBytes',
        'An Ed25519 public key must contain exactly 32 unsigned bytes.',
      );
    }
  }

  final String packageId;
  final String keyId;
  final String signerId;
  final List<int> publicKeyBytes;
}

/// Resolves trusted public-key material outside the source package payload.
abstract interface class SourcePackageSignatureKeyResolver {
  Future<TrustedSourcePackageSigningKey?> resolve({
    required String packageId,
    required String keyId,
    required String signerId,
  });
}

enum SourcePackageSignatureStatus {
  unsigned,
  verified,
  keyNotTrusted,
  signerMismatch,
  invalid,
  failed,
}

/// A bounded, non-authoritative result from signature verification.
final class SourcePackageSignatureVerification {
  SourcePackageSignatureVerification({
    required this.status,
    required String code,
  }) : code = _requireCode(code);

  final SourcePackageSignatureStatus status;
  final String code;

  bool get isVerified => status == SourcePackageSignatureStatus.verified;
}

String _requirePackageId(String value) {
  if (value.isEmpty ||
      value.length > 128 ||
      !RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(value)) {
    throw ArgumentError.value(
      value,
      'packageId',
      'Package identity is invalid.',
    );
  }
  return value;
}

String _requireIdentifier(String value, String field) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.length > 128) {
    throw ArgumentError.value(
      value,
      field,
      'Must contain between 1 and 128 characters.',
    );
  }
  return normalized;
}

String _requireCode(String value) {
  final normalized = value.trim();
  if (!RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(normalized)) {
    throw ArgumentError.value(
      value,
      'code',
      'Must be a bounded lower-case diagnostic code.',
    );
  }
  return normalized;
}

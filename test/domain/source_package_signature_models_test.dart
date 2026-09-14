import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_package_signature_models.dart';

void main() {
  test('trusted key normalizes identity and keeps public bytes immutable', () {
    final original = List<int>.filled(32, 7);
    final key = TrustedSourcePackageSigningKey(
      packageId: 'example.anime',
      keyId: ' key-1 ',
      signerId: ' signer@example.com ',
      publicKeyBytes: original,
    );

    original[0] = 9;

    expect(key.packageId, 'example.anime');
    expect(key.keyId, 'key-1');
    expect(key.signerId, 'signer@example.com');
    expect(key.publicKeyBytes.first, 7);
    expect(() => key.publicKeyBytes[0] = 9, throwsUnsupportedError);
  });

  test('trusted key rejects invalid identities and key bytes', () {
    expect(
      () => TrustedSourcePackageSigningKey(
        packageId: 'Example.Anime',
        keyId: 'key-1',
        signerId: 'signer',
        publicKeyBytes: List<int>.filled(32, 7),
      ),
      throwsArgumentError,
    );
    expect(
      () => TrustedSourcePackageSigningKey(
        packageId: 'example.anime',
        keyId: 'key-1',
        signerId: 'signer',
        publicKeyBytes: List<int>.filled(31, 7),
      ),
      throwsArgumentError,
    );
    expect(
      () => TrustedSourcePackageSigningKey(
        packageId: 'example.anime',
        keyId: 'key-1',
        signerId: 'signer',
        publicKeyBytes: [...List<int>.filled(31, 7), 256],
      ),
      throwsArgumentError,
    );
    expect(
      () => TrustedSourcePackageSigningKey(
        packageId: 'example.anime',
        keyId: ' ',
        signerId: 'signer',
        publicKeyBytes: List<int>.filled(32, 7),
      ),
      throwsArgumentError,
    );
  });

  test('verification result exposes only a bounded status and code', () {
    final verified = SourcePackageSignatureVerification(
      status: SourcePackageSignatureStatus.verified,
      code: 'verified',
    );
    final unsigned = SourcePackageSignatureVerification(
      status: SourcePackageSignatureStatus.unsigned,
      code: 'unsigned',
    );

    expect(verified.isVerified, isTrue);
    expect(unsigned.isVerified, isFalse);
    expect(SourcePackageSignatureStatus.values, contains(verified.status));
  });
}

import 'dart:async';
import 'dart:convert';

import 'package:cryptography/cryptography.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_package_signature_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/infrastructure/source_registry/source_package_signature_verifier.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  late Ed25519 algorithm;
  late SimpleKeyPair keyPair;

  setUpAll(() async {
    algorithm = Ed25519();
    keyPair = await algorithm.newKeyPairFromSeed(
      List<int>.generate(32, (index) => index + 1),
    );
  });

  test('unsigned packages return an explicit non-error result', () async {
    final resolver = _FakeKeyResolver();
    final verifier = SourcePackageSignatureVerifier(keyResolver: resolver);

    final result = await verifier.verify(_package());

    expect(result.status, SourcePackageSignatureStatus.unsigned);
    expect(result.code, 'unsigned');
    expect(result.isVerified, isFalse);
    expect(resolver.request, isNull);
  });

  test(
    'verifies an Ed25519 signature over the canonical unsigned payload',
    () async {
      final unsigned = _package();
      final payload = utf8.encode(
        const SourcePackageEncoder().encodeSignaturePayload(unsigned),
      );
      final signature = await algorithm.sign(payload, keyPair: keyPair);
      final resolver = _FakeKeyResolver(
        key: _trustedKey(await keyPair.extractPublicKey()),
      );
      final verifier = SourcePackageSignatureVerifier(keyResolver: resolver);

      final result = await verifier.verify(
        _package(
          signature: SourcePackageSignatureMetadata(
            declaredSignerId: 'signer@example.com',
            keyId: 'key-1',
            algorithm: 'ed25519',
            signatureBase64: base64Encode(signature.bytes),
          ),
        ),
      );

      expect(result.status, SourcePackageSignatureStatus.verified);
      expect(result.code, 'signature_verified');
      expect(result.isVerified, isTrue);
      expect(resolver.request, (
        packageId: 'example.anime',
        keyId: 'key-1',
        signerId: 'signer@example.com',
      ));
      expect(
        jsonDecode(
          const SourcePackageEncoder().encodeSignaturePayload(unsigned),
        ),
        isNot(contains('signature')),
      );
    },
  );

  test('a changed package fails signature verification', () async {
    final unsigned = _package();
    final signature = await algorithm.sign(
      utf8.encode(
        const SourcePackageEncoder().encodeSignaturePayload(unsigned),
      ),
      keyPair: keyPair,
    );
    final resolver = _FakeKeyResolver(
      key: _trustedKey(await keyPair.extractPublicKey()),
    );
    final verifier = SourcePackageSignatureVerifier(keyResolver: resolver);

    final result = await verifier.verify(
      _package(
        displayName: 'Changed Anime',
        signature: _signature(signature.bytes),
      ),
    );

    expect(result.status, SourcePackageSignatureStatus.invalid);
    expect(result.code, 'signature_invalid');
    expect(result.toString(), isNot(contains('Changed Anime')));
  });

  test('missing or mismatched trusted identity never verifies', () async {
    final signature = await _signatureFor(_package());
    final missing = SourcePackageSignatureVerifier(
      keyResolver: _FakeKeyResolver(),
    );
    final missingResult = await missing.verify(_package(signature: signature));
    expect(missingResult.status, SourcePackageSignatureStatus.keyNotTrusted);
    expect(missingResult.code, 'signature_key_not_trusted');

    final mismatched = SourcePackageSignatureVerifier(
      keyResolver: _FakeKeyResolver(
        key: TrustedSourcePackageSigningKey(
          packageId: 'example.anime',
          keyId: 'key-1',
          signerId: 'other-signer',
          publicKeyBytes: (await keyPair.extractPublicKey()).bytes,
        ),
      ),
    );
    final mismatchResult = await mismatched.verify(
      _package(signature: signature),
    );
    expect(mismatchResult.status, SourcePackageSignatureStatus.signerMismatch);
    expect(mismatchResult.code, 'signature_signer_mismatch');
  });

  test(
    'trusted package and key identity must match the package metadata',
    () async {
      final signature = await _signatureFor(_package());
      final publicKey = await keyPair.extractPublicKey();

      final packageMismatch = SourcePackageSignatureVerifier(
        keyResolver: _FakeKeyResolver(
          key: _trustedKey(publicKey, packageId: 'other.anime'),
        ),
      );
      final packageResult = await packageMismatch.verify(
        _package(signature: signature),
      );
      expect(packageResult.status, SourcePackageSignatureStatus.keyNotTrusted);
      expect(packageResult.code, 'signature_key_not_trusted');

      final keyMismatch = SourcePackageSignatureVerifier(
        keyResolver: _FakeKeyResolver(
          key: _trustedKey(publicKey, keyId: 'other-key'),
        ),
      );
      final keyResult = await keyMismatch.verify(
        _package(signature: signature),
      );
      expect(keyResult.status, SourcePackageSignatureStatus.keyNotTrusted);
      expect(keyResult.code, 'signature_key_not_trusted');
    },
  );

  test(
    'invalid signature bytes and resolver failures remain bounded',
    () async {
      final resolver = _FakeKeyResolver(
        key: _trustedKey(await keyPair.extractPublicKey()),
      );
      final verifier = SourcePackageSignatureVerifier(keyResolver: resolver);
      final invalidResult = await verifier.verify(
        _package(signature: _signature(List<int>.filled(64, 8))),
      );
      expect(invalidResult.status, SourcePackageSignatureStatus.invalid);
      expect(invalidResult.code, 'signature_invalid');

      final failingVerifier = SourcePackageSignatureVerifier(
        keyResolver: _FakeKeyResolver(
          error: StateError('private resolver detail'),
        ),
      );
      final failedResult = await failingVerifier.verify(
        _package(signature: _signature(List<int>.filled(64, 8))),
      );
      expect(failedResult.status, SourcePackageSignatureStatus.failed);
      expect(failedResult.code, 'signature_key_lookup_failed');
      expect(failedResult.code, isNot(contains('private')));

      final timeoutVerifier = SourcePackageSignatureVerifier(
        keyLookupTimeout: const Duration(milliseconds: 1),
        keyResolver: _FakeKeyResolver(neverCompletes: true),
      );
      final timeoutResult = await timeoutVerifier.verify(
        _package(signature: _signature(List<int>.filled(64, 8))),
      );
      expect(timeoutResult.status, SourcePackageSignatureStatus.failed);
      expect(timeoutResult.code, 'signature_key_lookup_timeout');
    },
  );
}

SourcePackageSignatureMetadata _signature(List<int> bytes) {
  return SourcePackageSignatureMetadata(
    declaredSignerId: 'signer@example.com',
    keyId: 'key-1',
    algorithm: 'ed25519',
    signatureBase64: base64Encode(bytes),
  );
}

Future<SourcePackageSignatureMetadata> _signatureFor(
  SourcePackageManifest package,
) async {
  final algorithm = Ed25519();
  final keyPair = await algorithm.newKeyPairFromSeed(
    List<int>.generate(32, (index) => index + 1),
  );
  final signature = await algorithm.sign(
    utf8.encode(const SourcePackageEncoder().encodeSignaturePayload(package)),
    keyPair: keyPair,
  );
  return _signature(signature.bytes);
}

TrustedSourcePackageSigningKey _trustedKey(
  SimplePublicKey publicKey, {
  String packageId = 'example.anime',
  String keyId = 'key-1',
  String signerId = 'signer@example.com',
}) {
  return TrustedSourcePackageSigningKey(
    packageId: packageId,
    keyId: keyId,
    signerId: signerId,
    publicKeyBytes: publicKey.bytes,
  );
}

SourcePackageManifest _package({
  String displayName = 'Example Anime',
  SourcePackageSignatureMetadata? signature,
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'example.anime',
    displayName: displayName,
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
    securityPolicy: testSourcePolicy(),
    programs: [
      SourceRuleProgram(
        programId: 'search',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: '.item',
        ),
        resultLimit: 10,
        fields: [
          SourceFieldRule(
            name: 'title',
            valueKind: SourceValueKind.text,
            required: true,
          ),
        ],
      ),
    ],
    signatureMetadata: signature,
  );
}

final class _FakeKeyResolver implements SourcePackageSignatureKeyResolver {
  _FakeKeyResolver({this.key, this.error, this.neverCompletes = false});

  final TrustedSourcePackageSigningKey? key;
  final Object? error;
  final bool neverCompletes;
  ({String packageId, String keyId, String signerId})? request;

  @override
  Future<TrustedSourcePackageSigningKey?> resolve({
    required String packageId,
    required String keyId,
    required String signerId,
  }) async {
    request = (packageId: packageId, keyId: keyId, signerId: signerId);
    if (neverCompletes) {
      return Completer<TrustedSourcePackageSigningKey?>().future;
    }
    final failure = error;
    if (failure != null) throw failure;
    return key;
  }
}

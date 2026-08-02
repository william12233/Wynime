import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  SourceRuleProgram program({
    String programId = 'search',
    int resultLimit = 10,
    SourceRegexCapture? capture,
  }) {
    return SourceRuleProgram(
      programId: programId,
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.item',
      ),
      resultLimit: resultLimit,
      fields: [
        SourceFieldRule(
          name: 'title',
          valueKind: SourceValueKind.text,
          required: true,
          regexCapture: capture,
        ),
      ],
    );
  }

  test('direct signature metadata enforces Ed25519 shape', () {
    final valid = SourcePackageSignatureMetadata(
      declaredSignerId: 'author@example.com',
      keyId: 'key-1',
      algorithm: 'ed25519',
      signatureBase64: base64Encode(List<int>.filled(64, 7)),
    );
    expect(valid.algorithm, 'ed25519');

    expect(
      () => SourcePackageSignatureMetadata(
        declaredSignerId: 'author',
        keyId: 'key-1',
        algorithm: 'rsa',
        signatureBase64: base64Encode(List<int>.filled(64, 7)),
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePackageSignatureMetadata(
        declaredSignerId: 'author',
        keyId: 'key-1',
        algorithm: 'ed25519',
        signatureBase64: base64Encode([1, 2, 3]),
      ),
      throwsArgumentError,
    );
  });

  test('package construction enforces record and regex budgets', () {
    final version = Version.parse('1.0.0');
    final constraint = VersionConstraint.parse('^1.0.0');
    final policy = testSourcePolicy(
      budget: testSourceBudget(maxRecords: 2, maxRegexPatternChars: 4),
    );

    expect(
      () => SourcePackageManifest(
        schemaVersion: 1,
        packageId: 'example.anime',
        displayName: 'Example Anime',
        version: version,
        wynimeVersionConstraint: constraint,
        securityPolicy: policy,
        programs: [program(resultLimit: 3)],
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePackageManifest(
        schemaVersion: 1,
        packageId: 'example.anime',
        displayName: 'Example Anime',
        version: version,
        wynimeVersionConstraint: constraint,
        securityPolicy: policy,
        programs: [program(capture: SourceRegexCapture(pattern: r'^(\d+)$'))],
      ),
      throwsArgumentError,
    );
  });

  test('package and program declaration counts are bounded', () {
    final version = Version.parse('1.0.0');
    final constraint = VersionConstraint.parse('^1.0.0');

    expect(
      () => SourceRuleProgram(
        programId: 'oversized',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: '.item',
        ),
        resultLimit: 1,
        fields: List.generate(
          65,
          (index) => SourceFieldRule(
            name: 'field$index',
            valueKind: SourceValueKind.text,
          ),
        ),
      ),
      throwsArgumentError,
    );

    expect(
      () => SourcePackageManifest(
        schemaVersion: 1,
        packageId: 'example.anime',
        displayName: 'Example Anime',
        version: version,
        wynimeVersionConstraint: constraint,
        securityPolicy: testSourcePolicy(),
        programs: List.generate(
          33,
          (index) => program(programId: 'program$index'),
        ),
      ),
      throwsArgumentError,
    );
  });
}

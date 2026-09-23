import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';

void main() {
  const decoder = SourcePackageDecoder();

  Map<String, Object?> validPackage() {
    return {
      'schemaVersion': 1,
      'packageId': 'example.anime',
      'displayName': 'Example Anime',
      'version': '1.2.0',
      'wynimeVersion': '^1.0.0',
      'security': {
        'domains': [
          {
            'host': 'example.com',
            'includeSubdomains': true,
            'schemes': ['https'],
          },
        ],
        'permissions': ['network'],
        'budget': {
          'maxDocumentBytes': 65536,
          'maxRecords': 20,
          'maxSelectorMatches': 100,
          'maxEvaluationSteps': 1000,
          'maxRegexPatternChars': 128,
          'maxRegexInputChars': 1024,
          'maxRedirects': 3,
        },
      },
      'programs': [
        {
          'id': 'search',
          'documentKind': 'html',
          'root': {'type': 'css', 'expression': '.anime-card'},
          'resultLimit': 20,
          'fields': [
            {
              'name': 'title',
              'selector': {'type': 'css', 'expression': '.title'},
              'value': 'text',
              'required': true,
            },
          ],
        },
      ],
    };
  }

  test('strict decoder creates a compatible source package', () {
    final package = decoder.decode(jsonEncode(validPackage()));

    expect(package.packageId, 'example.anime');
    expect(package.version, Version.parse('1.2.0'));
    expect(package.isCompatibleWith(Version.parse('1.5.0')), isTrue);
    expect(package.isCompatibleWith(Version.parse('2.0.0')), isFalse);
    expect(package.programById('search').resultLimit, 20);
  });

  test('signature metadata does not alter the security policy', () {
    final unsigned = decoder.decode(jsonEncode(validPackage()));
    final signedMap = validPackage()
      ..['signature'] = {
        'declaredSignerId': 'author@example.com',
        'keyId': 'key-1',
        'algorithm': 'ed25519',
        'signatureBase64': base64Encode(List<int>.filled(64, 7)),
      };
    final signed = decoder.decode(jsonEncode(signedMap));

    expect(signed.signatureMetadata, isNotNull);
    expect(
      signed.securityPolicy.allowsUri(Uri.parse('https://api.example.com')),
      unsigned.securityPolicy.allowsUri(Uri.parse('https://api.example.com')),
    );
    expect(
      signed.securityPolicy.permissions,
      unorderedEquals(unsigned.securityPolicy.permissions),
    );
  });

  test('decodes an exact non-standard domain port', () {
    final map = validPackage();
    final security = map['security']! as Map<String, Object?>;
    final domains = security['domains']! as List<Object?>;
    (domains.single as Map<String, Object?>)['ports'] = [30443];

    final package = decoder.decode(jsonEncode(map));
    final rule = package.securityPolicy.allowedDomains.single;

    expect(rule.ports, {30443});
    expect(
      package.securityPolicy.allowsUri(
        Uri.parse('https://example.com:30443/video'),
      ),
      isTrue,
    );
    expect(
      package.securityPolicy.allowsUri(Uri.parse('https://example.com/video')),
      isFalse,
    );
  });

  test('encoder preserves an exact non-standard domain port', () {
    final map = validPackage();
    final security = map['security']! as Map<String, Object?>;
    final domains = security['domains']! as List<Object?>;
    (domains.single as Map<String, Object?>)['ports'] = [30443];
    final package = decoder.decode(jsonEncode(map));

    final encoded = jsonDecode(const SourcePackageEncoder().encode(package));
    final encodedSecurity = encoded['security'] as Map<String, Object?>;
    final encodedDomains = encodedSecurity['domains'] as List<Object?>;

    expect((encodedDomains.single as Map<String, Object?>)['ports'], [30443]);
  });

  test('unknown keys fail instead of being silently ignored', () {
    final map = validPackage()..['executable'] = 'main.dart';

    expect(
      () => decoder.decode(jsonEncode(map)),
      throwsA(isA<SourcePackageFormatException>()),
    );
  });

  test('XPath fails explicitly in schema version 1', () {
    final map = validPackage();
    final program = (map['programs']! as List).single as Map<String, Object?>;
    program['root'] = {'type': 'xpath', 'expression': '//article'};

    expect(
      () => decoder.decode(jsonEncode(map)),
      throwsA(
        isA<SourcePackageFormatException>().having(
          (error) => error.message,
          'message',
          contains('XPath is intentionally unsupported'),
        ),
      ),
    );
  });

  test('malformed signature metadata is rejected', () {
    final map = validPackage()
      ..['signature'] = {
        'declaredSignerId': 'author',
        'keyId': 'key-1',
        'algorithm': 'ed25519',
        'signatureBase64': base64Encode([1, 2, 3]),
      };

    expect(
      () => decoder.decode(jsonEncode(map)),
      throwsA(isA<SourcePackageFormatException>()),
    );
  });
}

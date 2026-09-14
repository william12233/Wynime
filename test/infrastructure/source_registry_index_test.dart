import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_registry_models.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_index_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_index_encoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_package_verifier.dart';

void main() {
  const decoder = SourceRegistryIndexDecoder();
  const encoder = SourceRegistryIndexEncoder();
  const verifier = SourceRegistryPackageVerifier();

  test('decodes, sorts and re-encodes a strict registry snapshot', () {
    final index = SourceRegistryIndex(
      schemaVersion: 1,
      sourceRoot: 'sources',
      revision: 'a1b2c3d4',
      packages: [
        _entry('zeta.anime', 'zeta.anime/package.json'),
        _entry('alpha.anime', 'alpha.anime/package.json'),
      ],
    );
    final encoded = encoder.encode(index);
    final decoded = decoder.decodeBytes(utf8.encode(encoded));

    expect(decoded.packages.map((package) => package.packageId), [
      'alpha.anime',
      'zeta.anime',
    ]);
    expect(decoded.revision, 'a1b2c3d4');
    expect(
      decoded.relativePathFor(decoded.packages.first),
      'sources/alpha.anime/package.json',
    );
    expect(encoder.encode(decoded), encoded);
    expect(decoded.packageById('missing'), isNull);
  });

  test('registry package listings are immutable', () {
    final index = SourceRegistryIndex(
      schemaVersion: 1,
      sourceRoot: 'sources',
      revision: 'main',
      packages: [_entry('example.anime', 'example.anime/package.json')],
    );

    expect(
      () =>
          index.packages.add(_entry('other.anime', 'other.anime/package.json')),
      throwsUnsupportedError,
    );
  });

  test('unknown index and entry keys fail closed', () {
    final root = _indexMap([_entryMap('example.anime')]);
    root['unexpected'] = true;
    expect(
      () => decoder.decode(jsonEncode(root)),
      throwsA(isA<SourceRegistryIndexFormatException>()),
    );

    final entry = _entryMap('example.anime');
    entry['unexpected'] = true;
    final withUnknownEntry = _indexMap([entry]);
    expect(
      () => decoder.decode(jsonEncode(withUnknownEntry)),
      throwsA(isA<SourceRegistryIndexFormatException>()),
    );
  });

  test('rejects malformed UTF-8 index bytes', () {
    expect(
      () => decoder.decodeBytes([0x7b, 0xff, 0x7d]),
      throwsA(isA<SourceRegistryIndexFormatException>()),
    );
  });

  test('unsafe package paths fail closed', () {
    for (final unsafePath in [
      '../example.anime/package.json',
      '/example.anime/package.json',
      'example.anime\\package.json',
      'other.anime/package.json',
      'example.anime/package.txt',
      'example.anime/../package.json',
    ]) {
      final entry = _entryMap('example.anime')..['path'] = unsafePath;
      expect(
        () => decoder.decode(jsonEncode(_indexMap([entry]))),
        throwsA(isA<SourceRegistryIndexFormatException>()),
        reason: unsafePath,
      );
    }
  });

  test('duplicate package IDs and paths fail closed', () {
    final duplicateIds = _indexMap([
      _entryMap('example.anime'),
      _entryMap('example.anime'),
    ]);
    expect(
      () => decoder.decode(jsonEncode(duplicateIds)),
      throwsA(isA<SourceRegistryIndexFormatException>()),
    );

    final duplicatePaths = _indexMap([
      _entryMap('example.anime'),
      _entryMap('other.anime')..['path'] = 'example.anime/package.json',
    ]);
    expect(
      () => decoder.decode(jsonEncode(duplicatePaths)),
      throwsA(isA<SourceRegistryIndexFormatException>()),
    );
  });

  test(
    'invalid revisions, hashes, versions and package counts fail closed',
    () {
      final invalidHash = _entryMap('example.anime')..['sha256'] = 'not-a-hash';
      expect(
        () => decoder.decode(jsonEncode(_indexMap([invalidHash]))),
        throwsA(isA<SourceRegistryIndexFormatException>()),
      );

      for (final invalidVersion in [
        'latest',
        '01.2.3',
        '1.02.3',
        '1.2.03',
        '1.2.3-01',
      ]) {
        final entry = _entryMap('example.anime')..['version'] = invalidVersion;
        expect(
          () => decoder.decode(jsonEncode(_indexMap([entry]))),
          throwsA(isA<SourceRegistryIndexFormatException>()),
          reason: invalidVersion,
        );
      }

      final invalidRevision = _indexMap([_entryMap('example.anime')])
        ..['revision'] = '../main';
      expect(
        () => decoder.decode(jsonEncode(invalidRevision)),
        throwsA(isA<SourceRegistryIndexFormatException>()),
      );

      final tooMany = List<Object?>.generate(
        SourceRegistryIndex.maxEntries + 1,
        (index) => _entryMap('source-$index.anime'),
      );
      expect(
        () => decoder.decode(jsonEncode(_indexMap(tooMany))),
        throwsA(isA<SourceRegistryIndexFormatException>()),
      );

      final oversized = _indexMap([_entryMap('example.anime')])
        ..['padding'] = List<String>.filled(70000, 'x').join();
      expect(
        () => decoder.decode(jsonEncode(oversized)),
        throwsA(isA<SourceRegistryIndexFormatException>()),
      );
    },
  );

  test('rejects non-canonical surrounding whitespace', () {
    final cases = <Map<String, Object?>>[
      _indexMap([_entryMap('example.anime')])..['sourceRoot'] = ' sources',
      _indexMap([_entryMap('example.anime')])..['revision'] = 'main ',
      _indexMap([_entryMap('example.anime')..['packageId'] = ' example.anime']),
      _indexMap([
        _entryMap('example.anime')..['path'] = 'example.anime/package.json ',
      ]),
    ];
    for (final value in cases) {
      expect(
        () => decoder.decode(jsonEncode(value)),
        throwsA(isA<SourceRegistryIndexFormatException>()),
      );
    }
  });

  test('verifies package integrity and exact package identity', () {
    final packageSource = jsonEncode(_validPackage());
    final packageBytes = utf8.encode(packageSource);
    final entry = SourceRegistryEntry(
      packageId: 'example.anime',
      version: Version.parse('1.2.0'),
      packagePath: 'example.anime/package.json',
      sha256: sha256.convert(packageBytes).toString().toUpperCase(),
    );

    final package = verifier.verifyBytes(
      entry: entry,
      packageBytes: packageBytes,
    );

    expect(package.packageId, 'example.anime');
    expect(package.version, Version.parse('1.2.0'));
    expect(entry.sha256, entry.sha256.toLowerCase());
  });

  test('rejects changed package bytes before parsing', () {
    final packageSource = jsonEncode(_validPackage());
    final entry = _entry(
      'example.anime',
      'example.anime/package.json',
      source: packageSource,
    );

    expect(
      () => verifier.verify(entry: entry, packageSource: '$packageSource '),
      throwsA(
        isA<SourceRegistryPackageVerificationException>().having(
          (error) => error.code,
          'code',
          'package_integrity_mismatch',
        ),
      ),
    );
  });

  test(
    'rejects package identity or version mismatches after integrity check',
    () {
      final packageSource = jsonEncode(_validPackage());
      final wrongId = SourceRegistryEntry(
        packageId: 'other.anime',
        version: Version.parse('1.2.0'),
        packagePath: 'other.anime/package.json',
        sha256: sha256.convert(utf8.encode(packageSource)).toString(),
      );
      expect(
        () => verifier.verify(entry: wrongId, packageSource: packageSource),
        throwsA(
          isA<SourceRegistryPackageVerificationException>().having(
            (error) => error.code,
            'code',
            'package_metadata_mismatch',
          ),
        ),
      );

      final wrongVersion = SourceRegistryEntry(
        packageId: 'example.anime',
        version: Version.parse('2.0.0'),
        packagePath: 'example.anime/package.json',
        sha256: sha256.convert(utf8.encode(packageSource)).toString(),
      );
      expect(
        () =>
            verifier.verify(entry: wrongVersion, packageSource: packageSource),
        throwsA(isA<SourceRegistryPackageVerificationException>()),
      );

      final nonCanonicalPackageIdSource = jsonEncode(
        _validPackage()..['packageId'] = ' example.anime ',
      );
      final nonCanonicalPackageIdEntry = SourceRegistryEntry(
        packageId: 'example.anime',
        version: Version.parse('1.2.0'),
        packagePath: 'example.anime/package.json',
        sha256: sha256
            .convert(utf8.encode(nonCanonicalPackageIdSource))
            .toString(),
      );
      expect(
        () => verifier.verify(
          entry: nonCanonicalPackageIdEntry,
          packageSource: nonCanonicalPackageIdSource,
        ),
        throwsA(
          isA<SourceRegistryPackageVerificationException>().having(
            (error) => error.code,
            'code',
            'package_format_invalid',
          ),
        ),
      );

      final buildMetadataSource = jsonEncode(
        _validPackage()..['version'] = '1.2.0+01',
      );
      final buildMetadataEntry = SourceRegistryEntry(
        packageId: 'example.anime',
        version: Version.parse('1.2.0+1'),
        packagePath: 'example.anime/package.json',
        sha256: sha256.convert(utf8.encode(buildMetadataSource)).toString(),
      );
      expect(
        () => verifier.verify(
          entry: buildMetadataEntry,
          packageSource: buildMetadataSource,
        ),
        throwsA(
          isA<SourceRegistryPackageVerificationException>().having(
            (error) => error.code,
            'code',
            'package_metadata_mismatch',
          ),
        ),
      );
    },
  );

  test(
    'maps malformed verified package and oversized package to safe codes',
    () {
      final malformed = '{"schemaVersion":1}';
      final malformedEntry = _entry(
        'example.anime',
        'example.anime/package.json',
        source: malformed,
      );
      expect(
        () => verifier.verify(entry: malformedEntry, packageSource: malformed),
        throwsA(
          isA<SourceRegistryPackageVerificationException>().having(
            (error) => error.code,
            'code',
            'package_format_invalid',
          ),
        ),
      );

      final malformedBytes = [0x7b, 0xff, 0x7d];
      final malformedBytesEntry = SourceRegistryEntry(
        packageId: 'example.anime',
        version: Version.parse('1.2.0'),
        packagePath: 'example.anime/package.json',
        sha256: sha256.convert(malformedBytes).toString(),
      );
      expect(
        () => verifier.verifyBytes(
          entry: malformedBytesEntry,
          packageBytes: malformedBytes,
        ),
        throwsA(
          isA<SourceRegistryPackageVerificationException>().having(
            (error) => error.code,
            'code',
            'package_format_invalid',
          ),
        ),
      );

      final oversized = List<String>.filled(
        SourcePackageDecoder.maxPackageBytes + 1,
        'x',
      ).join();
      final oversizedEntry = _entry(
        'example.anime',
        'example.anime/package.json',
        source: oversized,
      );
      expect(
        () => verifier.verify(entry: oversizedEntry, packageSource: oversized),
        throwsA(
          isA<SourceRegistryPackageVerificationException>().having(
            (error) => error.code,
            'code',
            'package_too_large',
          ),
        ),
      );
    },
  );
}

SourceRegistryEntry _entry(
  String packageId,
  String packagePath, {
  String? source,
}) {
  final packageSource =
      source ?? jsonEncode(_validPackage(packageId: packageId));
  return SourceRegistryEntry(
    packageId: packageId,
    version: Version.parse('1.2.0'),
    packagePath: packagePath,
    sha256: sha256.convert(utf8.encode(packageSource)).toString(),
  );
}

Map<String, Object?> _indexMap(List<Object?> packages) {
  return {
    'schemaVersion': 1,
    'sourceRoot': 'sources',
    'revision': 'main',
    'packages': packages,
  };
}

Map<String, Object?> _entryMap(String packageId) {
  return {
    'packageId': packageId,
    'version': '1.2.0',
    'path': '$packageId/package.json',
    'sha256': List.filled(64, 'a').join(),
  };
}

Map<String, Object?> _validPackage({String packageId = 'example.anime'}) {
  return {
    'schemaVersion': 1,
    'packageId': packageId,
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
            'attribute': null,
            'required': true,
            'regex': null,
          },
        ],
      },
    ],
    'signature': null,
  };
}

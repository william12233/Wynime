import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_registry_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_artifact_catalog.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_index_encoder.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  const loader = SourceRegistryArtifactCatalogLoader();
  const indexEncoder = SourceRegistryIndexEncoder();

  test('loads one immutable, verified catalog in index order', () {
    final alpha = _artifact('alpha.anime');
    final zeta = _artifact('zeta.anime');
    final index = SourceRegistryIndex(
      schemaVersion: 1,
      sourceRoot: 'sources',
      revision: 'main-1',
      packages: [zeta.entry, alpha.entry],
    );

    final catalog = loader.load(
      indexBytes: utf8.encode(indexEncoder.encode(index)),
      packageBytesByPath: {
        index.relativePathFor(zeta.entry): zeta.bytes,
        index.relativePathFor(alpha.entry): alpha.bytes,
      },
    );

    expect(catalog.index.revision, 'main-1');
    expect(catalog.packages.map((item) => item.entry.packageId), [
      'alpha.anime',
      'zeta.anime',
    ]);
    expect(
      catalog.packageById('alpha.anime')?.package.packageId,
      'alpha.anime',
    );
    expect(
      () => catalog.packages.add(catalog.packages.first),
      throwsUnsupportedError,
    );
  });

  test('rejects an invalid index with a stable catalog code', () {
    expect(
      () => loader.load(
        indexBytes: [0x7b, 0xff, 0x7d],
        packageBytesByPath: const {},
      ),
      throwsA(
        isA<SourceRegistryCatalogException>().having(
          (error) => error.code,
          'code',
          'index_format_invalid',
        ),
      ),
    );
  });

  test('requires exactly the indexed artifact set', () {
    final artifact = _artifact('example.anime');
    final index = _index([artifact.entry]);
    final path = index.relativePathFor(artifact.entry);

    expect(
      () => loader.load(
        indexBytes: _indexBytes(index),
        packageBytesByPath: const {},
      ),
      throwsA(
        isA<SourceRegistryCatalogException>().having(
          (error) => error.code,
          'code',
          'artifact_missing',
        ),
      ),
    );
    expect(
      () => loader.load(
        indexBytes: _indexBytes(index),
        packageBytesByPath: {path: artifact.bytes, 'sources/extra.json': []},
      ),
      throwsA(
        isA<SourceRegistryCatalogException>().having(
          (error) => error.code,
          'code',
          'artifact_unexpected',
        ),
      ),
    );
  });

  test('forwards integrity failure without returning a partial catalog', () {
    final artifact = _artifact('example.anime');
    final index = _index([artifact.entry]);
    final changed = List<int>.from(artifact.bytes);
    changed[0] = changed[0] ^ 1;

    expect(
      () => loader.load(
        indexBytes: _indexBytes(index),
        packageBytesByPath: {index.relativePathFor(artifact.entry): changed},
      ),
      throwsA(
        isA<SourceRegistryCatalogException>().having(
          (error) => error.code,
          'code',
          'package_integrity_mismatch',
        ),
      ),
    );
  });

  test('forwards package identity failure after exact-byte verification', () {
    final package = _artifact('alpha.anime');
    final mismatchedEntry = SourceRegistryEntry(
      packageId: 'beta.anime',
      version: package.entry.version,
      packagePath: 'beta.anime/package.json',
      sha256: sha256.convert(package.bytes).toString(),
    );
    final index = _index([mismatchedEntry]);

    expect(
      () => loader.load(
        indexBytes: _indexBytes(index),
        packageBytesByPath: {
          index.relativePathFor(mismatchedEntry): package.bytes,
        },
      ),
      throwsA(
        isA<SourceRegistryCatalogException>().having(
          (error) => error.code,
          'code',
          'package_metadata_mismatch',
        ),
      ),
    );
  });
}

_RegistryArtifact _artifact(String packageId) {
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: packageId,
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
  );
  final bytes = utf8.encode(const SourcePackageEncoder().encode(package));
  return _RegistryArtifact(
    entry: SourceRegistryEntry(
      packageId: packageId,
      version: package.version,
      packagePath: '$packageId/package.json',
      sha256: sha256.convert(bytes).toString(),
    ),
    bytes: bytes,
  );
}

SourceRegistryIndex _index(List<SourceRegistryEntry> entries) {
  return SourceRegistryIndex(
    schemaVersion: 1,
    sourceRoot: 'sources',
    revision: 'main-1',
    packages: entries,
  );
}

List<int> _indexBytes(SourceRegistryIndex index) {
  return utf8.encode(const SourceRegistryIndexEncoder().encode(index));
}

final class _RegistryArtifact {
  const _RegistryArtifact({required this.entry, required this.bytes});

  final SourceRegistryEntry entry;
  final List<int> bytes;
}

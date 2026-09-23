import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as path;
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/infrastructure/source_registry/github_source_registry_repository.dart';
import 'package:wynime/src/infrastructure/source_registry/staged_source_registry_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_index_encoder.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_registry_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'loads exact staged bytes through the shared catalog verifier',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'wynime-staged-registry-',
      );
      addTearDown(() => root.delete(recursive: true));
      final artifact = _artifact();
      final index = SourceRegistryIndex(
        schemaVersion: 1,
        sourceRoot: 'sources',
        revision: 'staged-test',
        packages: [artifact.entry],
      );
      await _writeSnapshot(root, index, artifact.bytes);

      final repository = StagedSourceRegistryRepository(root: root);
      final first = repository.loadCatalog();
      final second = repository.loadCatalog();

      expect(identical(first, second), isTrue);
      final catalog = await first;
      expect(catalog.index.revision, 'staged-test');
      expect(catalog.packages.single.package.packageId, 'example.anime');
      expect(catalog.packages.single.entry.sha256, artifact.entry.sha256);
      repository.close();
    },
  );

  test(
    'rejects a changed staged artifact without partial catalog state',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'wynime-staged-registry-',
      );
      addTearDown(() => root.delete(recursive: true));
      final artifact = _artifact();
      final index = SourceRegistryIndex(
        schemaVersion: 1,
        sourceRoot: 'sources',
        revision: 'staged-test',
        packages: [artifact.entry],
      );
      final changed = List<int>.from(artifact.bytes);
      changed[0] = changed[0] ^ 1;
      await _writeSnapshot(root, index, changed);

      final repository = StagedSourceRegistryRepository(root: root);
      await expectLater(
        repository.loadCatalog(),
        throwsA(
          isA<SourceRegistryRepositoryException>().having(
            (error) => error.code,
            'code',
            'package_integrity_mismatch',
          ),
        ),
      );
    },
  );

  test('rejects an unavailable staged root with a bounded code', () async {
    final root = Directory(
      path.join(
        Directory.systemTemp.path,
        'wynime-staged-registry-does-not-exist-${DateTime.now().microsecondsSinceEpoch}',
      ),
    );
    final repository = StagedSourceRegistryRepository(root: root);

    await expectLater(
      repository.loadCatalog(),
      throwsA(
        isA<SourceRegistryRepositoryException>().having(
          (error) => error.code,
          'code',
          'staged_root_unavailable',
        ),
      ),
    );
  });
}

Future<void> _writeSnapshot(
  Directory root,
  SourceRegistryIndex index,
  List<int> packageBytes,
) async {
  final packageDirectory = Directory(
    path.join(root.path, 'sources', 'example.anime'),
  );
  await packageDirectory.create(recursive: true);
  await File(
    path.join(root.path, 'sources', 'index.json'),
  ).writeAsBytes(utf8.encode(const SourceRegistryIndexEncoder().encode(index)));
  await File(
    path.join(root.path, index.relativePathFor(index.packages.single)),
  ).writeAsBytes(packageBytes);
}

_Artifact _artifact() {
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'example.anime',
    displayName: 'Example Anime',
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
        resultLimit: 1,
        fields: [
          SourceFieldRule(
            name: 'title',
            valueKind: SourceValueKind.raw,
            required: true,
          ),
        ],
      ),
    ],
  );
  final bytes = utf8.encode(const SourcePackageEncoder().encode(package));
  return _Artifact(
    entry: SourceRegistryEntry(
      packageId: package.packageId,
      version: package.version,
      packagePath: 'example.anime/package.json',
      sha256: sha256.convert(bytes).toString(),
    ),
    bytes: bytes,
  );
}

final class _Artifact {
  const _Artifact({required this.entry, required this.bytes});

  final SourceRegistryEntry entry;
  final List<int> bytes;
}

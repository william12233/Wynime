import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_registry_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_artifact_catalog.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_index_encoder.dart';

import 'source_rule_test_support.dart';

SourceRegistryCatalog testSourceRegistryCatalog({
  Iterable<String> packageIds = const ['example.registry'],
  String revision = 'test-revision',
}) {
  final artifacts = [
    for (final packageId in packageIds) _testRegistryArtifact(packageId),
  ];
  final index = SourceRegistryIndex(
    schemaVersion: 1,
    sourceRoot: 'sources',
    revision: revision,
    packages: [for (final artifact in artifacts) artifact.entry],
  );
  return const SourceRegistryArtifactCatalogLoader().load(
    indexBytes: utf8.encode(const SourceRegistryIndexEncoder().encode(index)),
    packageBytesByPath: {
      for (final artifact in artifacts)
        index.relativePathFor(artifact.entry): artifact.bytes,
    },
  );
}

_TestRegistryArtifact _testRegistryArtifact(String packageId) {
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
  return _TestRegistryArtifact(
    entry: SourceRegistryEntry(
      packageId: packageId,
      version: package.version,
      packagePath: '$packageId/package.json',
      sha256: sha256.convert(bytes).toString(),
    ),
    bytes: bytes,
  );
}

final class _TestRegistryArtifact {
  const _TestRegistryArtifact({required this.entry, required this.bytes});

  final SourceRegistryEntry entry;
  final List<int> bytes;
}

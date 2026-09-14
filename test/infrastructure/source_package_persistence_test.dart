import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/repositories/source_package_repository.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
import 'package:wynime/src/infrastructure/repositories/drift_source_package_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/persistent_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';

import '../helpers/source_rule_test_support.dart';
import '../helpers/test_database.dart';

void main() {
  test('schema-v1 encoder round-trips every persisted manifest field', () {
    final package = _package(
      signature: SourcePackageSignatureMetadata(
        declaredSignerId: 'publisher.example',
        keyId: 'key-1',
        algorithm: 'ed25519',
        signatureBase64: base64Encode(List<int>.filled(64, 9)),
      ),
    );

    final encoded = const SourcePackageEncoder().encode(package);
    final decoded = const SourcePackageDecoder().decode(encoded);

    expect(jsonDecode(encoded), isA<Map<String, dynamic>>());
    expect(decoded.schemaVersion, package.schemaVersion);
    expect(decoded.packageId, package.packageId);
    expect(decoded.displayName, package.displayName);
    expect(decoded.version, package.version);
    expect(
      decoded.wynimeVersionConstraint.toString(),
      package.wynimeVersionConstraint.toString(),
    );
    expect(decoded.securityPolicy.allowedDomains.single.host, 'example.com');
    expect(decoded.securityPolicy.allowedDomains.single.schemes, {'https'});
    expect(decoded.securityPolicy.permissions, {SourcePermission.network});
    expect(decoded.securityPolicy.budget.maxRedirects, 3);
    expect(decoded.programs.single.rootSelector.expression, '.item');
    expect(decoded.programs.single.fields[1].attributeName, 'href');
    expect(decoded.programs.single.fields[2].regexCapture!.pattern, r'^(\d+)$');
    expect(decoded.signatureMetadata!.declaredSignerId, 'publisher.example');
    expect(
      decoded.signatureMetadata!.signatureBase64,
      package.signatureMetadata!.signatureBase64,
    );
  });

  test(
    'persistent manager restores enabled and consent state after restart',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftSourcePackageRepository(
        database,
        clock: () => DateTime.utc(2026, 9, 13, 12),
      );
      final first = PersistentSourcePackageManager(
        manager: DeclarativeSourcePackageManager(
          wynimeVersion: Version.parse('1.5.0'),
        ),
        repository: repository,
      );

      expect(await first.load(), isEmpty);
      final package = _package();
      final installed = await first.install(package);
      expect(installed.requiresConsent, isTrue);
      final enabled = await first.enable(
        packageId: package.packageId,
        version: package.version,
        userApproved: true,
        reconsentGranted: false,
      );
      expect(enabled.status, SourcePackageStatus.enabled);

      final restarted = PersistentSourcePackageManager(
        manager: DeclarativeSourcePackageManager(
          wynimeVersion: Version.parse('1.5.0'),
        ),
        repository: DriftSourcePackageRepository(database),
      );
      final restored = await restarted.load();

      expect(restarted.isLoaded, isTrue);
      expect(restored, hasLength(1));
      expect(restored.single.status, SourcePackageStatus.enabled);
      expect(restored.single.requiresConsent, isFalse);
      expect(restored.single.requiresReconsent, isFalse);
      expect(restored.single.package.programs.single.fields, hasLength(3));
    },
  );

  test(
    'restart preserves disabled re-consent state after a broader update',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftSourcePackageRepository(database);
      final manager = PersistentSourcePackageManager(
        manager: DeclarativeSourcePackageManager(
          wynimeVersion: Version.parse('1.5.0'),
        ),
        repository: repository,
      );
      await manager.load();
      final original = _package();
      await manager.install(original);
      await manager.enable(
        packageId: original.packageId,
        version: original.version,
        userApproved: true,
        reconsentGranted: false,
      );

      final broader = _package(
        version: '1.1.0',
        policy: testSourcePolicy(
          domains: [
            SourceDomainRule(host: 'example.com', includeSubdomains: true),
            SourceDomainRule(host: 'cdn.example.net'),
          ],
        ),
      );
      final updated = await manager.update(broader);
      expect(updated.status, SourcePackageStatus.disabled);
      expect(updated.requiresConsent, isTrue);
      expect(updated.requiresReconsent, isTrue);

      final restarted = PersistentSourcePackageManager(
        manager: DeclarativeSourcePackageManager(
          wynimeVersion: Version.parse('1.5.0'),
        ),
        repository: DriftSourcePackageRepository(database),
      );
      final restored = await restarted.load();
      expect(restored.single.status, SourcePackageStatus.disabled);
      expect(restored.single.requiresConsent, isTrue);
      expect(restored.single.requiresReconsent, isTrue);
    },
  );

  test(
    'malformed persisted package fails closed without echoing its payload',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      const secret = 'cookie=do-not-echo';
      await database
          .into(database.sourcePackages)
          .insert(
            SourcePackagesCompanion.insert(
              packageId: 'example.anime',
              packageJson: secret,
              status: SourcePackageStatus.enabled.name,
              requiresConsent: false,
              requiresReconsent: false,
              updatedAt: DateTime.utc(2026, 9, 13),
            ),
          );

      try {
        await DriftSourcePackageRepository(database).load();
        fail('expected persisted state failure');
      } on SourcePackageRepositoryException catch (error) {
        expect(error.code, 'persisted_state_invalid');
        expect(error.message, isNot(contains(secret)));
        expect(error.toString(), isNot(contains(secret)));
      }
    },
  );

  test(
    'mutation requires startup load and a failed commit rolls back memory',
    () async {
      final repository = _MemorySourcePackageRepository();
      final manager = PersistentSourcePackageManager(
        manager: DeclarativeSourcePackageManager(
          wynimeVersion: Version.parse('1.5.0'),
        ),
        repository: repository,
      );
      final package = _package();

      await expectLater(
        manager.install(package),
        throwsA(
          isA<SourcePackageRepositoryException>().having(
            (error) => error.code,
            'code',
            'not_initialized',
          ),
        ),
      );
      await manager.load();
      await manager.install(package);
      repository.failWrites = true;

      await expectLater(
        manager.enable(
          packageId: package.packageId,
          version: package.version,
          userApproved: true,
          reconsentGranted: false,
        ),
        throwsA(isA<SourcePackageRepositoryException>()),
      );
      expect(
        manager.installedPackages.single.status,
        SourcePackageStatus.disabled,
      );
      expect(repository.values.single.status, SourcePackageStatus.disabled);
    },
  );

  test(
    'concurrent mutations are serialized into one complete snapshot',
    () async {
      final repository = _MemorySourcePackageRepository();
      final manager = PersistentSourcePackageManager(
        manager: DeclarativeSourcePackageManager(
          wynimeVersion: Version.parse('1.5.0'),
        ),
        repository: repository,
      );
      await manager.load();

      await Future.wait([
        manager.install(_package(packageId: 'alpha.anime')),
        manager.install(_package(packageId: 'beta.anime')),
      ]);

      expect(repository.values.map((entry) => entry.package.packageId), [
        'alpha.anime',
        'beta.anime',
      ]);
    },
  );

  test('invalid reload does not partially replace a loaded manager', () async {
    final repository = _MemorySourcePackageRepository();
    final manager = PersistentSourcePackageManager(
      manager: DeclarativeSourcePackageManager(
        wynimeVersion: Version.parse('1.5.0'),
      ),
      repository: repository,
    );
    await manager.load();
    final package = _package();
    await manager.install(package);
    repository.values = [
      InstalledSourcePackage(
        package: package,
        status: SourcePackageStatus.enabled,
        requiresConsent: true,
        requiresReconsent: false,
      ),
    ];

    await expectLater(
      manager.load(),
      throwsA(
        isA<SourcePackageManagerException>().having(
          (error) => error.code,
          'code',
          'persisted_state_invalid',
        ),
      ),
    );
    expect(
      manager.installedPackages.single.status,
      SourcePackageStatus.disabled,
    );
  });
}

SourcePackageManifest _package({
  String packageId = 'example.anime',
  String version = '1.0.0',
  SourceSecurityPolicy? policy,
  SourcePackageSignatureMetadata? signature,
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: 'Example Anime',
    version: Version.parse(version),
    wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
    securityPolicy: policy ?? testSourcePolicy(),
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
            selector: SourceSelector(
              kind: SourceSelectorKind.css,
              expression: '.title',
            ),
            valueKind: SourceValueKind.text,
            required: true,
          ),
          SourceFieldRule(
            name: 'link',
            selector: SourceSelector(
              kind: SourceSelectorKind.css,
              expression: 'a',
            ),
            valueKind: SourceValueKind.attribute,
            attributeName: 'href',
          ),
          SourceFieldRule(
            name: 'episode',
            valueKind: SourceValueKind.raw,
            regexCapture: SourceRegexCapture(pattern: r'^(\d+)$'),
          ),
        ],
      ),
    ],
    signatureMetadata: signature,
  );
}

final class _MemorySourcePackageRepository implements SourcePackageRepository {
  List<InstalledSourcePackage> values = [];
  bool failWrites = false;

  @override
  Future<List<InstalledSourcePackage>> load() async =>
      List<InstalledSourcePackage>.unmodifiable(values);

  @override
  Future<void> replaceAll(Iterable<InstalledSourcePackage> packages) async {
    if (failWrites) {
      throw const SourcePackageRepositoryException(
        'storage_write_failed',
        'The source package state could not be committed.',
      );
    }
    values = List<InstalledSourcePackage>.unmodifiable(packages);
  }
}

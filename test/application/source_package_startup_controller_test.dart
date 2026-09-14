import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_package_startup_controller.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/repositories/source_package_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/persistent_source_package_manager.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'restores the durable snapshot once and exposes lifecycle state',
    () async {
      final repository = _MemorySourcePackageRepository([
        _installedPackage(status: SourcePackageStatus.enabled),
      ]);
      var factoryCalls = 0;
      final controller = SourcePackageStartupController(
        managerFactory: () async {
          factoryCalls += 1;
          return _manager(repository);
        },
      );
      addTearDown(controller.dispose);

      final first = controller.initialize();
      final second = controller.initialize();
      expect(identical(first, second), isTrue);
      await first;

      expect(factoryCalls, 1);
      expect(controller.status, SourcePackageStartupStatus.ready);
      expect(controller.errorCode, isNull);
      expect(controller.installedPackages, hasLength(1));
      expect(controller.enabledPackages, hasLength(1));
      expect(
        controller.installedPackages.single.package.packageId,
        'example.anime',
      );
    },
  );

  test('maps a repository failure to a stable safe code', () async {
    final repository = _MemorySourcePackageRepository(
      const [],
      loadError: SourcePackageRepositoryException(
        'storage read failed; token=secret',
        'private storage details must not reach the UI',
      ),
    );
    final controller = SourcePackageStartupController(
      managerFactory: () async => _manager(repository),
    );
    addTearDown(controller.dispose);

    await controller.initialize();

    expect(controller.status, SourcePackageStartupStatus.failed);
    expect(controller.errorCode, 'load_failed');
    expect(controller.errorCode, isNot(contains('secret')));
    expect(controller.installedPackages, isEmpty);
  });

  test('does not publish a completion update after disposal', () async {
    final managerCompleter = Completer<PersistentSourcePackageManager>();
    final controller = SourcePackageStartupController(
      managerFactory: () => managerCompleter.future,
    );
    var notifications = 0;
    controller.addListener(() => notifications += 1);

    final initialization = controller.initialize();
    expect(controller.status, SourcePackageStartupStatus.loading);
    controller.dispose();
    managerCompleter.complete(
      _manager(_MemorySourcePackageRepository(const [])),
    );

    await initialization;

    expect(notifications, 1);
    expect(controller.status, SourcePackageStartupStatus.loading);
  });

  test(
    'explicit install/update stages consent, and enable/disable persist state',
    () async {
      final repository = _MemorySourcePackageRepository(const []);
      final controller = SourcePackageStartupController(
        managerFactory: () async => _manager(repository),
      );
      addTearDown(controller.dispose);
      await controller.initialize();

      final staged = await controller.installOrUpdate(_package());
      expect(staged.status, SourcePackageStatus.disabled);
      expect(staged.requiresConsent, isTrue);
      expect(controller.installedPackages.single.requiresConsent, isTrue);

      final enabled = await controller.enable(
        packageId: 'example.anime',
        version: Version.parse('1.0.0'),
        userApproved: true,
        reconsentGranted: true,
      );
      expect(enabled.status, SourcePackageStatus.enabled);
      expect(enabled.requiresConsent, isFalse);
      expect(
        controller.enabledPackages.single.package.packageId,
        'example.anime',
      );

      final disabled = await controller.disable(
        packageId: 'example.anime',
        version: Version.parse('1.0.0'),
      );
      expect(disabled.status, SourcePackageStatus.disabled);
      expect(repository.values.single.status, SourcePackageStatus.disabled);
    },
  );

  test(
    'stale lifecycle requests fail safely and do not poison the queue',
    () async {
      final repository = _MemorySourcePackageRepository(const []);
      final controller = SourcePackageStartupController(
        managerFactory: () async => _manager(repository),
      );
      addTearDown(controller.dispose);
      await controller.initialize();
      await controller.installOrUpdate(_package());

      await expectLater(
        controller.installOrUpdate(_package()),
        throwsA(
          isA<SourcePackageLifecycleException>().having(
            (error) => error.code,
            'code',
            'version_not_newer',
          ),
        ),
      );
      final enabled = await controller.enable(
        packageId: 'example.anime',
        version: Version.parse('1.0.0'),
        userApproved: true,
        reconsentGranted: true,
      );
      expect(enabled.status, SourcePackageStatus.enabled);
    },
  );

  test('close waits for a durable mutation and rejects later work', () async {
    final replaceGate = Completer<void>();
    final repository = _MemorySourcePackageRepository(
      const [],
      replaceGate: replaceGate,
    );
    final controller = SourcePackageStartupController(
      managerFactory: () async => _manager(repository),
    );
    await controller.initialize();

    final install = controller.installOrUpdate(_package());
    await Future<void>.delayed(Duration.zero);
    expect(controller.isMutating, isTrue);
    final close = controller.close();
    var closeCompleted = false;
    unawaited(close.then((_) => closeCompleted = true));
    await Future<void>.delayed(Duration.zero);
    expect(closeCompleted, isFalse);
    replaceGate.complete();
    await install;
    await close;

    await expectLater(
      controller.disable(
        packageId: 'example.anime',
        version: Version.parse('1.0.0'),
      ),
      throwsA(
        isA<SourcePackageLifecycleException>().having(
          (error) => error.code,
          'code',
          'controller_closed',
        ),
      ),
    );
    controller.dispose();
  });

  test(
    'close during manager creation does not wait on an unstarted load',
    () async {
      final managerCompleter = Completer<PersistentSourcePackageManager>();
      final controller = SourcePackageStartupController(
        managerFactory: () => managerCompleter.future,
      );
      final initialization = controller.initialize();

      await controller.close();
      managerCompleter.complete(
        _manager(_MemorySourcePackageRepository(const [])),
      );
      await initialization;
      controller.dispose();
    },
  );
}

PersistentSourcePackageManager _manager(SourcePackageRepository repository) {
  return PersistentSourcePackageManager(
    manager: DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.0'),
    ),
    repository: repository,
  );
}

InstalledSourcePackage _installedPackage({
  required SourcePackageStatus status,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) {
  return InstalledSourcePackage(
    package: _package(),
    status: status,
    requiresConsent: requiresConsent,
    requiresReconsent: requiresReconsent,
  );
}

SourcePackageManifest _package() {
  return SourcePackageManifest(
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
        fields: [
          SourceFieldRule(
            name: 'title',
            valueKind: SourceValueKind.raw,
            required: true,
          ),
        ],
        resultLimit: 1,
      ),
    ],
  );
}

final class _MemorySourcePackageRepository implements SourcePackageRepository {
  _MemorySourcePackageRepository(
    Iterable<InstalledSourcePackage> values, {
    this.loadError,
    this.replaceGate,
  }) : values = List<InstalledSourcePackage>.unmodifiable(values);

  List<InstalledSourcePackage> values;
  final Object? loadError;
  final Completer<void>? replaceGate;

  @override
  Future<List<InstalledSourcePackage>> load() async {
    final error = loadError;
    if (error != null) throw error;
    return values;
  }

  @override
  Future<void> replaceAll(Iterable<InstalledSourcePackage> packages) async {
    values = List<InstalledSourcePackage>.unmodifiable(packages);
    await replaceGate?.future;
  }
}

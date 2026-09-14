import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/app/wynime_app.dart';
import 'package:wynime/src/application/source_package_startup_controller.dart';
import 'package:wynime/src/application/source_registry_controller.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/repositories/source_package_repository.dart';
import 'package:wynime/src/infrastructure/source_registry/github_source_registry_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/persistent_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_artifact_catalog.dart';

import '../helpers/source_registry_test_support.dart';
import '../helpers/source_rule_test_support.dart';

void main() {
  testWidgets('renders restored packages and truthful lifecycle statuses', (
    tester,
  ) async {
    final controller = _controller([
      _installed(
        'example.enabled',
        'Enabled Example',
        SourcePackageStatus.enabled,
      ),
      _installed(
        'example.review',
        'Review Example',
        SourcePackageStatus.disabled,
        requiresConsent: true,
      ),
      _installed(
        'example.reconsent',
        'Re-consent Example',
        SourcePackageStatus.disabled,
        requiresConsent: true,
        requiresReconsent: true,
      ),
      _installed(
        'example.disabled',
        'Disabled Example',
        SourcePackageStatus.disabled,
      ),
    ]);

    await _pumpApp(tester, controller);
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pumpAndSettle();

    expect(find.text('Enabled Example'), findsOneWidget);
    expect(find.text('Review Example'), findsOneWidget);
    expect(find.text('Re-consent Example'), findsOneWidget);
    expect(find.text('Disabled Example'), findsOneWidget);
    expect(find.text('Enabled'), findsOneWidget);
    expect(find.text('Review required'), findsOneWidget);
    expect(find.text('Re-consent required'), findsOneWidget);
    expect(find.text('Disabled'), findsOneWidget);
    expect(find.byType(ElevatedButton), findsNothing);
    expect(find.byType(OutlinedButton), findsNWidgets(4));
    expect(find.text('Enable'), findsNWidgets(3));
    expect(find.text('Disable'), findsOneWidget);
  });

  testWidgets('shows a truthful loading state while startup is pending', (
    tester,
  ) async {
    final controller = _pendingController();

    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      WynimeApp(locale: const Locale('en'), sourcePackages: controller),
    );
    await tester.pump();
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pump();

    expect(find.text('Loading source packages'), findsOneWidget);
    expect(
      find.text(
        'Wynime is restoring the saved source-package state. No source request is sent during startup.',
      ),
      findsOneWidget,
    );
    expect(find.byType(ElevatedButton), findsNothing);
    expect(find.byType(OutlinedButton), findsNothing);
  });

  testWidgets('shows a truthful error state when startup restoration fails', (
    tester,
  ) async {
    final controller = SourcePackageStartupController(
      managerFactory: () async => _manager(
        _MemorySourcePackageRepository(
          const [],
          loadError: const SourcePackageRepositoryException(
            'storage_read_failed',
            'private storage detail',
          ),
        ),
      ),
    );

    await _pumpApp(tester, controller);
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pumpAndSettle();

    expect(find.text('Source packages are unavailable'), findsOneWidget);
    expect(
      find.text(
        'The saved source-package state could not be restored, so no source is enabled.',
      ),
      findsOneWidget,
    );
    expect(find.text('storage_read_failed'), findsNothing);
    expect(find.byType(ElevatedButton), findsNothing);
    expect(find.byType(OutlinedButton), findsNothing);
  });

  testWidgets('renders registry candidates and refreshes read-only state', (
    tester,
  ) async {
    var calls = 0;
    final registry = SourceRegistryController(
      loadCatalog: () async {
        calls += 1;
        return testSourceRegistryCatalog(
          revision: calls == 1 ? 'registry-one' : 'registry-two',
          packageIds: const [
            'example.registry',
            'new.registry',
            'uninstalled.registry',
          ],
        );
      },
    );
    addTearDown(registry.dispose);

    await _pumpApp(
      tester,
      _controller([
        _installed(
          'example.registry',
          'Installed Example',
          SourcePackageStatus.disabled,
        ),
        _installed(
          'new.registry',
          'Older Example',
          SourcePackageStatus.disabled,
          version: Version.parse('0.9.0'),
        ),
      ]),
      sourceRegistry: registry,
    );
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pumpAndSettle();

    expect(find.text('Available source packages'), findsOneWidget);
    expect(find.text('Registry revision: registry-one'), findsOneWidget);
    expect(find.text('example.registry'), findsAtLeastNWidgets(1));
    expect(find.text('new.registry'), findsAtLeastNWidgets(1));
    expect(find.text('uninstalled.registry'), findsAtLeastNWidgets(1));
    expect(find.text('Installed'), findsOneWidget);
    expect(find.text('Update available'), findsOneWidget);
    expect(find.text('Not installed'), findsOneWidget);
    expect(
      find.textContaining('artifact integrity verified'),
      findsNWidgets(3),
    );
    expect(find.text('Install'), findsOneWidget);
    expect(find.text('Update'), findsOneWidget);
    expect(find.text('Enable'), findsNWidgets(2));

    await tester.scrollUntilVisible(
      find.text('Refresh registry'),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Refresh registry'));
    await tester.pumpAndSettle();

    expect(calls, 2);
    expect(find.text('Registry revision: registry-two'), findsOneWidget);
    expect(find.text('Registry revision: registry-one'), findsNothing);
  });

  testWidgets('stages a registry package without enabling it', (tester) async {
    final registry = SourceRegistryController(
      loadCatalog: () async =>
          testSourceRegistryCatalog(packageIds: const ['uninstalled.registry']),
    );
    addTearDown(registry.dispose);
    final controller = _controller(const []);

    await _pumpApp(tester, controller, sourceRegistry: registry);
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Install'),
      400,
      scrollable: find.byType(Scrollable).first,
    );

    await tester.tap(find.text('Install'));
    await tester.pumpAndSettle();

    expect(find.text('Installed'), findsOneWidget);
    expect(find.text('Review required'), findsOneWidget);
    expect(find.text('Install'), findsNothing);
    expect(
      find.text('uninstalled.registry was installed and awaits review.'),
      findsOneWidget,
    );
  });

  testWidgets('requires security review before enabling a package', (
    tester,
  ) async {
    final controller = _controller([
      _installed(
        'example.review',
        'Review Example',
        SourcePackageStatus.disabled,
        requiresConsent: true,
      ),
    ]);

    await _pumpApp(tester, controller);
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Enable'),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Enable'));
    await tester.pumpAndSettle();

    expect(find.text('Review source package'), findsOneWidget);
    expect(find.text('Allowed domains'), findsOneWidget);
    expect(find.text('Explicit permissions'), findsOneWidget);
    expect(find.textContaining('network'), findsOneWidget);
    expect(find.textContaining('selector matches ≤ 100'), findsOneWidget);
    expect(find.textContaining('evaluation steps ≤ 1000'), findsOneWidget);
    expect(find.textContaining('regex pattern ≤ 128 chars'), findsOneWidget);
    expect(find.textContaining('regex input ≤ 1024 chars'), findsOneWidget);
    expect(find.text('Enable after review'), findsOneWidget);

    await tester.tap(find.text('Enable after review'));
    await tester.pumpAndSettle();

    expect(find.text('Review source package'), findsNothing);
    expect(find.text('Enabled'), findsOneWidget);
    expect(find.text('Review Example is now enabled.'), findsOneWidget);
  });

  testWidgets(
    'does not infer local absence while installed state is unavailable',
    (tester) async {
      final registry = SourceRegistryController(
        loadCatalog: () async => testSourceRegistryCatalog(),
      );
      addTearDown(registry.dispose);

      await _pumpApp(tester, null, sourceRegistry: registry);
      await tester.tap(find.byIcon(Icons.hub_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Installed state unavailable'), findsOneWidget);
      expect(find.text('Not installed'), findsNothing);
    },
  );

  testWidgets('keeps registry loading state and disables refresh', (
    tester,
  ) async {
    final pending = Completer<SourceRegistryCatalog>();
    var ready = false;
    final registry = SourceRegistryController(
      loadCatalog: () => pending.future,
    );
    addTearDown(registry.dispose);

    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      WynimeApp(
        locale: const Locale('en'),
        sourcePackages: _controller(const []),
        sourceRegistry: registry,
        onReady: () async => ready = true,
      ),
    );
    await tester.pump();
    await tester.pump();
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pump();

    expect(ready, isTrue);
    expect(find.text('Loading registry candidates'), findsOneWidget);
    expect(
      find.text(
        'Wynime is reading the configured source registry. No package will be installed automatically.',
      ),
      findsOneWidget,
    );
    expect(find.text('Refresh registry'), findsOneWidget);
    expect(
      tester.widget<OutlinedButton>(find.byType(OutlinedButton)).onPressed,
      isNull,
    );

    pending.complete(testSourceRegistryCatalog());
    await tester.pumpAndSettle();
  });

  testWidgets('shows a bounded registry error without exposing raw details', (
    tester,
  ) async {
    final registry = SourceRegistryController(
      loadCatalog: () async {
        throw const SourceRegistryRepositoryException(
          'network timeout; token=private',
          'raw response must not reach presentation',
        );
      },
    );
    addTearDown(registry.dispose);

    await _pumpApp(tester, _controller(const []), sourceRegistry: registry);
    await tester.tap(find.byIcon(Icons.hub_outlined));
    await tester.pumpAndSettle();

    expect(find.text('Registry candidates are unavailable'), findsOneWidget);
    expect(
      find.text(
        'The configured source registry could not be read. Existing installed packages are unchanged.',
      ),
      findsOneWidget,
    );
    expect(find.text('network timeout; token=private'), findsNothing);
    expect(find.text('raw response must not reach presentation'), findsNothing);
    expect(find.text('Refresh registry'), findsOneWidget);
  });
}

Future<void> _pumpApp(
  WidgetTester tester,
  SourcePackageStartupController? controller, {
  SourceRegistryController? sourceRegistry,
}) async {
  await tester.binding.setSurfaceSize(const Size(360, 800));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    WynimeApp(
      locale: const Locale('en'),
      sourcePackages: controller,
      sourceRegistry: sourceRegistry,
    ),
  );
  await tester.pumpAndSettle();
}

SourcePackageStartupController _controller(
  Iterable<InstalledSourcePackage> values,
) {
  return SourcePackageStartupController(
    managerFactory: () async =>
        _manager(_MemorySourcePackageRepository(values)),
  );
}

SourcePackageStartupController _pendingController() {
  final pending = Completer<PersistentSourcePackageManager>();
  return SourcePackageStartupController(managerFactory: () => pending.future);
}

PersistentSourcePackageManager _manager(SourcePackageRepository repository) {
  return PersistentSourcePackageManager(
    manager: DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.0'),
    ),
    repository: repository,
  );
}

InstalledSourcePackage _installed(
  String packageId,
  String displayName,
  SourcePackageStatus status, {
  bool requiresConsent = false,
  bool requiresReconsent = false,
  Version? version,
}) {
  return InstalledSourcePackage(
    package: _package(packageId, displayName, version: version),
    status: status,
    requiresConsent: requiresConsent,
    requiresReconsent: requiresReconsent,
  );
}

SourcePackageManifest _package(
  String packageId,
  String displayName, {
  Version? version,
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: displayName,
    version: version ?? Version.parse('1.0.0'),
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
  }) : values = List<InstalledSourcePackage>.unmodifiable(values);

  List<InstalledSourcePackage> values;
  final Object? loadError;

  @override
  Future<List<InstalledSourcePackage>> load() async {
    final error = loadError;
    if (error != null) throw error;
    return values;
  }

  @override
  Future<void> replaceAll(Iterable<InstalledSourcePackage> packages) async {
    values = List<InstalledSourcePackage>.unmodifiable(packages);
  }
}

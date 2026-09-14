import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/source_registry_controller.dart';
import 'package:wynime/src/infrastructure/source_registry/github_source_registry_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_artifact_catalog.dart';

import '../helpers/source_registry_test_support.dart';

void main() {
  test(
    'initialization is single-flight and exposes one immutable catalog',
    () async {
      final pending = Completer<SourceRegistryCatalog>();
      var calls = 0;
      final controller = SourceRegistryController(
        loadCatalog: () {
          calls += 1;
          return pending.future;
        },
      );
      addTearDown(controller.dispose);

      final first = controller.initialize();
      final second = controller.initialize();
      expect(identical(first, second), isTrue);
      expect(controller.status, SourceRegistryStatus.loading);
      expect(calls, 1);

      pending.complete(testSourceRegistryCatalog(revision: 'revision-one'));
      await first;

      expect(controller.status, SourceRegistryStatus.ready);
      expect(controller.errorCode, isNull);
      expect(controller.catalog?.index.revision, 'revision-one');
      expect(controller.catalog?.packages, hasLength(1));
    },
  );

  test(
    'explicit refresh is single-flight and replaces the old snapshot atomically',
    () async {
      final refreshPending = Completer<SourceRegistryCatalog>();
      var calls = 0;
      final controller = SourceRegistryController(
        loadCatalog: () {
          calls += 1;
          return calls == 1
              ? Future.value(testSourceRegistryCatalog(revision: 'first'))
              : refreshPending.future;
        },
      );
      addTearDown(controller.dispose);

      await controller.initialize();
      final firstRefresh = controller.refresh();
      final secondRefresh = controller.refresh();
      expect(identical(firstRefresh, secondRefresh), isTrue);
      expect(controller.status, SourceRegistryStatus.loading);
      expect(controller.catalog?.index.revision, 'first');

      refreshPending.complete(testSourceRegistryCatalog(revision: 'second'));
      await firstRefresh;

      expect(controller.status, SourceRegistryStatus.ready);
      expect(controller.catalog?.index.revision, 'second');
      expect(calls, 2);
    },
  );

  test('repository failures become bounded controller diagnostics', () async {
    final controller = SourceRegistryController(
      loadCatalog: () async {
        throw const SourceRegistryRepositoryException(
          'network timeout; token=private',
          'raw response and credentials must not reach presentation',
        );
      },
    );
    addTearDown(controller.dispose);

    await controller.initialize();

    expect(controller.status, SourceRegistryStatus.failed);
    expect(controller.errorCode, 'load_failed');
    expect(controller.errorCode, isNot(contains('private')));
    expect(controller.catalog, isNull);
  });

  test(
    'synchronous loader failure clears single-flight state for retry',
    () async {
      var calls = 0;
      final controller = SourceRegistryController(
        loadCatalog: () {
          calls += 1;
          if (calls == 1) {
            throw const SourceRegistryRepositoryException(
              'sync_failure',
              'private loader detail',
            );
          }
          return Future.value(testSourceRegistryCatalog(revision: 'recovered'));
        },
      );
      addTearDown(controller.dispose);

      await controller.initialize();
      expect(controller.status, SourceRegistryStatus.failed);

      await controller.refresh();

      expect(calls, 2);
      expect(controller.status, SourceRegistryStatus.ready);
      expect(controller.catalog?.index.revision, 'recovered');
    },
  );

  test('dispose closes the repository and ignores a late catalog', () async {
    final pending = Completer<SourceRegistryCatalog>();
    var closeCalls = 0;
    final controller = SourceRegistryController(
      loadCatalog: () => pending.future,
      closeRepository: () => closeCalls += 1,
    );
    var notifications = 0;
    controller.addListener(() => notifications += 1);

    final loading = controller.initialize();
    expect(notifications, 1);
    controller.dispose();
    controller.dispose();
    pending.complete(testSourceRegistryCatalog());
    await loading;

    expect(closeCalls, 1);
    expect(notifications, 1);
    expect(controller.catalog, isNull);
    expect(controller.refresh(), completes);
  });
}

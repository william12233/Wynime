import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/application/source_installed_live_search_pipeline.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_search_coordinator.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_search_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';
import 'package:wynime/src/presentation/pages/product_pages.dart';
import 'package:wynime/src/presentation/source_search_presentation_controller.dart';

void main() {
  test('starts in a deterministic idle state', () {
    final controller = _controller();
    addTearDown(controller.dispose);

    expect(controller.state.status, SourceSearchPresentationStatus.idle);
    expect(controller.state.query, isEmpty);
    expect(controller.state.results, isEmpty);
  });

  test(
    'maps one application operation into ordered, source-provenanced UI results',
    () async {
      final first = _installed(
        _package('first.anime', displayName: 'First Source'),
      );
      final second = _installed(
        _package('second.anime', displayName: 'Second Source'),
      );
      var calls = 0;
      Iterable<InstalledSourcePackage>? receivedPackages;
      String? receivedQuery;
      final controller = _controller(
        packages: [first, second],
        operation: ({required installedPackages, required query}) async {
          calls++;
          receivedPackages = installedPackages;
          receivedQuery = query;
          return _availableResult(
            [first, second],
            titles: const ['Same title', 'Same title'],
          );
        },
      );
      addTearDown(controller.dispose);

      await controller.search('  anime  ');

      expect(calls, 1);
      expect(receivedPackages, isNotNull);
      expect(receivedPackages, hasLength(2));
      expect(receivedQuery, 'anime');
      expect(controller.state.status, SourceSearchPresentationStatus.available);
      expect(controller.state.query, 'anime');
      expect(controller.state.results.map((item) => item.result.title), [
        'Same title',
        'Same title',
      ]);
      expect(controller.state.results.map((item) => item.sourceDisplayName), [
        'First Source',
        'Second Source',
      ]);
      expect(controller.state.results.map((item) => item.result.sourceId), [
        'first.anime',
        'second.anime',
      ]);
    },
  );

  test('emits loading before the application operation completes', () async {
    final package = _installed(_package('source.anime'));
    final gate = Completer<SourceInstalledLiveSearchPipelineResult>();
    final controller = _controller(
      packages: [package],
      operation: ({required installedPackages, required query}) => gate.future,
    );
    addTearDown(controller.dispose);

    final search = controller.search('anime');

    expect(controller.state.status, SourceSearchPresentationStatus.loading);
    expect(controller.state.query, 'anime');
    gate.complete(_availableResult([package], titles: const ['Ready']));
    await search;

    expect(controller.state.status, SourceSearchPresentationStatus.available);
    expect(controller.state.results.single.result.title, 'Ready');
  });

  test('preserves partial, not-found and no-usable-source statuses', () async {
    final package = _installed(_package('source.anime'));
    final cases =
        <
          ({
            SourceInstalledLiveSearchPipelineResult result,
            SourceSearchPresentationStatus expected,
          })
        >[
          (
            result: _partialResult([package]),
            expected: SourceSearchPresentationStatus.partial,
          ),
          (
            result: _notFoundResult([package]),
            expected: SourceSearchPresentationStatus.notFound,
          ),
          (
            result: _noUsableResult(),
            expected: SourceSearchPresentationStatus.noUsableSources,
          ),
        ];

    for (final testCase in cases) {
      final controller = _controller(
        packages: [package],
        operation: ({required installedPackages, required query}) async =>
            testCase.result,
      );
      await controller.search('anime');
      expect(controller.state.status, testCase.expected);
      if (testCase.expected == SourceSearchPresentationStatus.partial) {
        expect(controller.state.results, hasLength(1));
      } else {
        expect(controller.state.results, isEmpty);
      }
      controller.dispose();
    }
  });

  test('maps the typed no-sources state without results', () async {
    final package = _installed(_package('source.anime'));
    final controller = _controller(
      packages: [package],
      operation: ({required installedPackages, required query}) async =>
          _noSourcesResult(package),
    );
    addTearDown(controller.dispose);

    await controller.search('anime');

    expect(controller.state.status, SourceSearchPresentationStatus.noSources);
    expect(controller.state.query, 'anime');
    expect(controller.state.results, isEmpty);
  });

  test(
    'rejects empty and invalid input before the application boundary',
    () async {
      var calls = 0;
      final controller = _controller(
        operation: ({required installedPackages, required query}) async {
          calls++;
          return _noUsableResult();
        },
      );
      addTearDown(controller.dispose);

      await controller.search('');
      expect(controller.state.status, SourceSearchPresentationStatus.idle);
      await controller.search('bad\u0001query');

      expect(
        controller.state.status,
        SourceSearchPresentationStatus.invalidQuery,
      );
      expect(calls, 0);
    },
  );

  test('maps unexpected failures to a safe retryable state', () async {
    var calls = 0;
    final package = _installed(_package('source.anime'));
    final controller = _controller(
      packages: [package],
      operation: ({required installedPackages, required query}) async {
        calls++;
        if (calls == 1) {
          throw StateError(
            'https://private.example/body=secret&token=private-token',
          );
        }
        return _availableResult([package], titles: const ['Recovered']);
      },
    );
    addTearDown(controller.dispose);

    await controller.search('anime');
    expect(controller.state.status, SourceSearchPresentationStatus.failed);
    expect(controller.state.toString(), isNot(contains('private-token')));
    await controller.retry();

    expect(calls, 2);
    expect(controller.state.status, SourceSearchPresentationStatus.available);
    expect(controller.state.results.single.result.title, 'Recovered');
  });

  test('an older query cannot replace a newer query', () async {
    final package = _installed(_package('source.anime'));
    final firstGate = Completer<SourceInstalledLiveSearchPipelineResult>();
    final secondGate = Completer<SourceInstalledLiveSearchPipelineResult>();
    final controller = _controller(
      packages: [package],
      operation: ({required installedPackages, required query}) {
        return query == 'first' ? firstGate.future : secondGate.future;
      },
    );
    addTearDown(controller.dispose);

    final firstSearch = controller.search('first');
    await Future<void>.delayed(Duration.zero);
    final secondSearch = controller.search('second');
    secondGate.complete(_availableResult([package], titles: const ['New']));
    await secondSearch;
    firstGate.complete(_availableResult([package], titles: const ['Old']));
    await firstSearch;

    expect(controller.state.query, 'second');
    expect(controller.state.results.single.result.title, 'New');
  });

  test(
    'disposal invalidates an in-flight request without late notification',
    () async {
      final package = _installed(_package('source.anime'));
      final gate = Completer<SourceInstalledLiveSearchPipelineResult>();
      var notifications = 0;
      final controller = _controller(
        packages: [package],
        operation: ({required installedPackages, required query}) =>
            gate.future,
      )..addListener(() => notifications++);

      final search = controller.search('anime');
      await Future<void>.delayed(Duration.zero);
      final beforeDispose = notifications;
      controller.dispose();
      gate.complete(_availableResult([package], titles: const ['Late']));
      await search;

      expect(notifications, beforeDispose);
    },
  );

  testWidgets(
    'real TASK-054 pipeline renders duplicate titles with source provenance',
    (tester) async {
      final first = _installed(
        _package('first.anime', displayName: 'First Source'),
      );
      final second = _installed(
        _package('second.anime', displayName: 'Second Source'),
      );
      final transport = _QueueTransport([
        _successBody('first.anime', 'Same title'),
        _successBody('second.anime', 'Same title'),
      ]);
      final pipeline = _pipeline(transport);
      await _pumpSearch(tester, pipeline: pipeline, packages: [first, second]);

      await tester.enterText(
        find.byKey(const ValueKey('source-search-field')),
        'anime',
      );
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('source-search-state-available')),
        findsOneWidget,
      );
      expect(find.text('Same title'), findsNWidgets(2));
      expect(find.text('Source: First Source (first.anime)'), findsOneWidget);
      expect(find.text('Source: Second Source (second.anime)'), findsOneWidget);
      expect(transport.requests, hasLength(2));
      expect(transport.requests.first.uri.queryParameters['q'], 'anime');
    },
  );

  testWidgets('partial state keeps usable results and exposes a retry action', (
    tester,
  ) async {
    final package = _installed(_package('source.anime'));
    var calls = 0;
    await _pumpSearch(
      tester,
      packages: [package],
      operation: ({required installedPackages, required query}) async {
        calls++;
        return calls == 1
            ? _partialResult([package])
            : _availableResult([package], titles: const ['Retried']);
      },
    );

    await tester.enterText(
      find.byKey(const ValueKey('source-search-field')),
      'anime',
    );
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('source-search-state-partial')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('source-search-retry')), findsOneWidget);
    expect(find.text('Partial result'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('source-search-retry')));
    await tester.pumpAndSettle();
    expect(calls, 2);
    expect(
      find.byKey(const ValueKey('source-search-state-available')),
      findsOneWidget,
    );
    expect(find.text('Retried'), findsOneWidget);
  });
}

SourceSearchPresentationController _controller({
  Iterable<InstalledSourcePackage> packages = const [],
  SourceInstalledLiveSearchOperation? operation,
}) => SourceSearchPresentationController(
  installedPackages: () => packages,
  searchOperation: operation,
);

Future<void> _pumpSearch(
  WidgetTester tester, {
  SourceInstalledLiveSearchPipeline? pipeline,
  Iterable<InstalledSourcePackage> packages = const [],
  SourceInstalledLiveSearchOperation? operation,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      locale: const Locale('en'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      home: Scaffold(
        body: SearchPage(
          showPageHeader: false,
          sourceSearchPipeline: pipeline,
          installedPackagesProvider: () => packages,
          searchOperation: operation,
        ),
      ),
    ),
  );
  await tester.pump();
}

SourceInstalledLiveSearchPipeline _pipeline(SourceHttpTransport transport) {
  final version = Version.parse('1.0.0');
  final runtime = SourceLiveHttpPackageRuntime(
    httpExecutor: SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: version,
      ),
      transport: transport,
    ),
    fixtureRuntime: DeclarativeSourcePackageRuntime(wynimeVersion: version),
  );
  return SourceInstalledLiveSearchPipeline(
    planFactory: SourceLiveOperationPlanFactory(wynimeVersion: version),
    searchCoordinator: SourceLiveSearchCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourceSearchNormalizer(),
    ),
  );
}

SourceInstalledLiveSearchPipelineResult _availableResult(
  List<InstalledSourcePackage> packages, {
  required List<String> titles,
}) {
  final normalized = <SourceSearchNormalizationResult>[];
  final results = <SourceSearchResult>[];
  final packageResults =
      <SourceLiveOperationPlanResult<SourceLiveSearchPlan>>[];
  for (var index = 0; index < packages.length; index++) {
    final package = packages[index];
    final result = SourceSearchResult(
      sourceId: package.package.packageId,
      subjectId: '${package.package.packageId}-subject',
      title: titles[index],
    );
    results.add(result);
    normalized.add(
      SourceSearchNormalizationResult(
        packageId: package.package.packageId,
        packageVersion: package.package.version,
        programId: 'search',
        status: SourceSearchNormalizationStatus.available,
        results: [result],
        diagnostics: const [],
      ),
    );
    packageResults.add(_readyPackageResult(package));
  }
  return SourceInstalledLiveSearchPipelineResult(
    status: SourceInstalledLiveSearchPipelineStatus.available,
    packageResults: packageResults,
    coordinatorResult: SourceSearchCoordinatorResult(
      query: 'anime',
      status: SourceSearchCoordinatorStatus.available,
      sourceResults: normalized,
      results: results,
    ),
  );
}

SourceInstalledLiveSearchPipelineResult _partialResult(
  List<InstalledSourcePackage> packages,
) {
  final result = _availableResult(packages, titles: const ['Partial result']);
  return SourceInstalledLiveSearchPipelineResult(
    status: SourceInstalledLiveSearchPipelineStatus.partial,
    packageResults: result.packageResults,
    coordinatorResult: SourceSearchCoordinatorResult(
      query: 'anime',
      status: SourceSearchCoordinatorStatus.partial,
      sourceResults: result.coordinatorResult!.sourceResults,
      results: result.coordinatorResult!.results,
      reasonCode: 'partial_source_results',
    ),
    reasonCode: 'partial_source_results',
  );
}

SourceInstalledLiveSearchPipelineResult _notFoundResult(
  List<InstalledSourcePackage> packages,
) {
  final packageResults = packages
      .map(_readyPackageResult)
      .toList(growable: false);
  final sourceResults = packages
      .map(
        (package) => SourceSearchNormalizationResult(
          packageId: package.package.packageId,
          packageVersion: package.package.version,
          programId: 'search',
          status: SourceSearchNormalizationStatus.notFound,
          results: const [],
          diagnostics: const [],
        ),
      )
      .toList(growable: false);
  return SourceInstalledLiveSearchPipelineResult(
    status: SourceInstalledLiveSearchPipelineStatus.notFound,
    packageResults: packageResults,
    coordinatorResult: SourceSearchCoordinatorResult(
      query: 'anime',
      status: SourceSearchCoordinatorStatus.notFound,
      sourceResults: sourceResults,
      results: const [],
      reasonCode: 'source_search_not_found',
    ),
  );
}

SourceInstalledLiveSearchPipelineResult _noUsableResult() =>
    SourceInstalledLiveSearchPipelineResult(
      status: SourceInstalledLiveSearchPipelineStatus.noUsableSources,
      packageResults: const [],
      reasonCode: 'no_usable_search_sources',
    );

SourceInstalledLiveSearchPipelineResult _noSourcesResult(
  InstalledSourcePackage package,
) {
  return SourceInstalledLiveSearchPipelineResult(
    status: SourceInstalledLiveSearchPipelineStatus.noSources,
    packageResults: [_readyPackageResult(package)],
    coordinatorResult: SourceSearchCoordinatorResult(
      query: 'anime',
      status: SourceSearchCoordinatorStatus.noSources,
      sourceResults: const [],
      results: const [],
      reasonCode: 'no_enabled_sources',
    ),
  );
}

SourceLiveOperationPlanResult<SourceLiveSearchPlan> _readyPackageResult(
  InstalledSourcePackage installed,
) {
  final package = installed.package;
  return SourceLiveOperationPlanResult(
    packageId: package.packageId,
    packageVersion: package.version,
    operation: SourcePackageLiveOperationKind.search,
    programId: 'search',
    status: SourceLiveOperationPlanFactoryStatus.ready,
    plan: SourceLiveSearchPlan(
      requestPlan: SourceLiveHttpRequestPlan(
        installedPackage: installed,
        programId: 'search',
        request: SourceHttpRequest(
          uri: Uri.parse('https://example.com/search?q=anime'),
          securityPolicy: package.securityPolicy,
        ),
      ),
      mapping: SourceSearchFieldMapping(
        subjectIdField: 'subjectId',
        titleField: 'title',
      ),
    ),
  );
}

SourcePackageManifest _package(String packageId, {String? displayName}) {
  final program = SourceRuleProgram(
    programId: 'search',
    documentKind: SourceDocumentKind.json,
    rootSelector: SourceSelector(
      kind: SourceSelectorKind.jsonPath,
      expression: r'$[*]',
    ),
    fields: [
      SourceFieldRule(
        name: 'subjectId',
        valueKind: SourceValueKind.raw,
        required: true,
        selector: SourceSelector(
          kind: SourceSelectorKind.jsonPath,
          expression: r'$.subjectId',
        ),
      ),
      SourceFieldRule(
        name: 'title',
        valueKind: SourceValueKind.raw,
        required: true,
        selector: SourceSelector(
          kind: SourceSelectorKind.jsonPath,
          expression: r'$.title',
        ),
      ),
    ],
    resultLimit: 20,
  );
  return SourcePackageManifest(
    schemaVersion: 2,
    packageId: packageId,
    displayName: displayName ?? packageId,
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
    securityPolicy: SourceSecurityPolicy(
      allowedDomains: [
        SourceDomainRule(host: 'example.com', includeSubdomains: true),
      ],
      permissions: {SourcePermission.network},
      budget: SourceResourceBudget(
        maxDocumentBytes: 64 * 1024,
        maxRecords: 20,
        maxSelectorMatches: 100,
        maxEvaluationSteps: 1000,
        maxRegexPatternChars: 128,
        maxRegexInputChars: 1024,
        maxRedirects: 3,
      ),
    ),
    programs: [program],
    liveOperations: [
      SourcePackageLiveOperation(
        kind: SourcePackageLiveOperationKind.search,
        programId: 'search',
        uriTemplate: 'https://example.com/search?q={query}',
        mapping: SourceSearchFieldMapping(
          subjectIdField: 'subjectId',
          titleField: 'title',
        ),
      ),
    ],
  );
}

InstalledSourcePackage _installed(SourcePackageManifest package) =>
    InstalledSourcePackage(
      package: package,
      status: SourcePackageStatus.enabled,
      requiresConsent: false,
      requiresReconsent: false,
    );

SourceHttpTransportResult _successBody(String packageId, String title) =>
    SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: Uri.parse('https://example.com/search'),
        redirectChain: const [],
        body: jsonEncode([
          {'subjectId': '$packageId-subject', 'title': title},
        ]),
      ),
    );

final class _QueueTransport implements SourceHttpTransport {
  _QueueTransport(this.results);

  final List<SourceHttpTransportResult> results;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    return results.removeAt(0);
  }

  @override
  Future<void> close() async {}
}

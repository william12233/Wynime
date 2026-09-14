import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_installed_live_search_pipeline.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_search_coordinator.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_search_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'fans out exact factory plans in package order and preserves provenance',
    () async {
      final first = _installed(_package('first.anime'));
      final second = _installed(_package('second.anime'));
      final transport = _QueueTransport([
        _successBody(first.package.packageId, 'First result'),
        _successBody(second.package.packageId, 'Second result'),
      ]);
      final pipeline = _pipeline(transport);

      final result = await pipeline.search(
        installedPackages: [first, second],
        query: 'A&B?/秘密',
      );

      expect(result.status, SourceInstalledLiveSearchPipelineStatus.available);
      expect(
        result.coordinatorResult!.status,
        SourceSearchCoordinatorStatus.available,
      );
      expect(result.packageResults, hasLength(2));
      expect(result.packageResults.map((value) => value.packageId), [
        'first.anime',
        'second.anime',
      ]);
      expect(result.readyPlans, hasLength(2));
      expect(result.readyPlans.first, same(result.packageResults.first.plan));
      expect(result.readyPlans.last, same(result.packageResults.last.plan));
      expect(result.coordinatorResult!.results.map((value) => value.sourceId), [
        'first.anime',
        'second.anime',
      ]);
      expect(result.coordinatorResult!.results.map((value) => value.title), [
        'First result',
        'Second result',
      ]);
      expect(transport.requests, hasLength(2));
      expect(
        transport.requests[0],
        same(result.packageResults[0].plan!.requestPlan.request),
      );
      expect(
        transport.requests[1],
        same(result.packageResults[1].plan!.requestPlan.request),
      );
      expect(transport.requests[0].uri.queryParameters['q'], 'A&B?/秘密');
      expect(
        result.packageResults[0].plan!.requestPlan.installedPackage,
        same(first),
      );
      expect(
        result.packageResults[0].plan!.requestPlan.request.securityPolicy,
        same(first.package.securityPolicy),
      );
    },
  );

  test(
    'snapshots a one-shot package iterable before live search I/O',
    () async {
      final first = _installed(_package('first.anime'));
      final second = _installed(_package('second.anime'));
      final packages = _OneShotPackages([first, second]);
      final transport = _QueueTransport([
        _successBody(first.package.packageId, 'First'),
        _successBody(second.package.packageId, 'Second'),
      ]);

      final future = _pipeline(
        transport,
      ).search(installedPackages: packages, query: 'anime');
      packages.values.clear();
      final result = await future;

      expect(result.status, SourceInstalledLiveSearchPipelineStatus.available);
      expect(packages.iteratorCount, 1);
      expect(transport.requests, hasLength(2));
      expect(result.coordinatorResult!.results, hasLength(2));
    },
  );

  test(
    'keeps typed package preflight failures while valid packages search',
    () async {
      final valid = _installed(_package('valid.anime'));
      final disabled = _installed(
        _package('disabled.anime'),
        status: SourcePackageStatus.disabled,
      );
      final consent = _installed(
        _package('consent.anime'),
        requiresConsent: true,
      );
      final incompatible = _installed(
        _package(
          'incompatible.anime',
          constraint: VersionConstraint.parse('^2.0.0'),
        ),
      );
      final noDeclaration = _installed(
        _package('legacy.anime', schemaVersion: 1, withSearch: false),
      );
      final transport = _QueueTransport([
        _successBody(valid.package.packageId, 'Valid'),
      ]);

      final result = await _pipeline(transport).search(
        installedPackages: [
          disabled,
          valid,
          consent,
          incompatible,
          noDeclaration,
        ],
        query: 'anime',
      );

      expect(result.status, SourceInstalledLiveSearchPipelineStatus.partial);
      expect(result.reasonCode, 'partial_package_preflight');
      expect(
        result.coordinatorResult!.status,
        SourceSearchCoordinatorStatus.available,
      );
      expect(result.coordinatorResult!.results.single.sourceId, 'valid.anime');
      expect(result.packageResults.map((value) => value.status), [
        SourceLiveOperationPlanFactoryStatus.disabled,
        SourceLiveOperationPlanFactoryStatus.ready,
        SourceLiveOperationPlanFactoryStatus.consentRequired,
        SourceLiveOperationPlanFactoryStatus.incompatible,
        SourceLiveOperationPlanFactoryStatus.operationNotFound,
      ]);
      expect(result.readyPlans, hasLength(1));
      expect(
        result.readyPlans.single.requestPlan.installedPackage,
        same(valid),
      );
      expect(transport.requests, hasLength(1));
    },
  );

  test(
    'returns no usable sources without calling the live coordinator',
    () async {
      final packages = [
        _installed(
          _package('disabled.anime'),
          status: SourcePackageStatus.disabled,
        ),
        _installed(_package('consent.anime'), requiresReconsent: true),
        _installed(
          _package(
            'incompatible.anime',
            constraint: VersionConstraint.parse('^2.0.0'),
          ),
        ),
        _installed(
          _package('legacy.anime', schemaVersion: 1, withSearch: false),
        ),
      ];
      final transport = _QueueTransport([]);
      final result = await _pipeline(
        transport,
      ).search(installedPackages: packages, query: 'private-query');

      expect(
        result.status,
        SourceInstalledLiveSearchPipelineStatus.noUsableSources,
      );
      expect(result.reasonCode, 'no_usable_search_sources');
      expect(result.coordinatorResult, isNull);
      expect(result.rejectedPackageCount, 4);
      expect(transport.requests, isEmpty);
      expect(result.toString(), isNot(contains('private-query')));
      expect(result.toString(), isNot(contains('https://example.com')));
    },
  );

  test(
    'rejects over-bound, duplicate and throwing snapshots before transport',
    () async {
      final overBoundTransport = _QueueTransport([]);
      final overBound = await _pipeline(overBoundTransport).search(
        installedPackages: List<InstalledSourcePackage>.generate(
          33,
          (index) => _installed(_package('source$index.anime')),
        ),
        query: 'anime',
      );
      expect(overBound.status, SourceInstalledLiveSearchPipelineStatus.failed);
      expect(overBound.reasonCode, 'too_many_installed_packages');
      expect(overBound.packageResults, isEmpty);
      expect(overBoundTransport.requests, isEmpty);

      final duplicatePackage = _installed(_package('duplicate.anime'));
      final duplicateTransport = _QueueTransport([]);
      final duplicate = await _pipeline(duplicateTransport).search(
        installedPackages: [duplicatePackage, duplicatePackage],
        query: 'anime',
      );
      expect(duplicate.status, SourceInstalledLiveSearchPipelineStatus.failed);
      expect(duplicate.reasonCode, 'duplicate_installed_package');
      expect(duplicateTransport.requests, isEmpty);

      final throwingTransport = _QueueTransport([]);
      final throwing = await _pipeline(
        throwingTransport,
      ).search(installedPackages: _ThrowingPackages(), query: 'anime');
      expect(throwing.status, SourceInstalledLiveSearchPipelineStatus.failed);
      expect(throwing.reasonCode, 'invalid_installed_packages');
      expect(throwing.packageResults, isEmpty);
      expect(throwingTransport.requests, isEmpty);
    },
  );

  test('admits exactly 32 packages at the configured boundary', () async {
    final packages = List<InstalledSourcePackage>.generate(
      32,
      (index) => _installed(_package('source$index.anime')),
    );
    final transport = _QueueTransport(
      List<SourceHttpTransportResult>.generate(
        32,
        (index) =>
            _successBody(packages[index].package.packageId, 'Result $index'),
      ),
    );

    final result = await _pipeline(
      transport,
    ).search(installedPackages: packages, query: 'anime');

    expect(result.status, SourceInstalledLiveSearchPipelineStatus.available);
    expect(result.packageResults, hasLength(32));
    expect(result.readyPackageCount, 32);
    expect(result.rejectedPackageCount, 0);
    expect(transport.requests, hasLength(32));
    expect(result.coordinatorResult!.results, hasLength(32));
    expect(result.packageResults.first.packageId, 'source0.anime');
    expect(result.packageResults.last.packageId, 'source31.anime');
  });

  test('preserves downstream notFound and partial semantics', () async {
    final notFoundPackage = _installed(_package('notfound.anime'));
    final notFoundTransport = _QueueTransport([_successBody('', '[]')]);
    final notFound = await _pipeline(
      notFoundTransport,
    ).search(installedPackages: [notFoundPackage], query: 'anime');
    expect(notFound.status, SourceInstalledLiveSearchPipelineStatus.notFound);
    expect(
      notFound.coordinatorResult!.status,
      SourceSearchCoordinatorStatus.notFound,
    );
    expect(notFound.coordinatorResult!.reasonCode, 'source_search_not_found');

    final first = _installed(_package('first.anime'));
    final second = _installed(_package('second.anime'));
    final partialTransport = _QueueTransport([
      _successBody(first.package.packageId, 'First'),
      _successBody(second.package.packageId, 'TOP_SECRET_RESPONSE'),
    ], secondBody: 'not-json');
    final partial = await _pipeline(
      partialTransport,
    ).search(installedPackages: [first, second], query: 'anime');
    expect(partial.status, SourceInstalledLiveSearchPipelineStatus.partial);
    expect(
      partial.coordinatorResult!.status,
      SourceSearchCoordinatorStatus.partial,
    );
    expect(partial.coordinatorResult!.reasonCode, 'partial_source_results');
    expect(partial.coordinatorResult!.results.single.sourceId, 'first.anime');
    expect(partial.toString(), isNot(contains('TOP_SECRET_RESPONSE')));
  });

  test(
    'delegates close and stale suppression to the existing coordinator',
    () async {
      final package = _installed(_package('example.anime'));
      final transport = _DeferredTransport();
      final pipeline = _pipeline(transport);
      final pending = pipeline.search(
        installedPackages: [package],
        query: 'anime',
      );
      await transport.started.future;
      pipeline.close();
      transport.complete(_successBody(package.package.packageId, 'Late'));

      final result = await pending;
      expect(result.status, SourceInstalledLiveSearchPipelineStatus.failed);
      expect(
        result.coordinatorResult!.status,
        SourceSearchCoordinatorStatus.failed,
      );
      expect(result.coordinatorResult!.reasonCode, 'live_search_closed');
      expect(result.coordinatorResult!.results, isEmpty);
    },
  );

  test(
    'preserves coordinator stale results across the pipeline boundary',
    () async {
      final package = _installed(_package('example.anime'));
      final transport = _TwoCallDeferredTransport();
      final pipeline = _pipeline(transport);

      final first = pipeline.search(
        installedPackages: [package],
        query: 'first',
      );
      await transport.firstStarted.future;
      final second = pipeline.search(
        installedPackages: [package],
        query: 'second',
      );
      await transport.secondStarted.future;

      transport.completeFirst(_successBody(package.package.packageId, 'Old'));
      final stale = await first;
      expect(stale.status, SourceInstalledLiveSearchPipelineStatus.failed);
      expect(
        stale.coordinatorResult!.status,
        SourceSearchCoordinatorStatus.failed,
      );
      expect(stale.coordinatorResult!.reasonCode, 'stale_search');
      expect(stale.coordinatorResult!.results, isEmpty);

      transport.completeSecond(_successBody(package.package.packageId, 'New'));
      final current = await second;
      expect(current.status, SourceInstalledLiveSearchPipelineStatus.available);
      expect(current.coordinatorResult!.results.single.title, 'New');
    },
  );

  test(
    'rejects invalid queries through the factory without source I/O',
    () async {
      final package = _installed(_package('example.anime'));
      final transport = _QueueTransport([]);
      final result = await _pipeline(
        transport,
      ).search(installedPackages: [package], query: 'bad\u0001query');

      expect(
        result.status,
        SourceInstalledLiveSearchPipelineStatus.noUsableSources,
      );
      expect(
        result.packageResults.single.status,
        SourceLiveOperationPlanFactoryStatus.invalidInput,
      );
      expect(result.packageResults.single.reasonCode, 'invalid_search_query');
      expect(transport.requests, isEmpty);
    },
  );
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

SourcePackageManifest _package(
  String packageId, {
  int schemaVersion = 2,
  bool withSearch = true,
  VersionConstraint? constraint,
}) {
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
    schemaVersion: schemaVersion,
    packageId: packageId,
    displayName: packageId,
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: constraint ?? VersionConstraint.parse('^1.0.0'),
    securityPolicy: testSourcePolicy(),
    programs: [program],
    liveOperations: schemaVersion == 2 && withSearch
        ? [
            SourcePackageLiveOperation(
              kind: SourcePackageLiveOperationKind.search,
              programId: 'search',
              uriTemplate: 'https://example.com/search?q={query}',
              mapping: SourceSearchFieldMapping(
                subjectIdField: 'subjectId',
                titleField: 'title',
              ),
            ),
          ]
        : const [],
  );
}

InstalledSourcePackage _installed(
  SourcePackageManifest package, {
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) => InstalledSourcePackage(
  package: package,
  status: status,
  requiresConsent: requiresConsent,
  requiresReconsent: requiresReconsent,
);

SourceHttpTransportResult _successBody(String packageId, String title) {
  final body = title == '[]'
      ? '[]'
      : jsonEncode([
          {'subjectId': '$packageId-subject', 'title': title},
        ]);
  return SourceHttpTransportResult(
    status: SourceHttpTransportStatus.success,
    response: SourceHttpResponse(
      statusCode: 200,
      finalUri: Uri.parse('https://example.com/search'),
      redirectChain: const [],
      body: body,
    ),
  );
}

final class _QueueTransport implements SourceHttpTransport {
  _QueueTransport(this.results, {this.secondBody});

  final List<SourceHttpTransportResult> results;
  final String? secondBody;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    if (secondBody != null && requests.length == 2) {
      return SourceHttpTransportResult(
        status: SourceHttpTransportStatus.success,
        response: SourceHttpResponse(
          statusCode: 200,
          finalUri: request.uri,
          redirectChain: const [],
          body: secondBody!,
        ),
      );
    }
    final queued = results.removeAt(0);
    final response = queued.response!;
    return SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: response.statusCode,
        finalUri: request.uri,
        redirectChain: response.redirectChain,
        body: response.body,
      ),
    );
  }

  @override
  Future<void> close() async {}
}

final class _DeferredTransport implements SourceHttpTransport {
  final started = Completer<void>();
  final _result = Completer<SourceHttpTransportResult>();
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) {
    requests.add(request);
    if (!started.isCompleted) {
      started.complete();
    }
    return _result.future;
  }

  void complete(SourceHttpTransportResult result) => _result.complete(result);

  @override
  Future<void> close() async {}
}

final class _TwoCallDeferredTransport implements SourceHttpTransport {
  final firstStarted = Completer<void>();
  final secondStarted = Completer<void>();
  final _first = Completer<SourceHttpTransportResult>();
  final _second = Completer<SourceHttpTransportResult>();
  var _calls = 0;

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) {
    _calls++;
    if (_calls == 1) {
      firstStarted.complete();
      return _first.future;
    }
    secondStarted.complete();
    return _second.future;
  }

  void completeFirst(SourceHttpTransportResult result) =>
      _first.complete(result);

  void completeSecond(SourceHttpTransportResult result) =>
      _second.complete(result);

  @override
  Future<void> close() async {}
}

final class _OneShotPackages extends Iterable<InstalledSourcePackage> {
  _OneShotPackages(this.values);

  final List<InstalledSourcePackage> values;
  var iteratorCount = 0;

  @override
  Iterator<InstalledSourcePackage> get iterator {
    iteratorCount++;
    if (iteratorCount > 1) {
      throw StateError('one-shot iterable was consumed twice');
    }
    return values.iterator;
  }
}

final class _ThrowingPackages extends Iterable<InstalledSourcePackage> {
  @override
  Iterator<InstalledSourcePackage> get iterator =>
      (throw StateError('package snapshot failed'));
}

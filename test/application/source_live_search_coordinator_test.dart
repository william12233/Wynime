import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_search_coordinator.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_search_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/domain/services/source_search_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'composes ordered live packages into normalized search results',
    () async {
      final first = _package('first.anime');
      final second = _package('second.anime');
      final transport = _QueueTransport([
        _success(
          _request(),
          '<article class="item">'
          '<span class="id">101</span><h2>First</h2></article>',
        ),
        _success(
          _request(),
          '<article class="item">'
          '<span class="id">202</span><h2>Second</h2></article>',
        ),
      ]);
      final subject = _subject(transport);

      final result = await subject.search(
        query: 'anime',
        plans: [_plan(first), _plan(second)],
      );

      expect(result.status, SourceSearchCoordinatorStatus.available);
      expect(result.results.map((value) => value.subjectId), ['101', '202']);
      expect(result.results.map((value) => value.title), ['First', 'Second']);
      expect(result.sourceResults.map((value) => value.packageId), [
        'first.anime',
        'second.anime',
      ]);
      expect(transport.requests, hasLength(2));
    },
  );

  test(
    'live package admission failure stays per-source and becomes partial',
    () async {
      final enabled = _package('enabled.anime');
      final disabled = _package('disabled.anime');
      final transport = _QueueTransport([
        _success(
          _request(),
          '<article class="item">'
          '<span class="id">101</span><h2>Enabled</h2></article>',
        ),
      ]);
      final result = await _subject(transport).search(
        query: 'anime',
        plans: [
          _plan(enabled),
          _plan(
            disabled,
            installedPackage: _installed(
              disabled,
              status: SourcePackageStatus.disabled,
            ),
          ),
        ],
      );

      expect(result.status, SourceSearchCoordinatorStatus.partial);
      expect(result.results.single.title, 'Enabled');
      expect(
        result.sourceResults[1].status,
        SourceSearchNormalizationStatus.disabled,
      );
      expect(transport.requests, hasLength(1));
    },
  );

  test(
    'invalid query, duplicate plans and plan limits short-circuit I/O',
    () async {
      final package = _package('example.anime');
      final transport = _QueueTransport([_success(_request(), 'unused')]);
      final subject = _subject(transport);

      final invalid = await subject.search(query: ' ', plans: [_plan(package)]);
      expect(invalid.reasonCode, 'invalid_query');
      expect(transport.requests, isEmpty);

      final duplicate = await subject.search(
        query: 'anime',
        plans: [_plan(package), _plan(package)],
      );
      expect(duplicate.reasonCode, 'duplicate_source_plan');
      expect(transport.requests, isEmpty);

      final tooMany = await subject.search(
        query: 'anime',
        plans: List<SourceLiveSearchPlan>.generate(
          33,
          (index) => _plan(_package('example$index.anime')),
        ),
      );
      expect(tooMany.reasonCode, 'too_many_source_plans');
      expect(transport.requests, isEmpty);
    },
  );

  test('normalizer identity mismatch fails closed', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLiveSearchCoordinator(
      runtime: runtime.runtime,
      normalizer: _ForgedNormalizer(),
    );

    final result = await subject.search(
      query: 'anime',
      plans: [_plan(package)],
    );

    expect(result.status, SourceSearchCoordinatorStatus.failed);
    expect(result.reasonCode, 'source_search_failed');
    expect(
      result.sourceResults.single.status,
      SourceSearchNormalizationStatus.failed,
    );
    expect(result.sourceResults.single.results, isEmpty);
  });

  test('document fallback runs only after a static notFound result', () async {
    final package = _package('example.anime');
    final transport = _QueueTransport([_success(_request(), '<main></main>')]);
    final fallback = _RecordingDocumentFallback(
      package: package,
      subjectId: '303',
      title: 'Hydrated',
    );
    final runtime = _subject(transport).runtime;
    final subject = SourceLiveSearchCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourceSearchNormalizer(),
      documentFallback: fallback,
    );

    final result = await subject.search(
      query: 'anime',
      plans: [_plan(package)],
    );

    expect(result.status, SourceSearchCoordinatorStatus.available);
    expect(result.results.single.subjectId, '303');
    expect(result.results.single.title, 'Hydrated');
    expect(fallback.plans, hasLength(1));
    expect(transport.requests, hasLength(1));
  });

  test(
    'document fallback does not run after static transport failure',
    () async {
      final package = _package('example.anime');
      final transport = _QueueTransport([
        SourceHttpTransportResult(
          status: SourceHttpTransportStatus.networkError,
          reasonCode: 'network_error',
        ),
      ]);
      final fallback = _RecordingDocumentFallback(
        package: package,
        subjectId: '303',
        title: 'Must not be used',
      );
      final runtime = _subject(transport).runtime;
      final subject = SourceLiveSearchCoordinator(
        runtime: runtime,
        normalizer: const DeclarativeSourceSearchNormalizer(),
        documentFallback: fallback,
      );

      final result = await subject.search(
        query: 'anime',
        plans: [_plan(package)],
      );

      expect(result.status, SourceSearchCoordinatorStatus.failed);
      expect(result.results, isEmpty);
      expect(fallback.plans, isEmpty);
    },
  );

  test('invalid normalized result shape fails closed', () async {
    final package = _package('example.anime');
    final runtime = _subject(_QueueTransport([_success(_request(), 'unused')]));
    final subject = SourceLiveSearchCoordinator(
      runtime: runtime.runtime,
      normalizer: _InvalidShapeNormalizer(),
    );

    final result = await subject.search(
      query: 'anime',
      plans: [_plan(package)],
    );

    expect(result.status, SourceSearchCoordinatorStatus.failed);
    expect(
      result.sourceResults.single.status,
      SourceSearchNormalizationStatus.failed,
    );
    expect(
      result.sourceResults.single.diagnostics.single.code,
      'normalization_result_invalid',
    );
    expect(result.results, isEmpty);
  });

  test('a newer search supersedes an older pending response', () async {
    final package = _package('example.anime');
    final transport = _DeferredTransport();
    final subject = _subject(transport);

    final first = subject.search(query: 'first', plans: [_plan(package)]);
    await transport.firstStarted.future;
    final second = subject.search(query: 'second', plans: [_plan(package)]);
    await transport.secondStarted.future;

    transport.completeFirst(
      _success(
        _request(),
        '<article class="item">'
        '<span class="id">101</span><h2>Old</h2></article>',
      ),
    );
    final stale = await first;
    expect(stale.status, SourceSearchCoordinatorStatus.failed);
    expect(stale.reasonCode, 'stale_search');
    expect(stale.results, isEmpty);

    transport.completeSecond(
      _success(
        _request(),
        '<article class="item">'
        '<span class="id">101</span><h2>New</h2></article>',
      ),
    );
    final current = await second;
    expect(current.status, SourceSearchCoordinatorStatus.available);
    expect(current.results.single.title, 'New');
  });

  test(
    'close invalidates pending and future searches without returning records',
    () async {
      final package = _package('example.anime');
      final transport = _DeferredTransport();
      final subject = _subject(transport);

      final pending = subject.search(query: 'anime', plans: [_plan(package)]);
      await transport.firstStarted.future;
      subject.close();
      transport.completeFirst(
        _success(
          _request(),
          '<article class="item">'
          '<span class="id">101</span><h2>Late</h2></article>',
        ),
      );

      final closed = await pending;
      expect(closed.status, SourceSearchCoordinatorStatus.failed);
      expect(closed.reasonCode, 'live_search_closed');
      expect(closed.results, isEmpty);

      final afterClose = await subject.search(
        query: 'anime',
        plans: [_plan(package)],
      );
      expect(afterClose.reasonCode, 'live_search_closed');
      expect(afterClose.results, isEmpty);
    },
  );
}

_RuntimeSubject _subject(SourceHttpTransport transport) {
  final runtime = SourceLiveHttpPackageRuntime(
    httpExecutor: SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: Version.parse('1.0.0'),
      ),
      transport: transport,
    ),
    fixtureRuntime: DeclarativeSourcePackageRuntime(
      wynimeVersion: Version.parse('1.0.0'),
    ),
  );
  return _RuntimeSubject(
    runtime: runtime,
    coordinator: SourceLiveSearchCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourceSearchNormalizer(),
    ),
  );
}

SourceLiveSearchPlan _plan(
  SourcePackageManifest package, {
  InstalledSourcePackage? installedPackage,
}) => SourceLiveSearchPlan(
  requestPlan: SourceLiveHttpRequestPlan(
    installedPackage: installedPackage ?? _installed(package),
    programId: 'search',
    request: _request(),
  ),
  mapping: SourceSearchFieldMapping(
    subjectIdField: 'subjectId',
    titleField: 'title',
  ),
);

SourceHttpRequest _request() => SourceHttpRequest(
  uri: Uri.parse('https://example.com/search?q=anime'),
  securityPolicy: testSourcePolicy(),
  headers: const {'accept': 'text/html'},
  timeout: const Duration(seconds: 2),
);

SourceHttpTransportResult _success(SourceHttpRequest request, String body) =>
    SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: request.uri,
        redirectChain: const [],
        body: body,
      ),
    );

SourcePackageManifest _package(String packageId) => SourcePackageManifest(
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
      fields: [
        SourceFieldRule(
          name: 'subjectId',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.id',
          ),
        ),
        SourceFieldRule(
          name: 'title',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: 'h2',
          ),
        ),
      ],
      resultLimit: 10,
    ),
  ],
);

InstalledSourcePackage _installed(
  SourcePackageManifest package, {
  SourcePackageStatus status = SourcePackageStatus.enabled,
}) => InstalledSourcePackage(
  package: package,
  status: status,
  requiresConsent: false,
  requiresReconsent: false,
);

final class _RuntimeSubject {
  const _RuntimeSubject({required this.runtime, required this.coordinator});

  final SourceLiveHttpPackageRuntime runtime;
  final SourceLiveSearchCoordinator coordinator;

  Future<SourceSearchCoordinatorResult> search({
    required String query,
    required Iterable<SourceLiveSearchPlan> plans,
  }) => coordinator.search(query: query, plans: plans);

  void close() => coordinator.close();
}

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

final class _DeferredTransport implements SourceHttpTransport {
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

final class _ForgedNormalizer implements SourceSearchNormalizer {
  @override
  SourceSearchNormalizationResult normalizeSearch({
    required SourceRuntimeResult runtimeResult,
    required SourceSearchFieldMapping mapping,
  }) => SourceSearchNormalizationResult(
    packageId: 'forged.package',
    packageVersion: runtimeResult.packageVersion,
    programId: runtimeResult.programId,
    status: SourceSearchNormalizationStatus.available,
    results: [
      SourceSearchResult(
        sourceId: 'forged.package',
        subjectId: 'forged',
        title: 'Forged',
      ),
    ],
    diagnostics: const [],
  );
}

final class _InvalidShapeNormalizer implements SourceSearchNormalizer {
  @override
  SourceSearchNormalizationResult normalizeSearch({
    required SourceRuntimeResult runtimeResult,
    required SourceSearchFieldMapping mapping,
  }) => SourceSearchNormalizationResult(
    packageId: runtimeResult.packageId,
    packageVersion: runtimeResult.packageVersion,
    programId: runtimeResult.programId,
    status: SourceSearchNormalizationStatus.available,
    results: const [],
    diagnostics: const [],
  );
}

final class _RecordingDocumentFallback
    implements SourceLiveSearchDocumentFallback {
  _RecordingDocumentFallback({
    required this.package,
    required this.subjectId,
    required this.title,
  });

  final SourcePackageManifest package;
  final String subjectId;
  final String title;
  final plans = <SourceLiveSearchPlan>[];

  @override
  Future<SourceRuntimeResult> capture(SourceLiveSearchPlan plan) async {
    plans.add(plan);
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.requestPlan.programId,
      status: SourceRuntimeStatus.available,
      records: [
        SourceRuntimeRecord({'subjectId': subjectId, 'title': title}),
      ],
      diagnostics: const [],
      consumedSteps: 2,
      selectorMatches: 1,
    );
  }
}

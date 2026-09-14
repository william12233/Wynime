import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_installed_live_episode_pipeline.dart';
import 'package:wynime/src/application/source_live_episode_coordinator.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/domain/models/source_episode_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_episode_normalization_models.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/domain/services/source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_episode_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'composes one schema-v2 target through the live chain with exact provenance',
    () async {
      final package = _package('example.anime');
      final installed = _installed(package);
      final target = _target(installed);
      final transport = _QueueTransport([
        _successEpisode('example.anime', 'Episode 1'),
      ]);
      final pipeline = _pipeline(transport);

      final result = await pipeline.listEpisodes(targets: [target]);

      expect(result.status, SourceInstalledLiveEpisodePipelineStatus.available);
      expect(
        result.coordinatorResult!.status,
        SourceEpisodeCoordinatorStatus.available,
      );
      expect(result.targetResults, hasLength(1));
      expect(result.targetResults.single.target, same(target));
      expect(result.readyTargets.single, same(target));
      final plan = result.readyPlans.single;
      expect(result.targetResults.single.plan, same(plan));
      expect(plan.requestPlan.installedPackage, same(installed));
      expect(
        plan.requestPlan.request.securityPolicy,
        same(package.securityPolicy),
      );
      expect(plan.mapping, same(_episodeMapping));
      expect(transport.requests, hasLength(1));
      expect(transport.requests.single, same(plan.requestPlan.request));
      expect(
        transport.requests.single.uri.path,
        '/anime/example.anime/subject-1/episodes',
      );
      expect(transport.requests.single.uri.queryParameters['line'], 'line-1');
      expect(
        transport.requests.single.uri.queryParameters['episode'],
        'episode-1',
      );
      expect(result.coordinatorResult!.episodes.single.title, 'Episode 1');
      expect(
        result.coordinatorResult!.episodes.single.identity,
        SourceEpisodeIdentity(
          sourceId: 'example.anime',
          lineId: 'line-1',
          subjectId: 'subject-1',
          episodeId: 'episode-1',
        ),
      );
    },
  );

  test(
    'keeps multiple exact targets and normalized episodes in caller order',
    () async {
      final first = _target(_installed(_package('first.anime')));
      final second = _target(_installed(_package('second.anime')));
      final transport = _QueueTransport([
        _successEpisode('first.anime', 'First episode'),
        _successEpisode('second.anime', 'Second episode'),
      ]);

      final result = await _pipeline(
        transport,
      ).listEpisodes(targets: [first, second]);

      expect(result.status, SourceInstalledLiveEpisodePipelineStatus.available);
      expect(result.targetResults.map((value) => value.target), [
        first,
        second,
      ]);
      expect(result.targetResults.map((value) => value.planResult.status), [
        SourceLiveOperationPlanFactoryStatus.ready,
        SourceLiveOperationPlanFactoryStatus.ready,
      ]);
      expect(result.readyPlans, hasLength(2));
      expect(result.readyPlans.first, same(result.targetResults.first.plan));
      expect(result.readyPlans.last, same(result.targetResults.last.plan));
      expect(
        result.coordinatorResult!.episodes.map(
          (value) => value.identity.sourceId,
        ),
        ['first.anime', 'second.anime'],
      );
      expect(result.coordinatorResult!.episodes.map((value) => value.title), [
        'First episode',
        'Second episode',
      ]);
      expect(transport.requests, hasLength(2));
    },
  );

  test('keeps typed factory rejections while ready targets continue', () async {
    final disabled = _target(
      _installed(
        _package('disabled.anime'),
        status: SourcePackageStatus.disabled,
      ),
    );
    final valid = _target(_installed(_package('valid.anime')));
    final consent = _target(
      _installed(_package('consent.anime'), requiresConsent: true),
    );
    final incompatible = _target(
      _installed(
        _package(
          'incompatible.anime',
          constraint: VersionConstraint.parse('^2.0.0'),
        ),
      ),
    );
    final missingOperation = _target(
      _installed(_package('missing.anime', withEpisode: false)),
    );
    final identityMismatch = _target(
      _installed(_package('mismatch.anime')),
      episode: _episode(sourceId: 'other.anime'),
    );
    final invalidIdentity = _target(
      _installed(_package('invalid.anime')),
      episode: _episode(episodeId: 'bad\u0001episode'),
    );
    final transport = _QueueTransport([
      _successEpisode('valid.anime', 'Valid episode'),
    ]);

    final result = await _pipeline(transport).listEpisodes(
      targets: [
        disabled,
        valid,
        consent,
        incompatible,
        missingOperation,
        identityMismatch,
        invalidIdentity,
      ],
    );

    expect(result.status, SourceInstalledLiveEpisodePipelineStatus.partial);
    expect(result.reasonCode, 'partial_target_preflight');
    expect(
      result.coordinatorResult!.status,
      SourceEpisodeCoordinatorStatus.available,
    );
    expect(result.targetResults.map((value) => value.target), [
      disabled,
      valid,
      consent,
      incompatible,
      missingOperation,
      identityMismatch,
      invalidIdentity,
    ]);
    expect(result.targetResults.map((value) => value.planResult.status), [
      SourceLiveOperationPlanFactoryStatus.disabled,
      SourceLiveOperationPlanFactoryStatus.ready,
      SourceLiveOperationPlanFactoryStatus.consentRequired,
      SourceLiveOperationPlanFactoryStatus.incompatible,
      SourceLiveOperationPlanFactoryStatus.operationNotFound,
      SourceLiveOperationPlanFactoryStatus.invalidInput,
      SourceLiveOperationPlanFactoryStatus.invalidInput,
    ]);
    expect(
      result.targetResults[5].planResult.reasonCode,
      'episode_identity_mismatch',
    );
    expect(
      result.targetResults[6].planResult.reasonCode,
      'episode_identity_mismatch',
    );
    expect(result.readyTargets.single, same(valid));
    expect(result.coordinatorResult!.episodes.single.title, 'Valid episode');
    expect(transport.requests, hasLength(1));
  });

  test(
    'returns no usable sources and performs zero live I/O when all reject',
    () async {
      final targets = [
        _target(
          _installed(
            _package('disabled.anime'),
            status: SourcePackageStatus.disabled,
          ),
        ),
        _target(_installed(_package('consent.anime'), requiresReconsent: true)),
        _target(
          _installed(
            _package(
              'incompatible.anime',
              constraint: VersionConstraint.parse('^2.0.0'),
            ),
          ),
        ),
        _target(_installed(_package('missing.anime', withEpisode: false))),
        _target(
          _installed(_package('mismatch.anime')),
          episode: _episode(sourceId: 'other.anime'),
        ),
        _target(
          _installed(_package('invalid.anime')),
          episode: _episode(episodeId: 'bad\u0001episode'),
        ),
      ];
      final transport = _QueueTransport([]);

      final result = await _pipeline(transport).listEpisodes(targets: targets);

      expect(
        result.status,
        SourceInstalledLiveEpisodePipelineStatus.noUsableSources,
      );
      expect(result.reasonCode, 'no_usable_episode_sources');
      expect(result.coordinatorResult, isNull);
      expect(result.readyTargetCount, 0);
      expect(result.rejectedTargetCount, targets.length);
      expect(transport.requests, isEmpty);
    },
  );

  test('admits exactly 32 targets at the configured boundary', () async {
    final targets = List<SourceInstalledLiveEpisodeTarget>.generate(
      32,
      (index) => _target(_installed(_package('source$index.anime'))),
    );
    final transport = _QueueTransport(
      List<SourceHttpTransportResult>.generate(
        32,
        (index) => _successEpisode('source$index.anime', 'Episode $index'),
      ),
    );

    final result = await _pipeline(transport).listEpisodes(targets: targets);

    expect(result.status, SourceInstalledLiveEpisodePipelineStatus.available);
    expect(result.targetResults, hasLength(32));
    expect(result.readyTargetCount, 32);
    expect(result.rejectedTargetCount, 0);
    expect(result.coordinatorResult!.episodes, hasLength(32));
    expect(transport.requests, hasLength(32));
    expect(result.targetResults.first.target, same(targets.first));
    expect(result.targetResults.last.target, same(targets.last));
  });

  test(
    'rejects 33rd, duplicate and throwing snapshots before source work',
    () async {
      final overBoundTransport = _QueueTransport(
        List<SourceHttpTransportResult>.generate(
          33,
          (index) => _successEpisode('source$index.anime', 'unused'),
        ),
      );
      final overBound = await _pipeline(overBoundTransport).listEpisodes(
        targets: List<SourceInstalledLiveEpisodeTarget>.generate(
          33,
          (index) => _target(_installed(_package('source$index.anime'))),
        ),
      );
      expect(overBound.status, SourceInstalledLiveEpisodePipelineStatus.failed);
      expect(overBound.reasonCode, 'too_many_episode_targets');
      expect(overBound.targetResults, isEmpty);
      expect(overBoundTransport.requests, isEmpty);

      final duplicateTarget = _target(_installed(_package('duplicate.anime')));
      final duplicateTransport = _QueueTransport([
        _successEpisode('duplicate.anime', 'unused'),
      ]);
      final duplicate = await _pipeline(
        duplicateTransport,
      ).listEpisodes(targets: [duplicateTarget, duplicateTarget]);
      expect(duplicate.status, SourceInstalledLiveEpisodePipelineStatus.failed);
      expect(duplicate.reasonCode, 'duplicate_episode_target');
      expect(duplicate.targetResults, isEmpty);
      expect(duplicateTransport.requests, isEmpty);

      final throwingTransport = _QueueTransport([]);
      final throwing = await _pipeline(
        throwingTransport,
      ).listEpisodes(targets: _ThrowingTargets());
      expect(throwing.status, SourceInstalledLiveEpisodePipelineStatus.failed);
      expect(throwing.reasonCode, 'invalid_episode_targets');
      expect(throwing.targetResults, isEmpty);
      expect(throwingTransport.requests, isEmpty);
    },
  );

  test(
    'snapshots a one-shot iterable before factory and live execution',
    () async {
      final first = _target(_installed(_package('first.anime')));
      final second = _target(_installed(_package('second.anime')));
      final targets = _OneShotTargets([first, second]);
      final transport = _QueueTransport([
        _successEpisode('first.anime', 'First'),
        _successEpisode('second.anime', 'Second'),
      ]);

      final pending = _pipeline(transport).listEpisodes(targets: targets);
      targets.values.clear();
      final result = await pending;

      expect(result.status, SourceInstalledLiveEpisodePipelineStatus.available);
      expect(targets.iteratorCount, 1);
      expect(result.targetResults.map((value) => value.target), [
        first,
        second,
      ]);
      expect(result.coordinatorResult!.episodes, hasLength(2));
      expect(transport.requests, hasLength(2));
    },
  );

  test(
    'preserves downstream notFound, partial, noSources and failed semantics',
    () async {
      final notFoundTarget = _target(_installed(_package('empty.anime')));
      final notFound = await _pipeline(
        _QueueTransport([_successEpisode('empty.anime', '[]')]),
      ).listEpisodes(targets: [notFoundTarget]);
      expect(
        notFound.status,
        SourceInstalledLiveEpisodePipelineStatus.notFound,
      );
      expect(
        notFound.coordinatorResult!.status,
        SourceEpisodeCoordinatorStatus.notFound,
      );

      final first = _target(_installed(_package('first.anime')));
      final second = _target(_installed(_package('second.anime')));
      final partial = await _pipeline(
        _QueueTransport([
          _successEpisode('first.anime', 'First'),
          _successBody('not-json'),
        ]),
      ).listEpisodes(targets: [first, second]);
      expect(partial.status, SourceInstalledLiveEpisodePipelineStatus.partial);
      expect(
        partial.coordinatorResult!.status,
        SourceEpisodeCoordinatorStatus.partial,
      );
      expect(partial.coordinatorResult!.episodes.single.title, 'First');

      final blocked = await _pipeline(
        _QueueTransport([_successEpisode('blocked.anime', 'unused')]),
        fixtureRuntime: const _FixedStatusRuntime(SourceRuntimeStatus.disabled),
      ).listEpisodes(targets: [_target(_installed(_package('blocked.anime')))]);
      expect(
        blocked.status,
        SourceInstalledLiveEpisodePipelineStatus.noSources,
      );
      expect(
        blocked.coordinatorResult!.status,
        SourceEpisodeCoordinatorStatus.noSources,
      );

      final failed = await _pipeline(
        _QueueTransport([
          SourceHttpTransportResult(
            status: SourceHttpTransportStatus.networkError,
            reasonCode: 'network_error',
          ),
        ]),
      ).listEpisodes(targets: [_target(_installed(_package('failed.anime')))]);
      expect(failed.status, SourceInstalledLiveEpisodePipelineStatus.failed);
      expect(
        failed.coordinatorResult!.status,
        SourceEpisodeCoordinatorStatus.failed,
      );
      expect(failed.coordinatorResult!.reasonCode, 'source_episode_failed');
    },
  );

  test(
    'package rejection cannot turn a downstream notFound into success',
    () async {
      final ready = _target(_installed(_package('empty.anime')));
      final rejected = _target(
        _installed(
          _package('disabled.anime'),
          status: SourcePackageStatus.disabled,
        ),
      );
      final result = await _pipeline(
        _QueueTransport([_successEpisode('empty.anime', '[]')]),
      ).listEpisodes(targets: [ready, rejected]);

      expect(result.status, SourceInstalledLiveEpisodePipelineStatus.partial);
      expect(
        result.coordinatorResult!.status,
        SourceEpisodeCoordinatorStatus.notFound,
      );
      expect(
        result.status,
        isNot(SourceInstalledLiveEpisodePipelineStatus.available),
      );
    },
  );

  test(
    'delegates close and stale suppression to the existing coordinator',
    () async {
      final target = _target(_installed(_package('example.anime')));
      final closeTransport = _DeferredTransport();
      final closePipeline = _pipeline(closeTransport);
      final pending = closePipeline.listEpisodes(targets: [target]);
      await closeTransport.started.future;
      closePipeline.close();
      closeTransport.complete(_successEpisode('example.anime', 'Late'));

      final closed = await pending;
      expect(closed.status, SourceInstalledLiveEpisodePipelineStatus.failed);
      expect(
        closed.coordinatorResult!.status,
        SourceEpisodeCoordinatorStatus.failed,
      );
      expect(closed.coordinatorResult!.reasonCode, 'live_episode_closed');
      expect(closed.coordinatorResult!.episodes, isEmpty);

      final staleTransport = _TwoCallDeferredTransport();
      final stalePipeline = _pipeline(staleTransport);
      final first = stalePipeline.listEpisodes(targets: [target]);
      await staleTransport.firstStarted.future;
      final second = stalePipeline.listEpisodes(targets: [target]);
      await staleTransport.secondStarted.future;

      staleTransport.completeFirst(_successEpisode('example.anime', 'Old'));
      final stale = await first;
      expect(stale.status, SourceInstalledLiveEpisodePipelineStatus.failed);
      expect(stale.coordinatorResult!.reasonCode, 'stale_episode');
      expect(stale.coordinatorResult!.episodes, isEmpty);

      staleTransport.completeSecond(_successEpisode('example.anime', 'New'));
      final current = await second;
      expect(
        current.status,
        SourceInstalledLiveEpisodePipelineStatus.available,
      );
      expect(current.coordinatorResult!.episodes.single.title, 'New');
    },
  );

  test('keeps output immutable and diagnostics redacted', () async {
    final target = _target(
      _installed(_package('secret.anime')),
      episode: _episode(episodeId: 'TOP_SECRET_EPISODE'),
    );
    final result = await _pipeline(
      _QueueTransport([_successEpisode('secret.anime', 'TOP_SECRET_RESPONSE')]),
    ).listEpisodes(targets: [target]);

    expect(
      () => result.targetResults.add(result.targetResults.single),
      throwsUnsupportedError,
    );
    expect(result.toString(), isNot(contains('TOP_SECRET_EPISODE')));
    expect(result.toString(), isNot(contains('TOP_SECRET_RESPONSE')));
    expect(result.toString(), isNot(contains('https://example.com')));
    expect(result.toString(), contains('secret.anime'));
  });
}

SourceInstalledLiveEpisodePipeline _pipeline(
  SourceHttpTransport transport, {
  SourcePackageRuntime? fixtureRuntime,
}) {
  final version = Version.parse('1.0.0');
  final runtime = SourceLiveHttpPackageRuntime(
    httpExecutor: SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: version,
      ),
      transport: transport,
    ),
    fixtureRuntime:
        fixtureRuntime ??
        DeclarativeSourcePackageRuntime(wynimeVersion: version),
  );
  return SourceInstalledLiveEpisodePipeline(
    planFactory: SourceLiveOperationPlanFactory(wynimeVersion: version),
    episodeCoordinator: SourceLiveEpisodeCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourceEpisodeNormalizer(),
    ),
  );
}

final _policy = testSourcePolicy();
final _episodeMapping = SourceEpisodeFieldMapping(
  lineIdField: 'lineId',
  subjectIdField: 'subjectId',
  episodeIdField: 'episodeId',
  titleField: 'episodeTitle',
);
final _searchMapping = SourceSearchFieldMapping(
  subjectIdField: 'subjectId',
  titleField: 'searchTitle',
);

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

SourceInstalledLiveEpisodeTarget _target(
  InstalledSourcePackage installed, {
  SourceEpisodeIdentity? episode,
}) => SourceInstalledLiveEpisodeTarget(
  installedPackage: installed,
  episode: episode ?? _episode(sourceId: installed.package.packageId),
);

SourceEpisodeIdentity _episode({
  String sourceId = 'example.anime',
  String lineId = 'line-1',
  String subjectId = 'subject-1',
  String episodeId = 'episode-1',
}) => SourceEpisodeIdentity(
  sourceId: sourceId,
  lineId: lineId,
  subjectId: subjectId,
  episodeId: episodeId,
);

SourcePackageManifest _package(
  String packageId, {
  bool withEpisode = true,
  VersionConstraint? constraint,
}) {
  final episodeProgram = _program('episode', [
    'lineId',
    'subjectId',
    'episodeId',
    'episodeTitle',
  ]);
  final searchProgram = _program('search', ['subjectId', 'searchTitle']);
  final liveOperations = withEpisode
      ? [
          SourcePackageLiveOperation(
            kind: SourcePackageLiveOperationKind.episode,
            programId: 'episode',
            uriTemplate:
                'https://example.com/anime/{sourceId}/{subjectId}/episodes?line={lineId}&episode={episodeId}',
            mapping: _episodeMapping,
          ),
        ]
      : [
          SourcePackageLiveOperation(
            kind: SourcePackageLiveOperationKind.search,
            programId: 'search',
            uriTemplate: 'https://example.com/search?q={query}',
            mapping: _searchMapping,
          ),
        ];
  return SourcePackageManifest(
    schemaVersion: 2,
    packageId: packageId,
    displayName: packageId,
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: constraint ?? VersionConstraint.parse('^1.0.0'),
    securityPolicy: _policy,
    programs: [episodeProgram, searchProgram],
    liveOperations: liveOperations,
  );
}

SourceRuleProgram _program(String id, List<String> fields) => SourceRuleProgram(
  programId: id,
  documentKind: SourceDocumentKind.json,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.jsonPath,
    expression: r'$[*]',
  ),
  fields: fields
      .map(
        (name) => SourceFieldRule(
          name: name,
          valueKind: SourceValueKind.raw,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.jsonPath,
            expression: r'$.' + name,
          ),
        ),
      )
      .toList(growable: false),
  resultLimit: 20,
);

SourceHttpTransportResult _successEpisode(String packageId, String title) {
  final body = title == '[]'
      ? '[]'
      : jsonEncode([
          {
            'lineId': 'line-1',
            'subjectId': 'subject-1',
            'episodeId': 'episode-1',
            'episodeTitle': title,
          },
        ]);
  return SourceHttpTransportResult(
    status: SourceHttpTransportStatus.success,
    response: SourceHttpResponse(
      statusCode: 200,
      finalUri: Uri.parse('https://example.com/episodes/$packageId'),
      redirectChain: const [],
      body: body,
    ),
  );
}

SourceHttpTransportResult _successBody(String body) =>
    SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: Uri.parse('https://example.com/episodes'),
        redirectChain: const [],
        body: body,
      ),
    );

final class _QueueTransport implements SourceHttpTransport {
  _QueueTransport(this.results);

  final List<SourceHttpTransportResult> results;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    if (results.isEmpty) {
      return SourceHttpTransportResult(
        status: SourceHttpTransportStatus.failed,
        reasonCode: 'queue_empty',
      );
    }
    final queued = results.removeAt(0);
    if (queued.status != SourceHttpTransportStatus.success) {
      return queued;
    }
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

final class _OneShotTargets extends Iterable<SourceInstalledLiveEpisodeTarget> {
  _OneShotTargets(this.values);

  final List<SourceInstalledLiveEpisodeTarget> values;
  var iteratorCount = 0;

  @override
  Iterator<SourceInstalledLiveEpisodeTarget> get iterator {
    iteratorCount++;
    if (iteratorCount > 1) {
      throw StateError('one-shot iterable was consumed twice');
    }
    return values.iterator;
  }
}

final class _ThrowingTargets
    extends Iterable<SourceInstalledLiveEpisodeTarget> {
  @override
  Iterator<SourceInstalledLiveEpisodeTarget> get iterator =>
      (throw StateError('target snapshot failed'));
}

final class _FixedStatusRuntime implements SourcePackageRuntime {
  const _FixedStatusRuntime(this.status);

  final SourceRuntimeStatus status;

  @override
  SourceRuntimeResult executeFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
  }) => SourceRuntimeResult(
    packageId: installedPackage.package.packageId,
    packageVersion: installedPackage.package.version,
    programId: programId,
    status: status,
    records: const [],
    diagnostics: const [],
    consumedSteps: 0,
    selectorMatches: 0,
  );
}

import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_installed_live_episode_pipeline.dart';
import 'package:wynime/src/application/source_installed_live_playback_pipeline.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_pipeline.dart';
import 'package:wynime/src/application/source_live_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_session_request_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';

import '../helpers/playback_test_support.dart';
import '../helpers/source_rule_test_support.dart' as source_support;

void main() {
  test(
    'traverses factory-generated playable plan through one exact playback lifecycle',
    () async {
      final package = _package('demo.anime');
      final target = _target(package);
      final transport = _QueueTransport([_playableBody(target.episode)]);
      final harness = _harness(transport);
      addTearDown(harness.close);

      final adRemovalPlan = testAdRemovalPlan(target.episode);
      final proxyBudget = testProxyBudget();
      final result = await harness.pipeline.openLive(
        targets: [target],
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: 41,
        proxyBudget: proxyBudget,
        preference: SourcePlaybackRoutePreference(
          packageId: package.packageId,
          packageVersion: package.version,
          programId: 'playable',
          sourceKey: 'primary',
        ),
        addressFamily: LoopbackAddressFamily.ipv6,
        refreshLeeway: const Duration(seconds: 41),
        maxAutomaticRefreshes: 2,
        episodeDuration: const Duration(minutes: 24),
      );

      expect(result.status, SourceInstalledLivePlaybackPipelineStatus.opened);
      expect(
        result.playbackResult!.status,
        SourceLivePlaybackPipelineStatus.opened,
      );
      expect(result.session, same(harness.coordinator.currentSession));
      expect(result.session, same(harness.player.openedSessions.single));
      expect(result.readyTargetCount, 1);
      expect(result.readyPlans.single, same(result.targetResults.single.plan));
      expect(
        result.targetResults.single.plan!.requestPlan.installedPackage,
        same(target.installedPackage),
      );
      expect(result.targetResults.single.plan!.episode, same(target.episode));
      expect(transport.requests, hasLength(1));
      expect(
        transport.requests.single,
        same(result.targetResults.single.plan!.requestPlan.request),
      );
      expect(harness.resolver.requests, hasLength(1));
      expect(harness.resolver.requests.single.episode, same(target.episode));
      expect(
        harness.resolver.requests.single.candidate.uri,
        Uri.parse('https://example.com/media/primary.m3u8'),
      );
      expect(
        harness.resolver.requests.single.candidate.sourceEventSequence,
        41,
      );
      expect(harness.resolver.requests.single.candidate.headers, isEmpty);
      expect(harness.resolver.requests.single.cookies, isEmpty);
      expect(harness.resolver.requests.single.userAgent, isNull);
      expect(harness.proxy.requests.single.budget, same(proxyBudget));
      expect(
        harness.proxy.requests.single.addressFamily,
        LoopbackAddressFamily.ipv6,
      );
      expect(harness.player.openedSessions, hasLength(1));
    },
  );

  test(
    'keeps rejected alternatives while a valid exact target opens through TASK-052',
    () async {
      final disabledPackage = _package('disabled.anime');
      final validPackage = _package('valid.anime');
      final disabled = _target(
        disabledPackage,
        status: SourcePackageStatus.disabled,
      );
      final valid = _target(validPackage);
      final transport = _QueueTransport([_playableBody(valid.episode)]);
      final harness = _harness(transport);
      addTearDown(harness.close);

      final result = await harness.pipeline.openLive(
        targets: [disabled, valid],
        adRemovalPlan: testAdRemovalPlan(valid.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );

      expect(result.status, SourceInstalledLivePlaybackPipelineStatus.partial);
      expect(result.reasonCode, 'partial_target_preflight');
      expect(
        result.playbackResult!.status,
        SourceLivePlaybackPipelineStatus.opened,
      );
      expect(result.session, same(harness.coordinator.currentSession));
      expect(result.targetResults, hasLength(2));
      expect(
        result.targetResults.first.planResult.status,
        SourceLiveOperationPlanFactoryStatus.disabled,
      );
      expect(result.targetResults.first.plan, isNull);
      expect(
        result.targetResults.last.planResult.status,
        SourceLiveOperationPlanFactoryStatus.ready,
      );
      expect(result.readyPlans.single, same(result.targetResults.last.plan));
      expect(transport.requests, hasLength(1));
      expect(harness.resolver.requests.single.episode, same(valid.episode));
      expect(harness.player.openedSessions, hasLength(1));
    },
  );

  test(
    'retains the complete factory preflight matrix and performs zero work when no target is usable',
    () async {
      final disabled = _target(
        _package('disabled.anime'),
        status: SourcePackageStatus.disabled,
      );
      final consent = _target(_package('consent.anime'), requiresConsent: true);
      final reconsent = _target(
        _package('reconsent.anime'),
        requiresReconsent: true,
      );
      final incompatible = _target(
        _package(
          'incompatible.anime',
          constraint: VersionConstraint.parse('^2.0.0'),
        ),
      );
      final missing = _target(
        _package('legacy.anime', schemaVersion: 1, withPlayable: false),
      );
      final invalidIdentity = _target(
        _package('invalid.anime'),
        episode: SourceEpisodeIdentity(
          sourceId: 'other.anime',
          lineId: 'line-1',
          subjectId: 'subject-1',
          episodeId: 'episode-1',
        ),
      );
      final transport = _QueueTransport(const []);
      final harness = _harness(transport);
      addTearDown(harness.close);

      final result = await harness.pipeline.openLive(
        targets: [
          disabled,
          consent,
          reconsent,
          incompatible,
          missing,
          invalidIdentity,
        ],
        adRemovalPlan: testAdRemovalPlan(disabled.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );

      expect(
        result.status,
        SourceInstalledLivePlaybackPipelineStatus.noUsableSources,
      );
      expect(result.reasonCode, 'no_usable_playback_sources');
      expect(result.playbackResult, isNull);
      expect(result.readyTargetCount, 0);
      expect(result.rejectedTargetCount, 6);
      expect(result.targetResults.map((value) => value.planResult.status), [
        SourceLiveOperationPlanFactoryStatus.disabled,
        SourceLiveOperationPlanFactoryStatus.consentRequired,
        SourceLiveOperationPlanFactoryStatus.consentRequired,
        SourceLiveOperationPlanFactoryStatus.incompatible,
        SourceLiveOperationPlanFactoryStatus.operationNotFound,
        SourceLiveOperationPlanFactoryStatus.invalidInput,
      ]);
      expect(transport.requests, isEmpty);
      expect(harness.resolver.requests, isEmpty);
      expect(harness.proxy.requests, isEmpty);
      expect(harness.player.openedSessions, isEmpty);
    },
  );

  test(
    'accepts exactly 32 distinct installed episode targets in caller order',
    () async {
      final targets = List<SourceInstalledLiveEpisodeTarget>.generate(32, (
        index,
      ) {
        final package = _package('source$index.anime');
        return _target(
          package,
          episode: _episode(package.packageId, episodeId: 'episode-$index'),
        );
      });
      final transport = _QueueTransport(
        targets.map((target) => _playableBody(target.episode)).toList(),
      );
      final harness = _harness(transport);
      addTearDown(harness.close);

      final result = await harness.pipeline.openLive(
        targets: targets,
        adRemovalPlan: testAdRemovalPlan(targets.first.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );

      expect(result.status, SourceInstalledLivePlaybackPipelineStatus.opened);
      expect(result.targetResults, hasLength(32));
      expect(result.readyTargetCount, 32);
      expect(
        result.targetResults.map((value) => value.target.episode.episodeId),
        List<String>.generate(32, (index) => 'episode-$index'),
      );
      expect(transport.requests, hasLength(32));
      expect(harness.resolver.requests, hasLength(1));
      expect(harness.player.openedSessions, hasLength(1));
    },
  );

  test(
    'rejects 33rd, duplicate and throwing target snapshots before factory or live I/O',
    () async {
      final base = _target(_package('base.anime'));

      final overBoundTransport = _QueueTransport(const []);
      final overBoundHarness = _harness(overBoundTransport);
      addTearDown(overBoundHarness.close);
      final overBound = await overBoundHarness.pipeline.openLive(
        targets: List<SourceInstalledLiveEpisodeTarget>.generate(
          33,
          (index) => _target(_package('over$index.anime')),
        ),
        adRemovalPlan: testAdRemovalPlan(base.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );
      expect(
        overBound.status,
        SourceInstalledLivePlaybackPipelineStatus.failed,
      );
      expect(overBound.reasonCode, 'too_many_episode_targets');
      expect(overBound.targetResults, isEmpty);
      expect(overBoundTransport.requests, isEmpty);
      expect(overBoundHarness.resolver.requests, isEmpty);
      expect(overBoundHarness.player.openedSessions, isEmpty);

      final duplicateTransport = _QueueTransport(const []);
      final duplicateHarness = _harness(duplicateTransport);
      addTearDown(duplicateHarness.close);
      final duplicate = await duplicateHarness.pipeline.openLive(
        targets: [base, base],
        adRemovalPlan: testAdRemovalPlan(base.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );
      expect(
        duplicate.status,
        SourceInstalledLivePlaybackPipelineStatus.failed,
      );
      expect(duplicate.reasonCode, 'duplicate_episode_target');
      expect(duplicateTransport.requests, isEmpty);

      final throwingTransport = _QueueTransport(const []);
      final throwingHarness = _harness(throwingTransport);
      addTearDown(throwingHarness.close);
      final throwing = await throwingHarness.pipeline.openLive(
        targets: _ThrowingTargets(),
        adRemovalPlan: testAdRemovalPlan(base.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );
      expect(throwing.status, SourceInstalledLivePlaybackPipelineStatus.failed);
      expect(throwing.reasonCode, 'invalid_episode_targets');
      expect(throwingTransport.requests, isEmpty);
    },
  );

  test(
    'snapshots a one-shot target iterable before TASK-052 begins I/O',
    () async {
      final first = _target(_package('first.anime'));
      final second = _target(_package('second.anime'));
      final targets = _OneShotTargets([first, second]);
      final transport = _QueueTransport([
        _playableBody(first.episode),
        _playableBody(second.episode),
      ]);
      final harness = _harness(transport);
      addTearDown(harness.close);

      final pending = harness.pipeline.openLive(
        targets: targets,
        adRemovalPlan: testAdRemovalPlan(first.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );
      targets.values.clear();
      final result = await pending;

      expect(result.status, SourceInstalledLivePlaybackPipelineStatus.opened);
      expect(targets.iteratorCount, 1);
      expect(result.targetResults, hasLength(2));
      expect(transport.requests, hasLength(2));
    },
  );

  test(
    'preserves each typed TASK-052 route, session and open rejection',
    () async {
      final target = _target(_package('failure.anime'));
      final transport = _QueueTransport([
        _playableBody(target.episode),
        _playableBody(target.episode),
        _playableBody(target.episode),
        _playableBody(target.episode),
      ]);
      final harness = _harness(transport);
      addTearDown(harness.close);

      final route = await harness.pipeline.openLive(
        targets: [target],
        adRemovalPlan: testAdRemovalPlan(target.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
        preference: SourcePlaybackRoutePreference(
          packageId: target.installedPackage.package.packageId,
          packageVersion: target.installedPackage.package.version,
          programId: 'playable',
          sourceKey: 'missing',
        ),
      );
      expect(route.status, SourceInstalledLivePlaybackPipelineStatus.notOpened);
      expect(
        route.playbackResult!.failureStage,
        SourceLivePlaybackPipelineFailureStage.route,
      );
      expect(
        route.playbackResult!.routeStatus,
        SourceLivePlaybackRouteStatus.preferredSourceNotFound,
      );
      expect(route.reasonCode, route.playbackResult!.reasonCode);
      expect(harness.resolver.requests, isEmpty);

      final session = await harness.pipeline.openLive(
        targets: [target],
        adRemovalPlan: testAdRemovalPlan(
          _episode(
            target.installedPackage.package.packageId,
            episodeId: 'wrong',
          ),
        ),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );
      expect(
        session.status,
        SourceInstalledLivePlaybackPipelineStatus.notOpened,
      );
      expect(
        session.playbackResult!.failureStage,
        SourceLivePlaybackPipelineFailureStage.sessionRequest,
      );
      expect(
        session.playbackResult!.sessionRequestStatus,
        SourceLivePlaybackSessionRequestStatus.requestRejected,
      );
      expect(harness.resolver.requests, isEmpty);

      final open = await harness.pipeline.openLive(
        targets: [target],
        adRemovalPlan: testAdRemovalPlan(target.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
        episodeDuration: const Duration(seconds: -1),
      );
      expect(open.status, SourceInstalledLivePlaybackPipelineStatus.notOpened);
      expect(
        open.playbackResult!.failureStage,
        SourceLivePlaybackPipelineFailureStage.openRequest,
      );
      expect(
        open.playbackResult!.openRequestStatus,
        SourceLivePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
      );
      expect(harness.resolver.requests, isEmpty);
      expect(harness.player.openedSessions, isEmpty);
    },
  );

  test(
    'wraps only unexpected downstream exceptions in a safe failure',
    () async {
      final target = _target(_package('exception.anime'));
      final transport = _QueueTransport([_playableBody(target.episode)]);
      final harness = _harness(
        transport,
        resolverError: StateError('secret resolver detail'),
      );
      addTearDown(harness.close);

      final result = await harness.pipeline.openLive(
        targets: [target],
        adRemovalPlan: testAdRemovalPlan(target.episode),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );

      expect(result.status, SourceInstalledLivePlaybackPipelineStatus.failed);
      expect(result.reasonCode, 'live_playback_failed');
      expect(result.playbackResult, isNull);
      expect(result.toString(), contains('live_playback_failed'));
      expect(result.toString(), isNot(contains('secret resolver detail')));
      expect(result.toString(), isNot(contains('https://example.com')));
    },
  );

  test('preserves stale concurrent results from TASK-052', () async {
    final target = _target(_package('stale.anime'));
    final transport = _TwoCallDeferredTransport();
    final harness = _harness(transport);
    addTearDown(harness.close);

    final first = harness.pipeline.openLive(
      targets: [target],
      adRemovalPlan: testAdRemovalPlan(target.episode),
      sourceEventSequence: 0,
      proxyBudget: testProxyBudget(),
    );
    await transport.firstStarted.future;
    final second = harness.pipeline.openLive(
      targets: [target],
      adRemovalPlan: testAdRemovalPlan(target.episode),
      sourceEventSequence: 0,
      proxyBudget: testProxyBudget(),
    );
    await transport.secondStarted.future;

    transport.completeFirst(
      _successResponse(
        transport.requests[0].uri,
        _playableBody(target.episode),
      ),
    );
    final stale = await first;
    expect(stale.status, SourceInstalledLivePlaybackPipelineStatus.notOpened);
    expect(
      stale.playbackResult!.failureStage,
      SourceLivePlaybackPipelineFailureStage.playableSources,
    );
    expect(stale.playbackResult!.reasonCode, 'stale_playable');
    expect(harness.player.openedSessions, isEmpty);

    transport.completeSecond(
      _successResponse(
        transport.requests[1].uri,
        _playableBody(target.episode),
      ),
    );
    final current = await second;
    expect(current.status, SourceInstalledLivePlaybackPipelineStatus.opened);
    expect(current.session, same(harness.coordinator.currentSession));
    expect(harness.player.openedSessions, hasLength(1));
  });

  test('close delegates to existing source and playback authorities', () async {
    final target = _target(_package('closed.anime'));
    final transport = _DeferredTransport();
    final harness = _harness(transport);
    addTearDown(harness.close);

    final pending = harness.pipeline.openLive(
      targets: [target],
      adRemovalPlan: testAdRemovalPlan(target.episode),
      sourceEventSequence: 0,
      proxyBudget: testProxyBudget(),
    );
    await transport.started.future;
    await harness.pipeline.close();
    transport.complete(
      _successResponse(
        transport.requests.single.uri,
        _playableBody(target.episode),
      ),
    );

    final result = await pending;
    expect(result.status, SourceInstalledLivePlaybackPipelineStatus.notOpened);
    expect(
      result.playbackResult!.failureStage,
      SourceLivePlaybackPipelineFailureStage.playableSources,
    );
    expect(result.playbackResult!.reasonCode, 'live_playable_closed');
    expect(result.session, isNull);
    expect(harness.player.openedSessions, isEmpty);
  });

  test('returns immutable target results and redacted diagnostics', () async {
    final target = _target(_package('redacted.anime'));
    final transport = _QueueTransport([_playableBody(target.episode)]);
    final harness = _harness(transport);
    addTearDown(harness.close);

    final result = await harness.pipeline.openLive(
      targets: [target],
      adRemovalPlan: testAdRemovalPlan(target.episode),
      sourceEventSequence: 0,
      proxyBudget: testProxyBudget(),
    );

    expect(() => result.targetResults.clear(), throwsUnsupportedError);
    expect(result.toString(), contains('redacted.anime'));
    expect(result.toString(), isNot(contains(target.episode.episodeId)));
    expect(result.toString(), isNot(contains('https://example.com')));
    expect(result.toString(), isNot(contains('session-cookie')));
    expect(result.toString(), isNot(contains('secret-token')));
  });

  test('rejects impossible outer result states', () {
    final target = _target(_package('invariant.anime'));
    final factory = SourceLiveOperationPlanFactory(
      wynimeVersion: Version.parse('1.0.0'),
    );
    final planResult = factory.buildPlayableSourcePlan(
      installedPackage: target.installedPackage,
      episode: target.episode,
    );
    final targetResult = SourceInstalledLivePlaybackTargetResult(
      target: target,
      planResult: planResult,
    );

    expect(
      () => SourceInstalledLivePlaybackPipelineResult(
        status: SourceInstalledLivePlaybackPipelineStatus.opened,
        targetResults: [targetResult],
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceInstalledLivePlaybackPipelineResult(
        status: SourceInstalledLivePlaybackPipelineStatus.noUsableSources,
        targetResults: [targetResult],
        reasonCode: 'no_usable_playback_sources',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceInstalledLivePlaybackPipelineResult(
        status: SourceInstalledLivePlaybackPipelineStatus.failed,
        targetResults: const [],
        reasonCode: 'https://unsafe.invalid/?token=secret-token',
      ),
      throwsArgumentError,
    );
  });
}

final class _Harness {
  _Harness({
    required this.pipeline,
    required this.livePipeline,
    required this.coordinator,
    required this.transport,
    required this.resolver,
    required this.proxy,
    required this.player,
  });

  final SourceInstalledLivePlaybackPipeline pipeline;
  final SourceLivePlaybackPipeline livePipeline;
  final PlaybackCoordinator coordinator;
  final SourceHttpTransport transport;
  final _RecordingResolver resolver;
  final _RecordingProxy proxy;
  final _RecordingPlayer player;

  Future<void> close() async {
    livePipeline.playableSourceCoordinator.close();
    await coordinator.close();
  }
}

_Harness _harness(
  SourceHttpTransport transport, {
  SourceLivePlaybackPreparedRequestOpener? preparedOpener,
  Object? resolverError,
}) {
  final version = Version.parse('1.0.0');
  final resolver = _RecordingResolver(error: resolverError);
  final proxy = _RecordingProxy();
  final player = _RecordingPlayer();
  final coordinator = PlaybackCoordinator(
    resolver: resolver,
    proxy: proxy,
    player: player,
  );
  final runtime = SourceLiveHttpPackageRuntime(
    httpExecutor: SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: version,
      ),
      transport: transport,
    ),
    fixtureRuntime: DeclarativeSourcePackageRuntime(wynimeVersion: version),
  );
  final livePipeline = SourceLivePlaybackPipeline(
    playableSourceCoordinator: SourceLivePlayableSourceCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourcePlayableSourceNormalizer(),
    ),
    routeCoordinator: SourceLivePlaybackRouteCoordinator(
      wynimeVersion: version,
      routeCoordinator: const SourcePlaybackRouteCoordinator(
        selector: DeterministicSourcePlaybackRouteSelector(),
      ),
    ),
    sessionRequestCoordinator: SourceLivePlaybackSessionRequestCoordinator(
      wynimeVersion: version,
      builder: const DeterministicSourcePlaybackSessionRequestBuilder(),
    ),
    openRequestCoordinator: const SourceLivePlaybackOpenRequestCoordinator(),
    preparedOpener:
        preparedOpener ??
        PlaybackCoordinatorLivePlaybackPreparedRequestOpener(
          coordinator: coordinator,
        ),
  );
  return _Harness(
    pipeline: SourceInstalledLivePlaybackPipeline(
      planFactory: SourceLiveOperationPlanFactory(wynimeVersion: version),
      playbackPipeline: livePipeline,
      closeDelegate: () async {
        livePipeline.playableSourceCoordinator.close();
        await coordinator.close();
      },
    ),
    livePipeline: livePipeline,
    coordinator: coordinator,
    transport: transport,
    resolver: resolver,
    proxy: proxy,
    player: player,
  );
}

SourcePackageManifest _package(
  String packageId, {
  int schemaVersion = 2,
  bool withPlayable = true,
  VersionConstraint? constraint,
}) {
  return SourcePackageManifest(
    schemaVersion: schemaVersion,
    packageId: packageId,
    displayName: packageId,
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: constraint ?? VersionConstraint.parse('^1.0.0'),
    securityPolicy: source_support.testSourcePolicy(
      domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
    ),
    programs: [_playableProgram()],
    liveOperations: schemaVersion == 2 && withPlayable
        ? [
            SourcePackageLiveOperation(
              kind: SourcePackageLiveOperationKind.playableSource,
              programId: 'playable',
              uriTemplate:
                  'https://example.com/anime/{subjectId}/episodes/{episodeId}/sources',
              mapping: _playableMapping(),
            ),
          ]
        : const [],
  );
}

SourceRuleProgram _playableProgram() => SourceRuleProgram(
  programId: 'playable',
  documentKind: SourceDocumentKind.json,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.jsonPath,
    expression: r'$[*]',
  ),
  fields: [
    for (final name in ['sourceKey', 'label', 'kind', 'mediaUri', 'pageUri'])
      SourceFieldRule(
        name: name,
        valueKind: SourceValueKind.raw,
        required: true,
        selector: SourceSelector(
          kind: SourceSelectorKind.jsonPath,
          expression: r'$.' + name,
        ),
      ),
  ],
  resultLimit: 20,
);

SourcePlayableSourceFieldMapping _playableMapping() =>
    SourcePlayableSourceFieldMapping(
      sourceKeyField: 'sourceKey',
      labelField: 'label',
      kindField: 'kind',
      mediaUriField: 'mediaUri',
      pageUriField: 'pageUri',
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
  SourcePackageManifest package, {
  SourceEpisodeIdentity? episode,
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) => SourceInstalledLiveEpisodeTarget(
  installedPackage: _installed(
    package,
    status: status,
    requiresConsent: requiresConsent,
    requiresReconsent: requiresReconsent,
  ),
  episode: episode ?? _episode(package.packageId),
);

SourceEpisodeIdentity _episode(
  String sourceId, {
  String lineId = 'line-1',
  String subjectId = 'subject-1',
  String episodeId = 'episode-1',
}) => SourceEpisodeIdentity(
  sourceId: sourceId,
  lineId: lineId,
  subjectId: subjectId,
  episodeId: episodeId,
);

String _playableBody(SourceEpisodeIdentity episode) => jsonEncode([
  {
    'sourceKey': 'primary',
    'label': 'Primary HLS',
    'kind': 'hls',
    'mediaUri': 'https://example.com/media/primary.m3u8',
    'pageUri': 'https://example.com/watch/${episode.episodeId}',
  },
]);

SourceHttpTransportResult _successResponse(Uri uri, String body) =>
    SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: uri,
        redirectChain: const [],
        body: body,
      ),
    );

final class _QueueTransport implements SourceHttpTransport {
  _QueueTransport(this.bodies);

  final List<String> bodies;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    if (bodies.isEmpty) {
      return SourceHttpTransportResult(
        status: SourceHttpTransportStatus.failed,
        reasonCode: 'queue_empty',
      );
    }
    return _successResponse(request.uri, bodies.removeAt(0));
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
  final requests = <SourceHttpRequest>[];
  var _calls = 0;

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) {
    requests.add(request);
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
      throw StateError('one-shot target iterable was consumed twice');
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

final class _RecordingResolver implements PlaybackSessionResolver {
  _RecordingResolver({this.error});

  final Object? error;
  final requests = <PlaybackSessionResolutionRequest>[];

  @override
  Future<PlaybackSession> resolve(
    PlaybackSessionResolutionRequest request,
  ) async {
    requests.add(request);
    final failure = error;
    if (failure != null) {
      throw failure;
    }
    return testPlaybackSession(
      episode: request.episode,
      mediaUri: request.candidate.uri,
      pageUri: request.pageUri,
      adRemovalPlan: request.adRemovalPlan,
    );
  }
}

final class _RecordingProxy implements PlaybackProxyService {
  final requests = <PlaybackProxyRequest>[];

  @override
  Future<PlaybackProxyLease> expose(PlaybackProxyRequest request) async {
    requests.add(request);
    return PlaybackProxyLease(
      sessionId: request.session.sessionId,
      playbackUri: Uri.parse('http://127.0.0.1:42001/v1/session/capability'),
      close: () async {},
    );
  }

  @override
  Future<void> close() async {}
}

final class _RecordingPlayer implements PlayerBackend {
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final openedSessions = <PlaybackSession>[];

  @override
  String get backendId => 'task056-test-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task056-test-player');

  @override
  Future<void> open(PlaybackSession session) async {
    openedSessions.add(session);
  }

  @override
  Future<void> play() async {}

  @override
  Future<void> pause() async {}

  @override
  Future<void> seek(Duration position) async {}

  @override
  Future<void> setVolume(double volume) async {}

  @override
  Future<void> setRate(double rate) async {}

  @override
  Future<void> selectAudioTrack(String? trackId) async {}

  @override
  Future<void> selectSubtitleTrack(String? trackId) async {}

  @override
  Future<void> close() async {
    await _events.close();
  }
}

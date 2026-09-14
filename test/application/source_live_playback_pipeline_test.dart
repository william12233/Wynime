import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
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
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';

import '../helpers/playback_test_support.dart';
import '../helpers/source_rule_test_support.dart' as source_support;

void main() {
  final fixture = _fixture();
  final adRemovalPlan = testAdRemovalPlan(fixture.episode);

  test(
    'composes an explicit live HTTP plan through one playback lifecycle',
    () async {
      final transport = _QueueTransport([_success(fixture.request, _body())]);
      final resolver = _RecordingResolver();
      final proxy = _RecordingProxy();
      final player = _RecordingPlayer();
      final proxyBudget = testProxyBudget();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      final preparedOpener = _RecordingPreparedOpener(
        PlaybackCoordinatorLivePlaybackPreparedRequestOpener(
          coordinator: coordinator,
        ),
      );
      addTearDown(coordinator.close);

      final result =
          await _pipeline(
            transport,
            coordinator: coordinator,
            preparedOpener: preparedOpener,
          ).openLive(
            plans: [fixture.plan],
            adRemovalPlan: adRemovalPlan,
            sourceEventSequence: 7,
            proxyBudget: proxyBudget,
            preference: SourcePlaybackRoutePreference(
              packageId: fixture.package.packageId,
              packageVersion: fixture.package.version,
              programId: fixture.plan.requestPlan.programId,
              sourceKey: 'primary',
            ),
            addressFamily: LoopbackAddressFamily.ipv6,
            refreshLeeway: const Duration(seconds: 41),
            maxAutomaticRefreshes: 2,
            episodeDuration: const Duration(minutes: 24),
          );

      expect(result.status, SourceLivePlaybackPipelineStatus.opened);
      expect(result.failureStage, isNull);
      expect(result.session, same(coordinator.currentSession));
      expect(result.session, same(player.openedSessions.single));
      expect(transport.requests, hasLength(1));
      expect(resolver.requests, hasLength(1));
      expect(resolver.requests.single.episode, same(fixture.episode));
      expect(
        resolver.requests.single.candidate.uri,
        Uri.parse('https://example.com/media/primary.m3u8'),
      );
      expect(resolver.requests.single.candidate.sourceEventSequence, 7);
      expect(resolver.requests.single.candidate.headers, isEmpty);
      expect(resolver.requests.single.cookies, isEmpty);
      expect(resolver.requests.single.userAgent, isNull);
      expect(preparedOpener.openResults, hasLength(1));
      final openRequest = preparedOpener.openResults.single.request!;
      expect(openRequest.resolution, same(resolver.requests.single));
      expect(openRequest.proxyBudget, same(proxyBudget));
      expect(openRequest.addressFamily, LoopbackAddressFamily.ipv6);
      expect(openRequest.refreshLeeway, const Duration(seconds: 41));
      expect(openRequest.maxAutomaticRefreshes, 2);
      expect(openRequest.episodeDuration, const Duration(minutes: 24));
      expect(proxy.requests, hasLength(1));
      expect(proxy.requests.single.budget, same(proxyBudget));
      expect(proxy.requests.single.addressFamily, LoopbackAddressFamily.ipv6);
    },
  );

  test(
    'accepts partial playable results and reuses one caller plan snapshot',
    () async {
      final disabledPackage = _package('disabled.anime');
      final disabledPlan = _plan(
        disabledPackage,
        installedPackage: _installed(
          disabledPackage,
          status: SourcePackageStatus.disabled,
        ),
      );
      final transport = _QueueTransport([_success(fixture.request, _body())]);
      final resolver = _RecordingResolver();
      final proxy = _RecordingProxy();
      final player = _RecordingPlayer();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      addTearDown(coordinator.close);

      final oneShotPlans = () sync* {
        yield fixture.plan;
        yield disabledPlan;
      }();
      final result = await _pipeline(transport, coordinator: coordinator)
          .openLive(
            plans: oneShotPlans,
            adRemovalPlan: adRemovalPlan,
            sourceEventSequence: 0,
            proxyBudget: testProxyBudget(),
          );

      expect(result.status, SourceLivePlaybackPipelineStatus.opened);
      expect(result.session, same(coordinator.currentSession));
      expect(
        resolver.requests.single.candidate.uri.path,
        '/media/primary.m3u8',
      );
      expect(transport.requests, hasLength(1));
      expect(player.openedSessions, hasLength(1));
    },
  );

  test(
    'short-circuits each typed stage before downstream playback work',
    () async {
      final transport = _QueueTransport(
        List<SourceHttpTransportResult>.generate(
          4,
          (_) => _success(fixture.request, _body()),
        ),
      );
      final resolver = _RecordingResolver();
      final proxy = _RecordingProxy();
      final player = _RecordingPlayer();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      addTearDown(coordinator.close);
      final pipeline = _pipeline(transport, coordinator: coordinator);

      final noPlans = await pipeline.openLive(
        plans: const [],
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );
      expect(
        noPlans.failureStage,
        SourceLivePlaybackPipelineFailureStage.playableSources,
      );
      expect(
        noPlans.playableStatus,
        SourcePlayableSourceCoordinatorStatus.noSources,
      );

      final preferredSourceMissing = await pipeline.openLive(
        plans: [fixture.plan],
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
        preference: SourcePlaybackRoutePreference(
          packageId: fixture.package.packageId,
          packageVersion: fixture.package.version,
          programId: fixture.plan.requestPlan.programId,
          sourceKey: 'missing',
        ),
      );
      expect(
        preferredSourceMissing.failureStage,
        SourceLivePlaybackPipelineFailureStage.route,
      );
      expect(
        preferredSourceMissing.routeStatus,
        SourceLivePlaybackRouteStatus.preferredSourceNotFound,
      );

      final wrongAdPlan = await pipeline.openLive(
        plans: [fixture.plan],
        adRemovalPlan: testAdRemovalPlan(
          SourceEpisodeIdentity(
            sourceId: fixture.episode.sourceId,
            lineId: fixture.episode.lineId,
            subjectId: fixture.episode.subjectId,
            episodeId: 'episode-2',
          ),
        ),
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
      );
      expect(
        wrongAdPlan.failureStage,
        SourceLivePlaybackPipelineFailureStage.sessionRequest,
      );
      expect(
        wrongAdPlan.sessionRequestStatus,
        SourceLivePlaybackSessionRequestStatus.requestRejected,
      );

      final invalidSequence = await pipeline.openLive(
        plans: [fixture.plan],
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: -1,
        proxyBudget: testProxyBudget(),
      );
      expect(
        invalidSequence.failureStage,
        SourceLivePlaybackPipelineFailureStage.sessionRequest,
      );
      expect(
        invalidSequence.sessionRequestStatus,
        SourceLivePlaybackSessionRequestStatus.requestRejected,
      );

      final invalidOptions = await pipeline.openLive(
        plans: [fixture.plan],
        adRemovalPlan: adRemovalPlan,
        sourceEventSequence: 0,
        proxyBudget: testProxyBudget(),
        episodeDuration: const Duration(seconds: -1),
      );
      expect(
        invalidOptions.failureStage,
        SourceLivePlaybackPipelineFailureStage.openRequest,
      );
      expect(
        invalidOptions.openRequestStatus,
        SourceLivePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
      );

      expect(transport.requests, hasLength(4));
      expect(resolver.requests, isEmpty);
      expect(proxy.requests, isEmpty);
      expect(player.openedSessions, isEmpty);
    },
  );

  test(
    'stops at the prepared opener and preserves its typed rejection',
    () async {
      final transport = _QueueTransport([_success(fixture.request, _body())]);
      final resolver = _RecordingResolver();
      final proxy = _RecordingProxy();
      final player = _RecordingPlayer();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      addTearDown(coordinator.close);
      final preparedOpener = _RejectingPreparedOpener();

      final result =
          await _pipeline(
            transport,
            coordinator: coordinator,
            preparedOpener: preparedOpener,
          ).openLive(
            plans: [fixture.plan],
            adRemovalPlan: adRemovalPlan,
            sourceEventSequence: 0,
            proxyBudget: testProxyBudget(),
          );

      expect(result.status, SourceLivePlaybackPipelineStatus.notOpened);
      expect(
        result.failureStage,
        SourceLivePlaybackPipelineFailureStage.preparedOpen,
      );
      expect(
        result.preparedOpenStatus,
        SourceLivePlaybackPreparedOpenStatus.requestNotReady,
      );
      expect(result.reasonCode, 'prepared_open_rejected');
      expect(preparedOpener.calls, 1);
      expect(resolver.requests, isEmpty);
      expect(proxy.requests, isEmpty);
      expect(player.openedSessions, isEmpty);
    },
  );

  test('preserves the existing stable playback error', () async {
    final coordinator = PlaybackCoordinator(
      resolver: _ThrowingResolver(),
      proxy: _RecordingProxy(),
      player: _RecordingPlayer(),
    );
    addTearDown(coordinator.close);

    expect(
      () =>
          _pipeline(
            _QueueTransport([_success(fixture.request, _body())]),
            coordinator: coordinator,
          ).openLive(
            plans: [fixture.plan],
            adRemovalPlan: adRemovalPlan,
            sourceEventSequence: 0,
            proxyBudget: testProxyBudget(),
          ),
      throwsA(isA<PlaybackOperationException>()),
    );
  });

  test('rejects fabricated pipeline states and redacts diagnostics', () {
    expect(
      () => SourceLivePlaybackPipelineResult(
        status: SourceLivePlaybackPipelineStatus.opened,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPipelineResult(
        status: SourceLivePlaybackPipelineStatus.notOpened,
        failureStage: SourceLivePlaybackPipelineFailureStage.playableSources,
        playableStatus: SourcePlayableSourceCoordinatorStatus.partial,
        reasonCode: 'partial_source_results',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPipelineResult(
        status: SourceLivePlaybackPipelineStatus.notOpened,
        failureStage: SourceLivePlaybackPipelineFailureStage.route,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        reasonCode: 'route_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPipelineResult(
        status: SourceLivePlaybackPipelineStatus.notOpened,
        failureStage: SourceLivePlaybackPipelineFailureStage.preparedOpen,
        preparedOpenStatus: SourceLivePlaybackPreparedOpenStatus.opened,
        reasonCode: 'prepared_opened',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPipelineResult(
        status: SourceLivePlaybackPipelineStatus.notOpened,
        failureStage: SourceLivePlaybackPipelineFailureStage.route,
        routeStatus: SourceLivePlaybackRouteStatus.failed,
        reasonCode: 'https://unsafe.invalid/?token=secret',
      ),
      throwsArgumentError,
    );

    final diagnostic = SourceLivePlaybackPipelineResult(
      status: SourceLivePlaybackPipelineStatus.notOpened,
      failureStage: SourceLivePlaybackPipelineFailureStage.openRequest,
      openRequestStatus:
          SourceLivePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
      reasonCode: 'invalid_episode_duration',
    ).toString();
    expect(diagnostic, contains('failureStage: openRequest'));
    expect(diagnostic, isNot(contains('https://example.com')));
    expect(diagnostic, isNot(contains('session-cookie')));
  });
}

SourceLivePlaybackPipeline _pipeline(
  SourceHttpTransport transport, {
  required PlaybackCoordinator coordinator,
  SourceLivePlaybackPreparedRequestOpener? preparedOpener,
}) {
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
  return SourceLivePlaybackPipeline(
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
}

final class _Fixture {
  _Fixture({
    required this.package,
    required this.episode,
    required this.request,
    required this.plan,
  });

  final SourcePackageManifest package;
  final SourceEpisodeIdentity episode;
  final SourceHttpRequest request;
  final SourceLivePlayableSourcePlan plan;
}

_Fixture _fixture() {
  final policy = source_support.testSourcePolicy(
    domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
  );
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'demo.source',
    displayName: 'Demo Source',
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('>=1.0.0 <2.0.0'),
    securityPolicy: policy,
    programs: [
      SourceRuleProgram(
        programId: 'playback',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: '.source',
        ),
        fields: [
          SourceFieldRule(
            name: 'key',
            valueKind: SourceValueKind.text,
            required: true,
            selector: SourceSelector(
              kind: SourceSelectorKind.css,
              expression: '.key',
            ),
          ),
          SourceFieldRule(
            name: 'label',
            valueKind: SourceValueKind.text,
            required: true,
            selector: SourceSelector(
              kind: SourceSelectorKind.css,
              expression: '.label',
            ),
          ),
          SourceFieldRule(
            name: 'kind',
            valueKind: SourceValueKind.text,
            required: true,
            selector: SourceSelector(
              kind: SourceSelectorKind.css,
              expression: '.kind',
            ),
          ),
          SourceFieldRule(
            name: 'media',
            valueKind: SourceValueKind.text,
            required: true,
            selector: SourceSelector(
              kind: SourceSelectorKind.css,
              expression: '.media',
            ),
          ),
          SourceFieldRule(
            name: 'page',
            valueKind: SourceValueKind.text,
            required: true,
            selector: SourceSelector(
              kind: SourceSelectorKind.css,
              expression: '.page',
            ),
          ),
        ],
        resultLimit: 10,
      ),
    ],
  );
  final installed = _installed(package);
  final episode = _episode(package.packageId);
  final request = SourceHttpRequest(
    uri: Uri.parse('https://example.com/playback'),
    securityPolicy: policy,
    headers: const {'accept': 'text/html'},
    timeout: const Duration(seconds: 2),
  );
  return _Fixture(
    package: package,
    episode: episode,
    request: request,
    plan: SourceLivePlayableSourcePlan(
      requestPlan: SourceLiveHttpRequestPlan(
        installedPackage: installed,
        programId: 'playback',
        request: request,
      ),
      episode: episode,
      mapping: _mapping(),
    ),
  );
}

SourceLivePlayableSourcePlan _plan(
  SourcePackageManifest package, {
  InstalledSourcePackage? installedPackage,
}) => SourceLivePlayableSourcePlan(
  requestPlan: SourceLiveHttpRequestPlan(
    installedPackage: installedPackage ?? _installed(package),
    programId: 'playback',
    request: SourceHttpRequest(
      uri: Uri.parse('https://example.com/playback'),
      securityPolicy: package.securityPolicy,
      headers: const {'accept': 'text/html'},
      timeout: const Duration(seconds: 2),
    ),
  ),
  episode: _episode(package.packageId),
  mapping: _mapping(),
);

SourcePlayableSourceFieldMapping _mapping() => SourcePlayableSourceFieldMapping(
  sourceKeyField: 'key',
  labelField: 'label',
  kindField: 'kind',
  mediaUriField: 'media',
  pageUriField: 'page',
);

SourceEpisodeIdentity _episode(String sourceId) => SourceEpisodeIdentity(
  sourceId: sourceId,
  lineId: 'line-1',
  subjectId: 'subject-1',
  episodeId: 'episode-1',
);

SourcePackageManifest _package(String packageId) => SourcePackageManifest(
  schemaVersion: 1,
  packageId: packageId,
  displayName: packageId,
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
  securityPolicy: source_support.testSourcePolicy(
    domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
  ),
  programs: [
    SourceRuleProgram(
      programId: 'playback',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.source',
      ),
      fields: [
        SourceFieldRule(
          name: 'key',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.key',
          ),
        ),
        SourceFieldRule(
          name: 'label',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.label',
          ),
        ),
        SourceFieldRule(
          name: 'kind',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.kind',
          ),
        ),
        SourceFieldRule(
          name: 'media',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.media',
          ),
        ),
        SourceFieldRule(
          name: 'page',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.page',
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

String _body() => '''
<article class="source">
  <span class="key">primary</span>
  <span class="label">Primary HLS</span>
  <span class="kind">hls</span>
  <span class="media">https://example.com/media/primary.m3u8</span>
  <span class="page">https://example.com/watch/episode-1</span>
</article>
''';

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

final class _RecordingResolver implements PlaybackSessionResolver {
  final List<PlaybackSessionResolutionRequest> requests = [];

  @override
  Future<PlaybackSession> resolve(
    PlaybackSessionResolutionRequest request,
  ) async {
    requests.add(request);
    return testPlaybackSession(
      episode: request.episode,
      mediaUri: request.candidate.uri,
      pageUri: request.pageUri,
      adRemovalPlan: request.adRemovalPlan,
    );
  }
}

final class _ThrowingResolver implements PlaybackSessionResolver {
  @override
  Future<PlaybackSession> resolve(
    PlaybackSessionResolutionRequest request,
  ) async {
    throw StateError('resolver failed');
  }
}

final class _RecordingProxy implements PlaybackProxyService {
  final List<PlaybackProxyRequest> requests = [];

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
  final List<PlaybackSession> openedSessions = [];

  @override
  String get backendId => 'task052-test-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task052-test-player');

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

final class _RecordingPreparedOpener
    implements SourceLivePlaybackPreparedRequestOpener {
  _RecordingPreparedOpener(this._delegate);

  final SourceLivePlaybackPreparedRequestOpener _delegate;
  final List<SourceLivePlaybackOpenRequestCoordinatorResult> openResults = [];

  @override
  Future<SourceLivePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLivePlaybackOpenRequestCoordinatorResult openResult,
  }) {
    openResults.add(openResult);
    return _delegate.openPreparedRequest(openResult: openResult);
  }
}

final class _RejectingPreparedOpener
    implements SourceLivePlaybackPreparedRequestOpener {
  var calls = 0;

  @override
  Future<SourceLivePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLivePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    calls++;
    return SourceLivePlaybackPreparedOpenResult(
      status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
      openRequestStatus: SourceLivePlaybackOpenRequestCoordinatorStatus.failed,
      sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
      routeStatus: SourceLivePlaybackRouteStatus.selected,
      requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
      reasonCode: 'prepared_open_rejected',
    );
  }
}

import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playback_pipeline.dart';
import 'package:wynime/src/application/source_live_capture_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_capture_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playback_session_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  final fixture = _fixture();
  final adRemovalPlan = _adRemovalPlan(fixture.episode);

  test(
    'composes accepted live capture through one playback lifecycle',
    () async {
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
        PlaybackCoordinatorLiveCapturePreparedRequestOpener(
          coordinator: coordinator,
        ),
      );
      addTearDown(coordinator.close);

      final result =
          await _pipeline(
            coordinator: coordinator,
            preparedOpener: preparedOpener,
          ).openLive(
            plan: fixture.plan,
            adRemovalPlan: adRemovalPlan,
            proxyBudget: proxyBudget,
            preferredSourceKey: 'primary',
            addressFamily: LoopbackAddressFamily.ipv6,
            refreshLeeway: const Duration(seconds: 41),
            maxAutomaticRefreshes: 2,
            episodeDuration: const Duration(minutes: 24),
          );

      expect(result.status, SourceLiveCapturePlaybackPipelineStatus.opened);
      expect(result.failureStage, isNull);
      expect(result.session, same(coordinator.currentSession));
      expect(result.session, same(player.openedSessions.single));
      expect(resolver.requests, hasLength(1));
      expect(resolver.requests.single.episode, same(fixture.episode));
      expect(
        resolver.requests.single.candidate,
        same(fixture.primaryCandidate),
      );
      expect(
        resolver.requests.single.candidate.headers['x-capture'],
        'primary',
      );
      expect(resolver.requests.single.candidate.sourceEventSequence, 10);
      expect(resolver.requests.single.cookies, hasLength(1));
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
    'short-circuits typed failures before downstream playback work',
    () async {
      final resolver = _RecordingResolver();
      final proxy = _RecordingProxy();
      final player = _RecordingPlayer();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      addTearDown(coordinator.close);
      final pipeline = _pipeline(coordinator: coordinator);

      final disabled = await pipeline.openLive(
        plan: _planWith(
          fixture.plan,
          installedPackage: fixture.installedPackage.copyWith(
            status: SourcePackageStatus.disabled,
          ),
        ),
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
      );
      expect(
        disabled.failureStage,
        SourceLiveCapturePlaybackPipelineFailureStage.playableSources,
      );
      expect(
        disabled.playableStatus,
        SourceLiveCapturePlayableSourceStatus.disabled,
      );

      final captureFailed = await pipeline.openLive(
        plan: _planWith(
          fixture.plan,
          captureResult: SourceLiveCaptureResult(
            packageId: fixture.package.packageId,
            packageVersion: fixture.package.version,
            programId: fixture.plan.programId,
            status: SourceLiveCaptureStatus.failed,
            reasonCode: 'capture_failed',
          ),
        ),
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
      );
      expect(
        captureFailed.failureStage,
        SourceLiveCapturePlaybackPipelineFailureStage.playableSources,
      );
      expect(
        captureFailed.playableStatus,
        SourceLiveCapturePlayableSourceStatus.failed,
      );

      final preferredSourceMissing = await pipeline.openLive(
        plan: fixture.plan,
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
        preferredSourceKey: 'missing',
      );
      expect(
        preferredSourceMissing.failureStage,
        SourceLiveCapturePlaybackPipelineFailureStage.route,
      );
      expect(
        preferredSourceMissing.routeStatus,
        SourceLiveCapturePlaybackRouteStatus.preferredSourceNotFound,
      );

      final wrongAdPlan = await pipeline.openLive(
        plan: fixture.plan,
        adRemovalPlan: _adRemovalPlan(_episode(episodeId: 'episode-2')),
        proxyBudget: testProxyBudget(),
      );
      expect(
        wrongAdPlan.failureStage,
        SourceLiveCapturePlaybackPipelineFailureStage.sessionRequest,
      );
      expect(
        wrongAdPlan.sessionRequestStatus,
        SourceLiveCapturePlaybackSessionRequestStatus.failed,
      );

      final invalidOptions = await pipeline.openLive(
        plan: fixture.plan,
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
        episodeDuration: const Duration(seconds: -1),
      );
      expect(
        invalidOptions.failureStage,
        SourceLiveCapturePlaybackPipelineFailureStage.openRequest,
      );
      expect(
        invalidOptions.openRequestStatus,
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .invalidEpisodeDuration,
      );

      expect(resolver.requests, isEmpty);
      expect(proxy.requests, isEmpty);
      expect(player.openedSessions, isEmpty);
    },
  );

  test(
    'stops at the prepared opener and preserves its typed rejection',
    () async {
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
            coordinator: coordinator,
            preparedOpener: preparedOpener,
          ).openLive(
            plan: fixture.plan,
            adRemovalPlan: adRemovalPlan,
            proxyBudget: testProxyBudget(),
          );

      expect(result.status, SourceLiveCapturePlaybackPipelineStatus.notOpened);
      expect(
        result.failureStage,
        SourceLiveCapturePlaybackPipelineFailureStage.preparedOpen,
      );
      expect(
        result.preparedOpenStatus,
        SourceLiveCapturePlaybackPreparedOpenStatus.requestNotReady,
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
      () => _pipeline(coordinator: coordinator).openLive(
        plan: fixture.plan,
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
      ),
      throwsA(isA<PlaybackOperationException>()),
    );
  });

  test('rejects fabricated pipeline states and redacts diagnostics', () {
    expect(
      () => SourceLiveCapturePlaybackPipelineResult(
        status: SourceLiveCapturePlaybackPipelineStatus.opened,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackPipelineResult(
        status: SourceLiveCapturePlaybackPipelineStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackPipelineFailureStage.route,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
        reasonCode: 'route_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackPipelineResult(
        status: SourceLiveCapturePlaybackPipelineStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackPipelineFailureStage.openRequest,
        openRequestStatus:
            SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready,
        reasonCode: 'live_open_request_ready',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackPipelineResult(
        status: SourceLiveCapturePlaybackPipelineStatus.notOpened,
        failureStage:
            SourceLiveCapturePlaybackPipelineFailureStage.preparedOpen,
        preparedOpenStatus: SourceLiveCapturePlaybackPreparedOpenStatus.opened,
        reasonCode: 'prepared_opened',
      ),
      throwsArgumentError,
    );

    expect(
      () => SourceLiveCapturePlaybackPipelineResult(
        status: SourceLiveCapturePlaybackPipelineStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackPipelineFailureStage.route,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.failed,
        reasonCode: 'https://unsafe.invalid/?token=secret',
      ),
      throwsArgumentError,
    );

    final diagnostic = SourceLiveCapturePlaybackPipelineResult(
      status: SourceLiveCapturePlaybackPipelineStatus.notOpened,
      failureStage: SourceLiveCapturePlaybackPipelineFailureStage.openRequest,
      openRequestStatus: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
          .invalidEpisodeDuration,
      reasonCode: 'invalid_episode_duration',
    ).toString();
    expect(diagnostic, contains('failureStage: openRequest'));
    expect(diagnostic, isNot(contains('media.example.com')));
    expect(diagnostic, isNot(contains('captured-value')));
  });
}

SourceLiveCapturePlaybackPipeline _pipeline({
  required PlaybackCoordinator coordinator,
  SourceLiveCapturePlaybackPreparedRequestOpener? preparedOpener,
}) => SourceLiveCapturePlaybackPipeline(
  playableSourceCoordinator: SourceLiveCapturePlayableSourceCoordinator(
    wynimeVersion: Version.parse('1.0.0'),
  ),
  routeCoordinator: SourceLiveCapturePlaybackRouteCoordinator(
    wynimeVersion: Version.parse('1.0.0'),
  ),
  sessionRequestCoordinator: SourceLiveCapturePlaybackSessionRequestCoordinator(
    wynimeVersion: Version.parse('1.0.0'),
  ),
  openRequestCoordinator:
      const SourceLiveCapturePlaybackOpenRequestCoordinator(),
  preparedOpener:
      preparedOpener ??
      PlaybackCoordinatorLiveCapturePreparedRequestOpener(
        coordinator: coordinator,
      ),
);

final class _Fixture {
  _Fixture({
    required this.package,
    required this.installedPackage,
    required this.episode,
    required this.primaryCandidate,
    required this.plan,
  });

  final SourcePackageManifest package;
  final InstalledSourcePackage installedPackage;
  final SourceEpisodeIdentity episode;
  final WebMediaCandidate primaryCandidate;
  final SourceLiveCapturePlayableSourcePlan plan;
}

_Fixture _fixture() {
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'demo.source',
    displayName: 'Demo Source',
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('>=1.0.0 <2.0.0'),
    securityPolicy: _securityPolicy(),
    programs: [
      SourceRuleProgram(
        programId: 'live',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: 'body',
        ),
        fields: [
          SourceFieldRule(name: 'value', valueKind: SourceValueKind.text),
        ],
        resultLimit: 1,
      ),
    ],
  );
  final installedPackage = InstalledSourcePackage(
    package: package,
    status: SourcePackageStatus.enabled,
    requiresConsent: false,
    requiresReconsent: false,
  );
  final episode = _episode();
  final pageUri = Uri.parse('https://page.example.com/watch/episode-1');
  final primaryUri = Uri.parse('https://media.example.com/video/primary.mp4');
  final primaryCandidate = WebMediaCandidate(
    kind: WebCandidateKind.video,
    uri: primaryUri.replace(fragment: ''),
    headers: const {'x-capture': 'primary'},
    sourceEventSequence: 10,
  );
  final captureRequest = SourceLiveCaptureRequest(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'live',
    webCaptureRequest: WebCaptureRequest(
      initialUri: pageUri,
      securityPolicy: package.securityPolicy,
      budget: WebCaptureBudget(
        maxEvents: 100,
        maxCandidates: 10,
        maxHeaderBytes: 4096,
        maxCookieBytes: 4096,
      ),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.desktop,
        value: 'Mozilla/5.0 Wynime Test Browser',
      ),
      captureMediaRequests: true,
    ),
  );
  final captureResult = SourceLiveCaptureResult(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'live',
    status: SourceLiveCaptureStatus.captured,
    snapshot: WebCaptureSnapshot(
      events: [
        WebCaptureEvent(
          sequence: 10,
          kind: WebRequestKind.resource,
          uri: primaryUri,
          headers: const {'x-capture': 'primary'},
        ),
      ],
      candidates: [primaryCandidate],
      cookies: [
        WebCaptureCookie(
          name: 'session',
          value: 'captured-value',
          domain: 'example.com',
        ),
      ],
      stopReason: WebCaptureStopReason.completed,
      finalUri: pageUri,
    ),
  );
  final plan = SourceLiveCapturePlayableSourcePlan(
    installedPackage: installedPackage,
    programId: 'live',
    episode: episode,
    captureRequest: captureRequest,
    captureResult: captureResult,
    mappings: [
      SourceLiveCapturePlayableSourceMapping(
        candidateIndex: 0,
        sourceKey: 'primary',
        label: 'Primary',
      ),
    ],
  );
  return _Fixture(
    package: package,
    installedPackage: installedPackage,
    episode: episode,
    primaryCandidate: primaryCandidate,
    plan: plan,
  );
}

SourceLiveCapturePlayableSourcePlan _planWith(
  SourceLiveCapturePlayableSourcePlan plan, {
  InstalledSourcePackage? installedPackage,
  SourceLiveCaptureResult? captureResult,
}) => SourceLiveCapturePlayableSourcePlan(
  installedPackage: installedPackage ?? plan.installedPackage,
  programId: plan.programId,
  episode: plan.episode,
  captureRequest: plan.captureRequest,
  captureResult: captureResult ?? plan.captureResult,
  mappings: plan.mappings,
);

SourceEpisodeIdentity _episode({String episodeId = 'episode-1'}) =>
    SourceEpisodeIdentity(
      sourceId: 'demo.source',
      lineId: 'line-1',
      subjectId: 'subject-1',
      episodeId: episodeId,
    );

AdRemovalPlan _adRemovalPlan(SourceEpisodeIdentity episode) => AdRemovalPlan(
  key: AdRemovalPlanKey(
    episode: episode,
    manifestFingerprint: testAdRemovalPlan(episode).key.manifestFingerprint,
  ),
);

SourceSecurityPolicy _securityPolicy() => SourceSecurityPolicy(
  allowedDomains: [
    SourceDomainRule(host: 'example.com', includeSubdomains: true),
  ],
  permissions: const {
    SourcePermission.network,
    SourcePermission.cookies,
    SourcePermission.webView,
    SourcePermission.mediaRequestInspection,
    SourcePermission.desktopUserAgent,
  },
  budget: SourceResourceBudget(
    maxDocumentBytes: 1024,
    maxRecords: 10,
    maxSelectorMatches: 10,
    maxEvaluationSteps: 100,
    maxRegexPatternChars: 32,
    maxRegexInputChars: 128,
    maxRedirects: 2,
  ),
);

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
  String get backendId => 'task040-test-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task040-test-player');

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

final class _RejectingPreparedOpener
    implements SourceLiveCapturePlaybackPreparedRequestOpener {
  int calls = 0;

  @override
  Future<SourceLiveCapturePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLiveCapturePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    calls++;
    return SourceLiveCapturePlaybackPreparedOpenResult(
      status: SourceLiveCapturePlaybackPreparedOpenStatus.requestNotReady,
      openRequestStatus:
          SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.failed,
      sessionStatus: SourceLiveCapturePlaybackSessionRequestStatus.ready,
      routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
      reasonCode: 'prepared_open_rejected',
    );
  }
}

final class _RecordingPreparedOpener
    implements SourceLiveCapturePlaybackPreparedRequestOpener {
  _RecordingPreparedOpener(this._delegate);

  final SourceLiveCapturePlaybackPreparedRequestOpener _delegate;
  final List<SourceLiveCapturePlaybackOpenRequestCoordinatorResult>
  openResults = [];

  @override
  Future<SourceLiveCapturePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLiveCapturePlaybackOpenRequestCoordinatorResult openResult,
  }) {
    openResults.add(openResult);
    return _delegate.openPreparedRequest(openResult: openResult);
  }
}

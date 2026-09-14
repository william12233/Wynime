import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playable_source_plan_coordinator.dart';
import 'package:wynime/src/application/source_live_capture_playback_entry_point.dart';
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
import 'package:wynime/src/domain/models/source_live_capture_package_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
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
    'composes package admission and capture into one playback lifecycle',
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
          await _entry(
            coordinator: coordinator,
            preparedOpener: preparedOpener,
          ).openCapturedLive(
            packagePlan: fixture.packagePlan,
            admission: fixture.admission,
            captureResult: fixture.captureResult,
            episode: fixture.episode,
            mappings: fixture.mappings,
            adRemovalPlan: adRemovalPlan,
            proxyBudget: proxyBudget,
            preferredSourceKey: 'primary',
            addressFamily: LoopbackAddressFamily.ipv6,
            refreshLeeway: const Duration(seconds: 41),
            maxAutomaticRefreshes: 2,
            episodeDuration: const Duration(minutes: 24),
          );

      expect(result.status, SourceLiveCapturePlaybackEntryStatus.opened);
      expect(result.failureStage, isNull);
      expect(result.planResult, isNull);
      expect(result.pipelineResult, isNull);
      expect(result.session, same(coordinator.currentSession));
      expect(result.session, same(player.openedSessions.single));
      expect(resolver.requests, hasLength(1));
      expect(resolver.requests.single.episode, same(fixture.episode));
      expect(
        resolver.requests.single.candidate,
        same(fixture.primaryCandidate),
      );
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
    'short-circuits admission and plan failures before pipeline side effects',
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
      final entry = _entry(coordinator: coordinator);

      final admissionRejected = await entry.openCapturedLive(
        packagePlan: fixture.packagePlan,
        admission: SourceLiveCapturePackageResult(
          packageId: fixture.package.packageId,
          packageVersion: fixture.package.version,
          programId: fixture.packagePlan.programId,
          status: SourceLiveCapturePackageStatus.consentRequired,
          reasonCode: 'consent_required',
        ),
        captureResult: fixture.captureResult,
        episode: fixture.episode,
        mappings: fixture.mappings,
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
      );
      expect(
        admissionRejected.status,
        SourceLiveCapturePlaybackEntryStatus.notOpened,
      );
      expect(
        admissionRejected.failureStage,
        SourceLiveCapturePlaybackEntryFailureStage.plan,
      );
      expect(
        admissionRejected.planResult!.failureStage,
        SourceLiveCapturePlayableSourcePlanFailureStage.packageAdmission,
      );
      expect(
        admissionRejected.planResult!.packageStatus,
        SourceLiveCapturePackageStatus.consentRequired,
      );
      expect(admissionRejected.pipelineResult, isNull);

      final invalidMapping = await entry.openCapturedLive(
        packagePlan: fixture.packagePlan,
        admission: fixture.admission,
        captureResult: fixture.captureResult,
        episode: fixture.episode,
        mappings: const [],
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
      );
      expect(
        invalidMapping.failureStage,
        SourceLiveCapturePlaybackEntryFailureStage.plan,
      );
      expect(
        invalidMapping.planResult!.reasonCode,
        'candidate_mapping_invalid',
      );
      expect(invalidMapping.pipelineResult, isNull);

      expect(resolver.requests, isEmpty);
      expect(proxy.requests, isEmpty);
      expect(player.openedSessions, isEmpty);
    },
  );

  test(
    'preserves pipeline rejection and forwards no ready plan in the result',
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

      final result = await _entry(coordinator: coordinator).openCapturedLive(
        packagePlan: fixture.packagePlan,
        admission: fixture.admission,
        captureResult: fixture.captureResult,
        episode: fixture.episode,
        mappings: fixture.mappings,
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
        episodeDuration: const Duration(seconds: -1),
      );

      expect(result.status, SourceLiveCapturePlaybackEntryStatus.notOpened);
      expect(
        result.failureStage,
        SourceLiveCapturePlaybackEntryFailureStage.pipeline,
      );
      expect(result.session, isNull);
      expect(result.planResult, isNull);
      expect(
        result.pipelineResult!.failureStage,
        SourceLiveCapturePlaybackPipelineFailureStage.openRequest,
      );
      expect(
        result.pipelineResult!.openRequestStatus,
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .invalidEpisodeDuration,
      );
      expect(resolver.requests, isEmpty);
      expect(proxy.requests, isEmpty);
      expect(player.openedSessions, isEmpty);
    },
  );

  test('propagates the existing stable playback error boundary', () async {
    final coordinator = PlaybackCoordinator(
      resolver: _ThrowingResolver(),
      proxy: _RecordingProxy(),
      player: _RecordingPlayer(),
    );
    addTearDown(coordinator.close);

    expect(
      () => _entry(coordinator: coordinator).openCapturedLive(
        packagePlan: fixture.packagePlan,
        admission: fixture.admission,
        captureResult: fixture.captureResult,
        episode: fixture.episode,
        mappings: fixture.mappings,
        adRemovalPlan: adRemovalPlan,
        proxyBudget: testProxyBudget(),
      ),
      throwsA(isA<PlaybackOperationException>()),
    );
  });

  test('rejects fabricated entry states and redacts diagnostics', () {
    expect(
      () => SourceLiveCapturePlaybackEntryResult(
        status: SourceLiveCapturePlaybackEntryStatus.opened,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackEntryResult(
        status: SourceLiveCapturePlaybackEntryStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackEntryFailureStage.plan,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackEntryResult(
        status: SourceLiveCapturePlaybackEntryStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackEntryFailureStage.pipeline,
        planResult: SourceLiveCapturePlayableSourcePlanResult(
          status: SourceLiveCapturePlayableSourcePlanStatus.notReady,
          failureStage: SourceLiveCapturePlayableSourcePlanFailureStage.plan,
          reasonCode: 'candidate_mapping_invalid',
        ),
      ),
      throwsArgumentError,
    );

    final diagnostic = SourceLiveCapturePlaybackEntryResult(
      status: SourceLiveCapturePlaybackEntryStatus.notOpened,
      failureStage: SourceLiveCapturePlaybackEntryFailureStage.pipeline,
      pipelineResult: SourceLiveCapturePlaybackPipelineResult(
        status: SourceLiveCapturePlaybackPipelineStatus.notOpened,
        failureStage: SourceLiveCapturePlaybackPipelineFailureStage.openRequest,
        openRequestStatus: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .invalidEpisodeDuration,
        reasonCode: 'invalid_episode_duration',
      ),
    ).toString();
    expect(diagnostic, contains('failureStage: pipeline'));
    expect(diagnostic, contains('invalid_episode_duration'));
    expect(diagnostic, isNot(contains('media.example.com')));
    expect(diagnostic, isNot(contains('captured-value')));
  });
}

SourceLiveCapturePlaybackEntryPoint _entry({
  required PlaybackCoordinator coordinator,
  SourceLiveCapturePlaybackPreparedRequestOpener? preparedOpener,
}) => SourceLiveCapturePlaybackEntryPoint(
  planCoordinator: const SourceLiveCapturePlayableSourcePlanCoordinator(),
  playbackPipeline: SourceLiveCapturePlaybackPipeline(
    playableSourceCoordinator: SourceLiveCapturePlayableSourceCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
    ),
    routeCoordinator: SourceLiveCapturePlaybackRouteCoordinator(
      wynimeVersion: Version.parse('1.0.0'),
    ),
    sessionRequestCoordinator:
        SourceLiveCapturePlaybackSessionRequestCoordinator(
          wynimeVersion: Version.parse('1.0.0'),
        ),
    openRequestCoordinator:
        const SourceLiveCapturePlaybackOpenRequestCoordinator(),
    preparedOpener:
        preparedOpener ??
        PlaybackCoordinatorLiveCapturePreparedRequestOpener(
          coordinator: coordinator,
        ),
  ),
);

final class _Fixture {
  _Fixture({
    required this.package,
    required this.packagePlan,
    required this.admission,
    required this.captureResult,
    required this.episode,
    required this.primaryCandidate,
    required this.mappings,
  });

  final SourcePackageManifest package;
  final SourceLiveCapturePackagePlan packagePlan;
  final SourceLiveCapturePackageResult admission;
  final SourceLiveCaptureResult captureResult;
  final SourceEpisodeIdentity episode;
  final WebMediaCandidate primaryCandidate;
  final List<SourceLiveCapturePlayableSourceMapping> mappings;
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
  final webCaptureRequest = WebCaptureRequest(
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
  );
  final captureRequest = SourceLiveCaptureRequest(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'live',
    webCaptureRequest: webCaptureRequest,
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
  final packagePlan = SourceLiveCapturePackagePlan(
    installedPackage: installedPackage,
    programId: 'live',
    webCaptureRequest: webCaptureRequest,
  );
  final admission = SourceLiveCapturePackageResult(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'live',
    status: SourceLiveCapturePackageStatus.ready,
    request: captureRequest,
  );
  return _Fixture(
    package: package,
    packagePlan: packagePlan,
    admission: admission,
    captureResult: captureResult,
    episode: episode,
    primaryCandidate: primaryCandidate,
    mappings: [
      SourceLiveCapturePlayableSourceMapping(
        candidateIndex: 0,
        sourceKey: 'primary',
        label: 'Primary',
      ),
    ],
  );
}

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
  String get backendId => 'task042-test-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task042-test-player');

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

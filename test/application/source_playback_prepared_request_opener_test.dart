import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/application/source_playback_session_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

import '../helpers/playback_test_support.dart';
import '../helpers/source_rule_test_support.dart' as source_support;

void main() {
  final proxyBudget = _proxyBudget();
  final openResult = _readyOpenResult(proxyBudget);

  test('passes one ready open request to PlaybackCoordinator', () async {
    final resolver = _RecordingResolver(
      testPlaybackSession(
        episode: _episode(),
        mediaUri: Uri.parse('https://cdn.example.com/video/master.m3u8'),
        pageUri: Uri.parse('https://example.com/watch/episode-1'),
      ),
    );
    final proxy = _RecordingProxy();
    final player = _RecordingPlayer();
    final coordinator = PlaybackCoordinator(
      resolver: resolver,
      proxy: proxy,
      player: player,
    );
    addTearDown(coordinator.close);

    final result = await PlaybackCoordinatorPreparedRequestOpener(
      coordinator: coordinator,
    ).openPreparedRequest(openResult: openResult);

    expect(result.status, SourcePlaybackPreparedOpenStatus.opened);
    expect(result.session, same(coordinator.currentSession));
    expect(result.session, same(player.openedSessions.single));
    expect(resolver.resolveCount, 1);
    expect(proxy.requests.single.budget, same(proxyBudget));
    expect(proxy.requests.single.addressFamily, LoopbackAddressFamily.ipv6);
    expect(resolver.lastRequest, same(openResult.request!.resolution));
  });

  test('short-circuits every non-ready open-request result', () async {
    final resolver = _RecordingResolver(testPlaybackSession());
    final proxy = _RecordingProxy();
    final player = _RecordingPlayer();
    final coordinator = PlaybackCoordinator(
      resolver: resolver,
      proxy: proxy,
      player: player,
    );
    addTearDown(coordinator.close);

    final results = [
      SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected,
        reasonCode: 'route_not_selected',
      ),
      SourcePlaybackOpenRequestCoordinatorResult(
        status: SourcePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'invalid_refresh_leeway',
      ),
      SourcePlaybackOpenRequestCoordinatorResult(
        status: SourcePlaybackOpenRequestCoordinatorStatus
            .invalidAutomaticRefreshes,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'invalid_automatic_refreshes',
      ),
      SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'invalid_episode_duration',
      ),
      SourcePlaybackOpenRequestCoordinatorResult(
        status: SourcePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'open_request_build_failed',
      ),
    ];

    for (final input in results) {
      final result = await PlaybackCoordinatorPreparedRequestOpener(
        coordinator: coordinator,
      ).openPreparedRequest(openResult: input);
      expect(result.status, SourcePlaybackPreparedOpenStatus.requestNotReady);
      expect(result.session, isNull);
      expect(result.openRequestStatus, input.status);
      expect(result.sessionStatus, input.sessionStatus);
      expect(result.sessionRequestStatus, input.requestStatus);
      expect(result.reasonCode, input.reasonCode);
    }
    expect(resolver.resolveCount, 0);
    expect(proxy.requests, isEmpty);
    expect(player.openedSessions, isEmpty);
  });

  test('preserves downstream stable playback errors', () async {
    final coordinator = PlaybackCoordinator(
      resolver: _ThrowingResolver(),
      proxy: _RecordingProxy(),
      player: _RecordingPlayer(),
    );
    addTearDown(coordinator.close);

    expect(
      () => PlaybackCoordinatorPreparedRequestOpener(
        coordinator: coordinator,
      ).openPreparedRequest(openResult: openResult),
      throwsA(isA<PlaybackOperationException>()),
    );
  });

  test('keeps prepared-open results bounded and redacted', () {
    expect(
      () => SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.opened,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: SourcePlaybackOpenRequestCoordinatorStatus.ready,
        reasonCode: 'open_request_not_ready',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        reasonCode: 'route_not_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected,
        sessionRequestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'route_not_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
        reasonCode: 'session_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
        sessionRequestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'session_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        sessionRequestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'https://unsafe.invalid/video',
      ),
      throwsArgumentError,
    );
    final result = SourcePlaybackPreparedOpenResult(
      status: SourcePlaybackPreparedOpenStatus.requestNotReady,
      openRequestStatus:
          SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
      sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
      sessionRequestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
      reasonCode: 'invalid_episode_duration',
    );
    expect(result.toString(), contains('status: requestNotReady'));
    expect(result.toString(), contains('hasSession: false'));
    expect(result.toString(), isNot(contains('example.anime')));
    expect(result.toString(), isNot(contains('master.m3u8')));
  });
}

SourcePlaybackOpenRequestCoordinatorResult _readyOpenResult(
  PlaybackProxyBudget proxyBudget,
) {
  final episode = _episode();
  final package = _package();
  final route = SourcePlaybackRoute(
    packageId: package.packageId,
    packageVersion: package.version,
    programId: 'playback',
    source: SourcePlayableSource(
      episode: episode,
      sourceKey: 'primary',
      label: 'Primary',
      kind: WebCandidateKind.hls,
      mediaUri: Uri.parse('https://cdn.example.com/video/master.m3u8'),
      pageUri: Uri.parse('https://example.com/watch/episode-1'),
    ),
  );
  final sessionResult =
      SourcePlaybackSessionRequestCoordinator(
        builder: const DeterministicSourcePlaybackSessionRequestBuilder(),
      ).buildRequest(
        routeResult: SourcePlaybackRouteCoordinatorResult(
          status: SourcePlaybackRouteCoordinatorStatus.selected,
          route: route,
          selectionResults: [
            SourcePlaybackRouteSelectionResult(
              status: SourcePlaybackRouteSelectionStatus.selected,
              route: route,
            ),
          ],
        ),
        package: package,
        adRemovalPlan: _adPlan(episode),
        sourceEventSequence: 0,
      );
  return const SourcePlaybackOpenRequestCoordinator().buildOpenRequest(
    sessionResult: sessionResult,
    proxyBudget: proxyBudget,
    addressFamily: LoopbackAddressFamily.ipv6,
  );
}

SourcePackageManifest _package() => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
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
        expression: 'article',
      ),
      fields: [
        SourceFieldRule(name: 'source', valueKind: SourceValueKind.text),
      ],
      resultLimit: 1,
    ),
  ],
);

SourceEpisodeIdentity _episode() => SourceEpisodeIdentity(
  sourceId: 'example.anime',
  lineId: 'line-1',
  subjectId: 'subject-1',
  episodeId: 'episode-1',
);

AdRemovalPlan _adPlan(SourceEpisodeIdentity episode) => AdRemovalPlan(
  key: AdRemovalPlanKey(
    episode: episode,
    manifestFingerprint: ManifestFingerprint(
      algorithm: 'sha256',
      value: 'fixture-fingerprint',
    ),
  ),
);

PlaybackProxyBudget _proxyBudget() => PlaybackProxyBudget(
  maxPlaylistBytes: 64 * 1024,
  maxResponseBytes: 1024 * 1024,
  maxRequestHeaderBytes: 64 * 1024,
  maxCookieBytes: 64 * 1024,
  maxRedirects: 3,
  maxRegisteredResources: 100,
  upstreamTimeout: const Duration(seconds: 5),
);

final class _RecordingResolver implements PlaybackSessionResolver {
  _RecordingResolver(this.session);

  final PlaybackSession session;
  int resolveCount = 0;
  PlaybackSessionResolutionRequest? lastRequest;

  @override
  Future<PlaybackSession> resolve(
    PlaybackSessionResolutionRequest request,
  ) async {
    resolveCount++;
    lastRequest = request;
    return session;
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
  @override
  String get backendId => 'test';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => const Stream<PlaybackEvent>.empty();

  final List<PlaybackSession> openedSessions = [];

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('test');

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
  Future<void> close() async {}
}

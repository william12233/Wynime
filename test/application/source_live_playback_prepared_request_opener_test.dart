import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_prepared_request_opener.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  final openRequest = _openRequest();
  final readyResult = SourceLivePlaybackOpenRequestCoordinatorResult(
    status: SourceLivePlaybackOpenRequestCoordinatorStatus.ready,
    request: openRequest,
    sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
    routeStatus: SourceLivePlaybackRouteStatus.selected,
    requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
  );

  test('passes one ready live HTTP request to PlaybackCoordinator', () async {
    final episode = openRequest.resolution.episode;
    final resolver = _RecordingResolver(
      testPlaybackSession(
        episode: episode,
        mediaUri: openRequest.resolution.candidate.uri,
        pageUri: openRequest.resolution.pageUri,
        adRemovalPlan: openRequest.resolution.adRemovalPlan,
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

    final result = await PlaybackCoordinatorLivePlaybackPreparedRequestOpener(
      coordinator: coordinator,
    ).openPreparedRequest(openResult: readyResult);

    expect(result.status, SourceLivePlaybackPreparedOpenStatus.opened);
    expect(result.session, same(coordinator.currentSession));
    expect(result.session, same(player.openedSessions.single));
    expect(resolver.resolveCount, 1);
    expect(resolver.lastRequest, same(openRequest.resolution));
    expect(proxy.requests.single.budget, same(openRequest.proxyBudget));
    expect(proxy.requests.single.addressFamily, openRequest.addressFamily);
  });

  test('short-circuits every non-ready live HTTP open result', () async {
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
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: SourceLivePlaybackRouteStatus.notFound,
        reasonCode: 'live_route_not_found',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.consentRequired,
        reasonCode: 'consent_required',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.disabled,
        reasonCode: 'package_disabled',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.incompatible,
        reasonCode: 'incompatible_wynime_version',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.requestRejected,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus:
            SourcePlaybackSessionRequestBuildStatus.mediaUriNotAllowed,
        reasonCode: 'media_uri_not_allowed',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.failed,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        reasonCode: 'live_session_request_failed',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status:
            SourceLivePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'invalid_refresh_leeway',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .invalidAutomaticRefreshes,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'invalid_automatic_refreshes',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .invalidEpisodeDuration,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'invalid_episode_duration',
      ),
      SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'live_open_request_build_failed',
      ),
    ];

    for (final input in results) {
      final result = await PlaybackCoordinatorLivePlaybackPreparedRequestOpener(
        coordinator: coordinator,
      ).openPreparedRequest(openResult: input);
      expect(
        result.status,
        SourceLivePlaybackPreparedOpenStatus.requestNotReady,
      );
      expect(result.session, isNull);
      expect(result.openRequestStatus, input.status);
      expect(result.sessionStatus, input.sessionStatus);
      expect(result.routeStatus, input.routeStatus);
      expect(result.requestStatus, input.requestStatus);
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
      () => PlaybackCoordinatorLivePlaybackPreparedRequestOpener(
        coordinator: coordinator,
      ).openPreparedRequest(openResult: readyResult),
      throwsA(isA<PlaybackOperationException>()),
    );
  });

  test('keeps prepared-live results bounded and redacted', () {
    expect(
      () => SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.opened,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: SourceLivePlaybackOpenRequestCoordinatorStatus.ready,
        reasonCode: 'live_open_request_not_ready',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'live_open_request_not_ready',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.requestRejected,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'session_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus:
            SourceLivePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'https://unsafe.invalid/video',
      ),
      throwsArgumentError,
    );
    final result = SourceLivePlaybackPreparedOpenResult(
      status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
      openRequestStatus:
          SourceLivePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
      sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
      routeStatus: SourceLivePlaybackRouteStatus.selected,
      requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
      reasonCode: 'invalid_episode_duration',
    );
    expect(result.toString(), contains('status: requestNotReady'));
    expect(result.toString(), contains('hasSession: false'));
    expect(result.toString(), isNot(contains('media.example.com')));
    expect(result.toString(), isNot(contains('secret-cookie-value')));
  });
}

PlaybackOpenRequest _openRequest() {
  final episode = testEpisode();
  final policy = testSourcePolicy(
    domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
  );
  final resolution = PlaybackSessionResolutionRequest(
    episode: episode,
    pageUri: Uri.parse('https://example.com/watch/episode-1'),
    candidate: WebMediaCandidate(
      kind: WebCandidateKind.video,
      uri: Uri.parse('https://media.example.com/video/episode-1.mp4'),
      headers: const {'x-capture': 'captured'},
      sourceEventSequence: 10,
    ),
    securityPolicy: policy,
    adRemovalPlan: testAdRemovalPlan(episode),
    cookies: [
      WebCaptureCookie(
        name: 'session',
        value: 'secret-cookie-value',
        domain: 'example.com',
      ),
    ],
    userAgent: 'Mozilla/5.0 Wynime Test Browser',
  );
  return PlaybackOpenRequest(
    resolution: resolution,
    proxyBudget: testProxyBudget(),
    addressFamily: LoopbackAddressFamily.ipv6,
  );
}

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
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final List<PlaybackSession> openedSessions = [];

  @override
  String get backendId => 'task051-test-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task051-test-player');

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

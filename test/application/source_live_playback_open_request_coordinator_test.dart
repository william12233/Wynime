import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  final session = _readySessionResult();
  final proxyBudget = _proxyBudget();

  test(
    'composes one ready live session request and preserves open options',
    () {
      final bangumiEpisode = BangumiEpisodeTarget(
        subjectId: 'subject-target',
        episodeId: 'episode-target',
      );
      final result = const SourceLivePlaybackOpenRequestCoordinator()
          .buildOpenRequest(
            sessionResult: session,
            proxyBudget: proxyBudget,
            addressFamily: LoopbackAddressFamily.ipv6,
            refreshLeeway: const Duration(seconds: 41),
            maxAutomaticRefreshes: 2,
            episodeDuration: const Duration(minutes: 24),
            bangumiEpisode: bangumiEpisode,
          );

      expect(
        result.status,
        SourceLivePlaybackOpenRequestCoordinatorStatus.ready,
      );
      expect(result.request, isNotNull);
      expect(result.request!.resolution, same(session.request));
      expect(result.request!.proxyBudget, same(proxyBudget));
      expect(result.request!.addressFamily, LoopbackAddressFamily.ipv6);
      expect(result.request!.refreshLeeway, const Duration(seconds: 41));
      expect(result.request!.maxAutomaticRefreshes, 2);
      expect(result.request!.episodeDuration, const Duration(minutes: 24));
      expect(result.request!.bangumiEpisode, same(bangumiEpisode));
      expect(
        result.sessionStatus,
        SourceLivePlaybackSessionRequestStatus.ready,
      );
      expect(result.routeStatus, SourceLivePlaybackRouteStatus.selected);
      expect(
        result.requestStatus,
        SourcePlaybackSessionRequestBuildStatus.ready,
      );
    },
  );

  test('does not compose any non-ready live session result', () {
    final results = [
      SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: SourceLivePlaybackRouteStatus.notFound,
        reasonCode: 'live_route_not_found',
      ),
      SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.consentRequired,
        reasonCode: 'consent_required',
      ),
      SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.disabled,
        reasonCode: 'package_disabled',
      ),
      SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.incompatible,
        reasonCode: 'incompatible_wynime_version',
      ),
      SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.requestRejected,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus:
            SourcePlaybackSessionRequestBuildStatus.mediaUriNotAllowed,
        reasonCode: 'media_uri_not_allowed',
      ),
      SourceLivePlaybackSessionRequestResult(
        status: SourceLivePlaybackSessionRequestStatus.failed,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        reasonCode: 'live_session_request_failed',
      ),
    ];

    for (final sessionResult in results) {
      final result = const SourceLivePlaybackOpenRequestCoordinator()
          .buildOpenRequest(
            sessionResult: sessionResult,
            proxyBudget: proxyBudget,
          );
      expect(
        result.status,
        SourceLivePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
      );
      expect(result.request, isNull);
      expect(result.sessionStatus, sessionResult.status);
      expect(result.routeStatus, sessionResult.routeStatus);
      expect(result.requestStatus, sessionResult.requestStatus);
      expect(result.reasonCode, sessionResult.reasonCode);
    }
  });

  test('rejects invalid open options before constructing a request', () {
    final coordinator = const SourceLivePlaybackOpenRequestCoordinator();

    final invalidRefresh = coordinator.buildOpenRequest(
      sessionResult: session,
      proxyBudget: proxyBudget,
      refreshLeeway: const Duration(seconds: -1),
    );
    expect(
      invalidRefresh.status,
      SourceLivePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway,
    );
    expect(invalidRefresh.request, isNull);

    for (final value in [-1, 4]) {
      final result = coordinator.buildOpenRequest(
        sessionResult: session,
        proxyBudget: proxyBudget,
        maxAutomaticRefreshes: value,
      );
      expect(
        result.status,
        SourceLivePlaybackOpenRequestCoordinatorStatus
            .invalidAutomaticRefreshes,
      );
      expect(result.request, isNull);
    }

    final invalidEpisode = coordinator.buildOpenRequest(
      sessionResult: session,
      proxyBudget: proxyBudget,
      episodeDuration: const Duration(seconds: -1),
    );
    expect(
      invalidEpisode.status,
      SourceLivePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
    );
    expect(invalidEpisode.request, isNull);
  });

  test('keeps result invariants and diagnostics bounded', () {
    final result = const SourceLivePlaybackOpenRequestCoordinator()
        .buildOpenRequest(sessionResult: session, proxyBudget: proxyBudget);

    expect(result.request!.resolution, same(session.request));
    expect(result.toString(), contains('status: ready'));
    expect(result.toString(), contains('hasRequest: true'));
    expect(result.toString(), isNot(contains('media.example.com')));
    expect(result.toString(), isNot(contains('episode-1')));
    expect(
      () => SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus.ready,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'live_session_request_ready',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.requestRejected,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'session_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'https://unsafe.invalid/video',
      ),
      throwsArgumentError,
    );
  });
}

SourceLivePlaybackSessionRequestResult _readySessionResult() {
  final episode = _episode();
  final policy = testSourcePolicy(
    domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
  );
  final request = PlaybackSessionResolutionRequest(
    episode: episode,
    pageUri: Uri.parse('https://example.com/watch/episode-1'),
    candidate: WebMediaCandidate(
      kind: WebCandidateKind.video,
      uri: Uri.parse('https://media.example.com/video/episode-1.mp4'),
      headers: const {'x-capture': 'captured'},
      sourceEventSequence: 10,
    ),
    securityPolicy: policy,
    adRemovalPlan: _adPlan(episode),
  );
  return SourceLivePlaybackSessionRequestResult(
    status: SourceLivePlaybackSessionRequestStatus.ready,
    request: request,
    routeStatus: SourceLivePlaybackRouteStatus.selected,
    requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
  );
}

SourceEpisodeIdentity _episode() => SourceEpisodeIdentity(
  sourceId: 'demo.source',
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

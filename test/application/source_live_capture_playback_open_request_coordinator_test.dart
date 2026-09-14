import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/source_live_capture_playback_open_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  final session = _readySessionResult();
  final proxyBudget = _proxyBudget();

  test('composes a ready live session request into one open request', () {
    final bangumiEpisode = BangumiEpisodeTarget(
      subjectId: 'subject-target',
      episodeId: 'episode-target',
    );
    final result = const SourceLiveCapturePlaybackOpenRequestCoordinator()
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
      SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready,
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
      SourceLiveCapturePlaybackSessionRequestStatus.ready,
    );
    expect(result.routeStatus, SourceLiveCapturePlaybackRouteStatus.selected);
  });

  test('does not compose non-ready live session results', () {
    final results = [
      SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.notFound,
        reasonCode: 'live_route_not_found',
      ),
      SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.consentRequired,
        reasonCode: 'consent_required',
      ),
      SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.disabled,
        reasonCode: 'package_disabled',
      ),
      SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.incompatible,
        reasonCode: 'incompatible_wynime_version',
      ),
      SourceLiveCapturePlaybackSessionRequestResult(
        status: SourceLiveCapturePlaybackSessionRequestStatus.failed,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
        reasonCode: 'live_session_request_failed',
      ),
    ];

    for (final sessionResult in results) {
      final result = const SourceLiveCapturePlaybackOpenRequestCoordinator()
          .buildOpenRequest(
            sessionResult: sessionResult,
            proxyBudget: proxyBudget,
          );
      expect(
        result.status,
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
      );
      expect(result.request, isNull);
      expect(result.sessionStatus, sessionResult.status);
      expect(result.routeStatus, sessionResult.routeStatus);
      expect(result.reasonCode, sessionResult.reasonCode);
    }
  });

  test('rejects invalid open options before constructing a request', () {
    final coordinator = const SourceLiveCapturePlaybackOpenRequestCoordinator();

    final invalidRefresh = coordinator.buildOpenRequest(
      sessionResult: session,
      proxyBudget: proxyBudget,
      refreshLeeway: const Duration(seconds: -1),
    );
    expect(
      invalidRefresh.status,
      SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
          .invalidRefreshLeeway,
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
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
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
      SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
          .invalidEpisodeDuration,
    );
    expect(invalidEpisode.request, isNull);
  });

  test('keeps live request identity and result diagnostics bounded', () {
    final result = const SourceLiveCapturePlaybackOpenRequestCoordinator()
        .buildOpenRequest(sessionResult: session, proxyBudget: proxyBudget);

    expect(result.request!.resolution, same(session.request));
    expect(result.toString(), contains('status: ready'));
    expect(result.toString(), contains('hasRequest: true'));
    expect(result.toString(), isNot(contains('secret-cookie-value')));
    expect(result.toString(), isNot(contains('media.example.com')));
    expect(
      () => SourceLiveCapturePlaybackOpenRequestCoordinatorResult(
        status: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackOpenRequestCoordinatorResult(
        status: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: SourceLiveCapturePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
        reasonCode: 'live_route_not_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackOpenRequestCoordinatorResult(
        status: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus:
            SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
        reasonCode: 'live_route_not_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveCapturePlaybackOpenRequestCoordinatorResult(
        status: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .invalidEpisodeDuration,
        sessionStatus: SourceLiveCapturePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
        reasonCode: 'https://unsafe.invalid/video',
      ),
      throwsArgumentError,
    );
  });
}

SourceLiveCapturePlaybackSessionRequestResult _readySessionResult() {
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
    cookies: [
      WebCaptureCookie(
        name: 'session',
        value: 'secret-cookie-value',
        domain: 'example.com',
      ),
    ],
    userAgent: 'Mozilla/5.0 Wynime Test Browser',
  );
  return SourceLiveCapturePlaybackSessionRequestResult(
    status: SourceLiveCapturePlaybackSessionRequestStatus.ready,
    request: request,
    routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
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

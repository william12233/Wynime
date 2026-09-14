import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/application/source_playback_session_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
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
import 'package:wynime/src/domain/services/playback_session_resolver.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  final session = _readySessionResult();
  final proxyBudget = _proxyBudget();

  test('composes a ready session request into one open request', () {
    final bangumiEpisode = BangumiEpisodeTarget(
      subjectId: 'subject-target',
      episodeId: 'episode-target',
    );
    final result = const SourcePlaybackOpenRequestCoordinator()
        .buildOpenRequest(
          sessionResult: session,
          proxyBudget: proxyBudget,
          addressFamily: LoopbackAddressFamily.ipv6,
          refreshLeeway: const Duration(seconds: 41),
          maxAutomaticRefreshes: 2,
          episodeDuration: const Duration(minutes: 24),
          bangumiEpisode: bangumiEpisode,
        );

    expect(result.status, SourcePlaybackOpenRequestCoordinatorStatus.ready);
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
      SourcePlaybackSessionRequestCoordinatorStatus.ready,
    );
    expect(result.requestStatus, SourcePlaybackSessionRequestBuildStatus.ready);
  });

  test('does not compose non-ready session results', () {
    final results = [
      SourcePlaybackSessionRequestCoordinatorResult(
        status: SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.notFound,
        reasonCode: 'route_not_found',
      ),
      SourcePlaybackSessionRequestCoordinatorResult(
        status: SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.packageMismatch,
        reasonCode: 'package_identity_mismatch',
      ),
      SourcePlaybackSessionRequestCoordinatorResult(
        status: SourcePlaybackSessionRequestCoordinatorStatus.failed,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
        reasonCode: 'session_request_build_failed',
      ),
    ];

    for (final sessionResult in results) {
      final result = const SourcePlaybackOpenRequestCoordinator()
          .buildOpenRequest(
            sessionResult: sessionResult,
            proxyBudget: proxyBudget,
          );
      expect(
        result.status,
        SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
      );
      expect(result.request, isNull);
      expect(result.sessionStatus, sessionResult.status);
      expect(result.requestStatus, sessionResult.requestStatus);
      expect(result.reasonCode, sessionResult.reasonCode);
    }
  });

  test('rejects invalid open options before constructing a request', () {
    final coordinator = const SourcePlaybackOpenRequestCoordinator();

    final invalidRefresh = coordinator.buildOpenRequest(
      sessionResult: session,
      proxyBudget: proxyBudget,
      refreshLeeway: const Duration(seconds: -1),
    );
    expect(
      invalidRefresh.status,
      SourcePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway,
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
        SourcePlaybackOpenRequestCoordinatorStatus.invalidAutomaticRefreshes,
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
      SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
    );
    expect(invalidEpisode.request, isNull);
  });

  test('keeps resolution identity and does not fabricate a session', () {
    final result = const SourcePlaybackOpenRequestCoordinator()
        .buildOpenRequest(sessionResult: session, proxyBudget: proxyBudget);

    expect(result.request!.resolution, isA<PlaybackSessionResolutionRequest>());
    expect(result.toString(), contains('status: ready'));
    expect(result.toString(), contains('hasRequest: true'));
    expect(result.toString(), isNot(contains('master.m3u8')));
    expect(result.toString(), isNot(contains('episode-1')));
    expect(result.toString(), isNot(contains('example.anime')));
  });

  test('result invariants reject fabricated or unsafe states', () {
    expect(
      () => SourcePlaybackOpenRequestCoordinatorResult(
        status: SourcePlaybackOpenRequestCoordinatorStatus.ready,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        reasonCode: 'session_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'route_not_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
        reasonCode: 'session_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'session_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestCoordinatorResult(
        status: SourcePlaybackOpenRequestCoordinatorStatus.failed,
        sessionStatus:
            SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.packageMismatch,
        reasonCode: 'open_request_build_failed',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
        reasonCode: 'https://unsafe.invalid/video',
      ),
      throwsArgumentError,
    );
  });
}

SourcePlaybackSessionRequestCoordinatorResult _readySessionResult() {
  final package = _package();
  final episode = _episode();
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
  final routeResult = SourcePlaybackRouteCoordinatorResult(
    status: SourcePlaybackRouteCoordinatorStatus.selected,
    route: route,
    selectionResults: [
      SourcePlaybackRouteSelectionResult(
        status: SourcePlaybackRouteSelectionStatus.selected,
        route: route,
      ),
    ],
  );
  return SourcePlaybackSessionRequestCoordinator(
    builder: const DeterministicSourcePlaybackSessionRequestBuilder(),
  ).buildRequest(
    routeResult: routeResult,
    package: package,
    adRemovalPlan: _adPlan(episode),
    sourceEventSequence: 0,
  );
}

SourcePackageManifest _package() => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
  securityPolicy: testSourcePolicy(
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

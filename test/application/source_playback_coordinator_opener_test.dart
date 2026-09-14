import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_playback_coordinator_opener.dart';
import 'package:wynime/src/application/source_playback_open_request_builder.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

import '../helpers/playback_test_support.dart';

void main() {
  final package = _package();
  final episode = _episode();
  final route = _route(episode);
  final plan = _adPlan(episode);
  final proxyBudget = _proxyBudget();

  test(
    'opens a ready source request through the existing coordinator',
    () async {
      final requestBuilder = _CapturingOpenRequestBuilder();
      final resolver = _RecordingResolver(
        testPlaybackSession(
          episode: episode,
          mediaUri: route.source.mediaUri,
          pageUri: route.source.pageUri,
          adRemovalPlan: plan,
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
      final opener = PlaybackCoordinatorSourceOpener(
        coordinator: coordinator,
        openRequestBuilder: requestBuilder,
      );
      final bangumiEpisode = BangumiEpisodeTarget(
        subjectId: '100',
        episodeId: '200',
      );

      final result = await opener.openFromSource(
        route: route,
        package: package,
        adRemovalPlan: plan,
        sourceEventSequence: 11,
        proxyBudget: proxyBudget,
        addressFamily: LoopbackAddressFamily.ipv6,
        refreshLeeway: const Duration(seconds: 45),
        maxAutomaticRefreshes: 2,
        episodeDuration: const Duration(minutes: 24),
        bangumiEpisode: bangumiEpisode,
      );

      expect(result.status, SourcePlaybackCoordinatorOpenStatus.opened);
      expect(result.session, same(coordinator.currentSession));
      expect(result.session, same(player.openedSessions.single));
      expect(requestBuilder.callCount, 1);
      expect(requestBuilder.route, same(route));
      expect(requestBuilder.package, same(package));
      expect(requestBuilder.adRemovalPlan, same(plan));
      expect(requestBuilder.sourceEventSequence, 11);
      expect(requestBuilder.proxyBudget, same(proxyBudget));
      expect(requestBuilder.addressFamily, LoopbackAddressFamily.ipv6);
      expect(requestBuilder.refreshLeeway, const Duration(seconds: 45));
      expect(requestBuilder.maxAutomaticRefreshes, 2);
      expect(requestBuilder.episodeDuration, const Duration(minutes: 24));
      expect(requestBuilder.bangumiEpisode, same(bangumiEpisode));
      expect(resolver.resolveCount, 1);
      expect(proxy.requests.single.budget, same(proxyBudget));
      expect(proxy.requests.single.addressFamily, LoopbackAddressFamily.ipv6);
      expect(
        proxy.requests.single.securityPolicy,
        same(package.securityPolicy),
      );
      expect(player.openedSessions.single.mediaUri, route.source.mediaUri);
    },
  );

  test(
    'rejects before coordinator invocation when request construction fails',
    () async {
      final resolver = _RecordingResolver(
        testPlaybackSession(episode: episode),
      );
      final proxy = _RecordingProxy();
      final player = _RecordingPlayer();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      addTearDown(coordinator.close);
      final opener = PlaybackCoordinatorSourceOpener(
        coordinator: coordinator,
        openRequestBuilder: _RejectingOpenRequestBuilder(),
      );

      final result = await opener.openFromSource(
        route: route,
        package: package,
        adRemovalPlan: plan,
        sourceEventSequence: 11,
        proxyBudget: proxyBudget,
      );

      expect(
        result.status,
        SourcePlaybackCoordinatorOpenStatus.requestRejected,
      );
      expect(
        result.requestStatus,
        SourcePlaybackOpenRequestBuildStatus.invalidEpisodeDuration,
      );
      expect(result.reasonCode, 'invalid_episode_duration');
      expect(result.session, isNull);
      expect(resolver.resolveCount, 0);
      expect(proxy.requests, isEmpty);
      expect(player.openedSessions, isEmpty);
    },
  );

  test(
    'propagates coordinator failure without reporting a successful open',
    () async {
      final coordinator = PlaybackCoordinator(
        resolver: _ThrowingResolver(),
        proxy: _RecordingProxy(),
        player: _RecordingPlayer(),
      );
      addTearDown(coordinator.close);
      final opener = PlaybackCoordinatorSourceOpener(coordinator: coordinator);

      expect(
        () => opener.openFromSource(
          route: route,
          package: package,
          adRemovalPlan: plan,
          sourceEventSequence: 11,
          proxyBudget: proxyBudget,
        ),
        throwsA(isA<PlaybackOperationException>()),
      );
    },
  );

  test('result invariants and diagnostics remain bounded and redacted', () {
    expect(
      () => SourcePlaybackCoordinatorOpenResult(
        status: SourcePlaybackCoordinatorOpenStatus.opened,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackCoordinatorOpenResult(
        status: SourcePlaybackCoordinatorOpenStatus.requestRejected,
        reasonCode: 'source_open_request_rejected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackCoordinatorOpenResult(
        status: SourcePlaybackCoordinatorOpenStatus.requestRejected,
        requestStatus: SourcePlaybackOpenRequestBuildStatus.ready,
        reasonCode: 'source_open_request_rejected',
      ),
      throwsArgumentError,
    );
    Object? unsafeReasonError;
    try {
      SourcePlaybackCoordinatorOpenResult(
        status: SourcePlaybackCoordinatorOpenStatus.requestRejected,
        requestStatus:
            SourcePlaybackOpenRequestBuildStatus.invalidEpisodeDuration,
        reasonCode: 'https://unsafe.invalid/?value=private',
      );
    } catch (error) {
      unsafeReasonError = error;
    }
    expect(unsafeReasonError, isA<ArgumentError>());
    expect(unsafeReasonError.toString(), isNot(contains('unsafe.invalid')));
    expect(unsafeReasonError.toString(), isNot(contains('value=private')));
    expect(
      () => SourcePlaybackCoordinatorOpenResult(
        status: SourcePlaybackCoordinatorOpenStatus.requestRejected,
        requestStatus:
            SourcePlaybackOpenRequestBuildStatus.invalidEpisodeDuration,
        reasonCode: 'Not Safe',
      ),
      throwsArgumentError,
    );
    final result = SourcePlaybackCoordinatorOpenResult(
      status: SourcePlaybackCoordinatorOpenStatus.requestRejected,
      requestStatus:
          SourcePlaybackOpenRequestBuildStatus.invalidEpisodeDuration,
      reasonCode: 'invalid_episode_duration',
    );
    final diagnostic = result.toString();
    expect(diagnostic, contains('status: requestRejected'));
    expect(diagnostic, contains('hasSession: false'));
    expect(diagnostic, isNot(contains('example.anime')));
    expect(diagnostic, isNot(contains('master.m3u8')));
    expect(diagnostic, isNot(contains('episode-1')));
  });
}

final class _CapturingOpenRequestBuilder
    implements SourcePlaybackOpenRequestBuilder {
  final SourcePlaybackOpenRequestBuilder _delegate =
      const DeterministicSourcePlaybackOpenRequestBuilder();

  int callCount = 0;
  SourcePlaybackRoute? route;
  SourcePackageManifest? package;
  AdRemovalPlan? adRemovalPlan;
  int? sourceEventSequence;
  PlaybackProxyBudget? proxyBudget;
  LoopbackAddressFamily? addressFamily;
  Duration? refreshLeeway;
  int? maxAutomaticRefreshes;
  Duration? episodeDuration;
  BangumiEpisodeTarget? bangumiEpisode;

  @override
  SourcePlaybackOpenRequestBuildResult buildOpenRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
    required PlaybackProxyBudget proxyBudget,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) {
    callCount++;
    this.route = route;
    this.package = package;
    this.adRemovalPlan = adRemovalPlan;
    this.sourceEventSequence = sourceEventSequence;
    this.proxyBudget = proxyBudget;
    this.addressFamily = addressFamily;
    this.refreshLeeway = refreshLeeway;
    this.maxAutomaticRefreshes = maxAutomaticRefreshes;
    this.episodeDuration = episodeDuration;
    this.bangumiEpisode = bangumiEpisode;
    return _delegate.buildOpenRequest(
      route: route,
      package: package,
      adRemovalPlan: adRemovalPlan,
      sourceEventSequence: sourceEventSequence,
      proxyBudget: proxyBudget,
      addressFamily: addressFamily,
      refreshLeeway: refreshLeeway,
      maxAutomaticRefreshes: maxAutomaticRefreshes,
      episodeDuration: episodeDuration,
      bangumiEpisode: bangumiEpisode,
    );
  }
}

final class _RejectingOpenRequestBuilder
    implements SourcePlaybackOpenRequestBuilder {
  @override
  SourcePlaybackOpenRequestBuildResult buildOpenRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
    required PlaybackProxyBudget proxyBudget,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) => SourcePlaybackOpenRequestBuildResult(
    status: SourcePlaybackOpenRequestBuildStatus.invalidEpisodeDuration,
    reasonCode: 'invalid_episode_duration',
  );
}

final class _RecordingResolver implements PlaybackSessionResolver {
  _RecordingResolver(this.session);

  final PlaybackSession session;
  int resolveCount = 0;

  @override
  Future<PlaybackSession> resolve(
    PlaybackSessionResolutionRequest request,
  ) async {
    resolveCount++;
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
  String get backendId => 'task013-test-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task013-test-player');

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

SourcePackageManifest _package() => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
  securityPolicy: testSourcePolicy(
    domains: [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
  ),
  programs: [_program()],
);

SourceRuleProgram _program() => SourceRuleProgram(
  programId: 'playback',
  documentKind: SourceDocumentKind.html,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.css,
    expression: 'article',
  ),
  fields: [SourceFieldRule(name: 'source', valueKind: SourceValueKind.text)],
  resultLimit: 1,
);

SourcePlaybackRoute _route(SourceEpisodeIdentity episode) =>
    SourcePlaybackRoute(
      packageId: 'example.anime',
      packageVersion: Version.parse('1.0.0'),
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

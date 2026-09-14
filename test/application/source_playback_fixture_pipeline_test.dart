import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_playback_fixture_pipeline.dart';
import 'package:wynime/src/application/source_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/application/source_playback_session_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_error_classifier.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/player_backend.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/source_package_runtime.dart';
import 'package:wynime/src/domain/services/source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/playback/default_playback_session_resolver.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';

import '../helpers/playback_test_support.dart' as playback_support;
import '../helpers/source_rule_test_support.dart' as source_support;

void main() {
  final package = _package();
  final installedPackage = InstalledSourcePackage(
    package: package,
    status: SourcePackageStatus.enabled,
    requiresConsent: false,
    requiresReconsent: false,
  );
  final episode = _episode();
  final mapping = _mapping();
  final plan = _adPlan(episode);
  final proxyBudget = playback_support.testProxyBudget();

  test(
    'composes playable source through prepared request and one coordinator lifecycle',
    () async {
      final resolver = _RecordingResolver();
      final proxy = _RecordingProxy();
      final player = _RecordingPlayer();
      final bangumiEpisode = BangumiEpisodeTarget(
        subjectId: 'bangumi-subject-1',
        episodeId: 'bangumi-episode-1',
      );
      final harness = _harness(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      addTearDown(harness.coordinator.close);

      final result = await harness.pipeline.openFixture(
        installedPackage: installedPackage,
        programId: ' playback ',
        fixture: _fixture(),
        mapping: mapping,
        episode: episode,
        adRemovalPlan: plan,
        sourceEventSequence: 7,
        proxyBudget: proxyBudget,
        preferredSourceKey: 'primary',
        addressFamily: LoopbackAddressFamily.ipv6,
        refreshLeeway: const Duration(seconds: 41),
        maxAutomaticRefreshes: 2,
        episodeDuration: const Duration(minutes: 24),
        bangumiEpisode: bangumiEpisode,
      );

      expect(result.status, SourcePlaybackFixturePipelineStatus.opened);
      expect(result.failureStage, isNull);
      expect(result.session, same(harness.coordinator.currentSession));
      expect(result.session, same(player.openedSessions.single));
      expect(
        result.session!.mediaUri,
        Uri.parse('https://cdn.example.com/video/master.m3u8'),
      );
      expect(
        result.session!.pageUri,
        Uri.parse('https://example.com/watch/episode-1'),
      );
      expect(
        result.session!.playbackUri,
        Uri.parse('http://127.0.0.1:42001/v1/session/capability'),
      );
      expect(resolver.requests, hasLength(1));
      expect(resolver.requests.single.episode, same(episode));
      expect(resolver.requests.single.adRemovalPlan, same(plan));
      expect(resolver.requests.single.candidate.sourceEventSequence, 7);
      expect(
        resolver.requests.single.candidate.uri,
        Uri.parse('https://cdn.example.com/video/master.m3u8'),
      );
      expect(harness.preparedOpener.requests, hasLength(1));
      final openRequest = harness.preparedOpener.requests.single;
      expect(openRequest.resolution, same(resolver.requests.single));
      expect(openRequest.proxyBudget, same(proxyBudget));
      expect(openRequest.addressFamily, LoopbackAddressFamily.ipv6);
      expect(openRequest.refreshLeeway, const Duration(seconds: 41));
      expect(openRequest.maxAutomaticRefreshes, 2);
      expect(openRequest.episodeDuration, const Duration(minutes: 24));
      expect(openRequest.bangumiEpisode, same(bangumiEpisode));
      expect(proxy.requests.single.budget, same(proxyBudget));
      expect(proxy.requests.single.addressFamily, LoopbackAddressFamily.ipv6);
      expect(
        proxy.requests.single.securityPolicy,
        same(package.securityPolicy),
      );
    },
  );

  test('stops at blocked playable sources before playback execution', () async {
    final runtime = _RecordingRuntime(_runtimeResult(package));
    final normalizer = _RecordingNormalizer(
      (runtimeResult) => _normalizationResult(
        package,
        status: _normalizationStatusFor(runtimeResult.status),
      ),
    );
    final harness = _harness(runtime: runtime, normalizer: normalizer);
    addTearDown(harness.coordinator.close);

    final result = await harness.pipeline.openFixture(
      installedPackage: installedPackage.copyWith(
        status: SourcePackageStatus.disabled,
      ),
      programId: 'playback',
      fixture: _fixture(),
      mapping: mapping,
      episode: episode,
      adRemovalPlan: plan,
      sourceEventSequence: 7,
      proxyBudget: proxyBudget,
    );

    expect(result.status, SourcePlaybackFixturePipelineStatus.notOpened);
    expect(
      result.failureStage,
      SourcePlaybackFixturePipelineFailureStage.playableSources,
    );
    expect(
      result.playableStatus,
      SourcePlayableSourceCoordinatorStatus.noSources,
    );
    expect(runtime.callCount, 0);
    expect(normalizer.callCount, 1);
    expect(harness.resolver, isA<_RecordingResolver>());
    expect((harness.resolver as _RecordingResolver).requests, isEmpty);
    expect(harness.proxy, isA<_RecordingProxy>());
    expect((harness.proxy as _RecordingProxy).requests, isEmpty);
    expect(harness.player, isA<_RecordingPlayer>());
    expect((harness.player as _RecordingPlayer).openedSessions, isEmpty);
  });

  test(
    'maps a playable normalization failure without opening playback',
    () async {
      final runtime = _RecordingRuntime(_runtimeResult(package));
      final normalizer = _RecordingNormalizer(
        (_) => _normalizationResult(
          package,
          status: SourcePlayableSourceNormalizationStatus.failed,
        ),
      );
      final harness = _harness(runtime: runtime, normalizer: normalizer);
      addTearDown(harness.coordinator.close);

      final result = await harness.pipeline.openFixture(
        installedPackage: installedPackage,
        programId: 'playback',
        fixture: _fixture(),
        mapping: mapping,
        episode: episode,
        adRemovalPlan: plan,
        sourceEventSequence: 7,
        proxyBudget: proxyBudget,
      );

      expect(result.status, SourcePlaybackFixturePipelineStatus.notOpened);
      expect(
        result.failureStage,
        SourcePlaybackFixturePipelineFailureStage.playableSources,
      );
      expect(
        result.playableStatus,
        SourcePlayableSourceCoordinatorStatus.failed,
      );
      expect(runtime.callCount, 1);
      expect(normalizer.callCount, 1);
      expect((harness.resolver as _RecordingResolver).requests, isEmpty);
    },
  );

  test(
    'preserves an exact preferred-source failure at the route boundary',
    () async {
      final harness = _harness(
        runtime: _RecordingRuntime(_runtimeResult(package)),
        normalizer: _RecordingNormalizer((_) => _normalizationResult(package)),
      );
      addTearDown(harness.coordinator.close);

      final result = await harness.pipeline.openFixture(
        installedPackage: installedPackage,
        programId: 'playback',
        fixture: _fixture(),
        mapping: mapping,
        episode: episode,
        adRemovalPlan: plan,
        sourceEventSequence: 7,
        proxyBudget: proxyBudget,
        preferredSourceKey: 'missing',
      );

      expect(result.status, SourcePlaybackFixturePipelineStatus.notOpened);
      expect(
        result.failureStage,
        SourcePlaybackFixturePipelineFailureStage.route,
      );
      expect(
        result.routeStatus,
        SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound,
      );
      expect((harness.resolver as _RecordingResolver).requests, isEmpty);
    },
  );

  test(
    'stops at the session-request boundary when the ad plan identity differs',
    () async {
      final harness = _harness(
        runtime: _RecordingRuntime(_runtimeResult(package)),
        normalizer: _RecordingNormalizer((_) => _normalizationResult(package)),
      );
      addTearDown(harness.coordinator.close);

      final result = await harness.pipeline.openFixture(
        installedPackage: installedPackage,
        programId: 'playback',
        fixture: _fixture(),
        mapping: mapping,
        episode: episode,
        adRemovalPlan: _adPlan(_episode(episodeId: 'episode-2')),
        sourceEventSequence: 7,
        proxyBudget: proxyBudget,
      );

      expect(result.status, SourcePlaybackFixturePipelineStatus.notOpened);
      expect(
        result.failureStage,
        SourcePlaybackFixturePipelineFailureStage.sessionRequest,
      );
      expect(
        result.sessionRequestStatus,
        SourcePlaybackSessionRequestCoordinatorStatus.requestRejected,
      );
      expect((harness.resolver as _RecordingResolver).requests, isEmpty);
    },
  );

  test('stops at open-request options before the prepared opener', () async {
    final harness = _harness(
      runtime: _RecordingRuntime(_runtimeResult(package)),
      normalizer: _RecordingNormalizer((_) => _normalizationResult(package)),
    );
    addTearDown(harness.coordinator.close);

    final result = await harness.pipeline.openFixture(
      installedPackage: installedPackage,
      programId: 'playback',
      fixture: _fixture(),
      mapping: mapping,
      episode: episode,
      adRemovalPlan: plan,
      sourceEventSequence: 7,
      proxyBudget: proxyBudget,
      episodeDuration: const Duration(seconds: -1),
    );

    expect(result.status, SourcePlaybackFixturePipelineStatus.notOpened);
    expect(
      result.failureStage,
      SourcePlaybackFixturePipelineFailureStage.openRequest,
    );
    expect(
      result.openRequestStatus,
      SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
    );
    expect((harness.resolver as _RecordingResolver).requests, isEmpty);
  });

  test(
    'preserves the coordinator stable error through the prepared opener',
    () async {
      final harness = _harness(
        resolver: _ThrowingResolver(),
        runtime: _RecordingRuntime(_runtimeResult(package)),
        normalizer: _RecordingNormalizer((_) => _normalizationResult(package)),
      );
      addTearDown(harness.coordinator.close);

      Object? caught;
      try {
        await harness.pipeline.openFixture(
          installedPackage: installedPackage,
          programId: 'playback',
          fixture: _fixture(),
          mapping: mapping,
          episode: episode,
          adRemovalPlan: plan,
          sourceEventSequence: 7,
          proxyBudget: proxyBudget,
        );
      } catch (error) {
        caught = error;
      }

      expect(caught, isA<PlaybackOperationException>());
      expect(caught.toString(), isNot(contains('upstream-secret.invalid')));
      expect((harness.proxy as _RecordingProxy).requests, isEmpty);
      expect((harness.player as _RecordingPlayer).openedSessions, isEmpty);
    },
  );

  test('rejects fabricated pipeline states and redacts diagnostics', () {
    expect(
      () => SourcePlaybackFixturePipelineResult(
        status: SourcePlaybackFixturePipelineStatus.opened,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackFixturePipelineResult(
        status: SourcePlaybackFixturePipelineStatus.notOpened,
        failureStage: SourcePlaybackFixturePipelineFailureStage.playableSources,
        playableStatus: SourcePlayableSourceCoordinatorStatus.partial,
        reasonCode: 'partial_playable_sources',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackFixturePipelineResult(
        status: SourcePlaybackFixturePipelineStatus.notOpened,
        failureStage: SourcePlaybackFixturePipelineFailureStage.route,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.selected,
        reasonCode: 'route_selected',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackFixturePipelineResult(
        status: SourcePlaybackFixturePipelineStatus.notOpened,
        failureStage: SourcePlaybackFixturePipelineFailureStage.openRequest,
        openRequestStatus: SourcePlaybackOpenRequestCoordinatorStatus.ready,
        reasonCode: 'open_request_ready',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourcePlaybackFixturePipelineResult(
        status: SourcePlaybackFixturePipelineStatus.notOpened,
        failureStage: SourcePlaybackFixturePipelineFailureStage.preparedOpen,
        preparedOpenStatus: SourcePlaybackPreparedOpenStatus.opened,
        reasonCode: 'prepared_opened',
      ),
      throwsArgumentError,
    );

    Object? unsafeError;
    try {
      SourcePlaybackFixturePipelineResult(
        status: SourcePlaybackFixturePipelineStatus.notOpened,
        failureStage: SourcePlaybackFixturePipelineFailureStage.route,
        routeStatus: SourcePlaybackRouteCoordinatorStatus.failed,
        reasonCode: 'https://unsafe.invalid/?token=secret',
      );
    } catch (error) {
      unsafeError = error;
    }
    expect(unsafeError, isA<ArgumentError>());
    expect(unsafeError.toString(), isNot(contains('unsafe.invalid')));
    expect(unsafeError.toString(), isNot(contains('token=secret')));

    final diagnostic = SourcePlaybackFixturePipelineResult(
      status: SourcePlaybackFixturePipelineStatus.notOpened,
      failureStage: SourcePlaybackFixturePipelineFailureStage.openRequest,
      openRequestStatus:
          SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
      reasonCode: 'invalid_episode_duration',
    ).toString();
    expect(diagnostic, contains('failureStage: openRequest'));
    expect(diagnostic, contains('openRequestStatus: invalidEpisodeDuration'));
    expect(diagnostic, isNot(contains('example.com')));
    expect(diagnostic, isNot(contains('master.m3u8')));
  });
}

_Harness _harness({
  PlaybackSessionResolver? resolver,
  PlaybackProxyService? proxy,
  PlayerBackend? player,
  SourcePackageRuntime? runtime,
  SourcePlayableSourceNormalizer? normalizer,
}) {
  final actualResolver = resolver ?? _RecordingResolver();
  final actualProxy = proxy ?? _RecordingProxy();
  final actualPlayer = player ?? _RecordingPlayer();
  final coordinator = PlaybackCoordinator(
    resolver: actualResolver,
    proxy: actualProxy,
    player: actualPlayer,
  );
  final preparedOpener = _RecordingPreparedOpener(
    PlaybackCoordinatorPreparedRequestOpener(coordinator: coordinator),
  );
  return _Harness(
    pipeline: SourcePlaybackFixturePipeline(
      playableSourceCoordinator: SourcePlayableSourceCoordinator(
        wynimeVersion: Version.parse('1.5.0'),
        runtime:
            runtime ??
            DeclarativeSourcePackageRuntime(
              wynimeVersion: Version.parse('1.5.0'),
            ),
        normalizer:
            normalizer ?? const DeclarativeSourcePlayableSourceNormalizer(),
      ),
      routeCoordinator: SourcePlaybackRouteCoordinator(
        selector: const DeterministicSourcePlaybackRouteSelector(),
      ),
      sessionRequestCoordinator: const SourcePlaybackSessionRequestCoordinator(
        builder: DeterministicSourcePlaybackSessionRequestBuilder(),
      ),
      openRequestCoordinator: const SourcePlaybackOpenRequestCoordinator(),
      preparedOpener: preparedOpener,
    ),
    coordinator: coordinator,
    resolver: actualResolver,
    proxy: actualProxy,
    player: actualPlayer,
    preparedOpener: preparedOpener,
  );
}

final class _Harness {
  const _Harness({
    required this.pipeline,
    required this.coordinator,
    required this.resolver,
    required this.proxy,
    required this.player,
    required this.preparedOpener,
  });

  final SourcePlaybackFixturePipeline pipeline;
  final PlaybackCoordinator coordinator;
  final PlaybackSessionResolver resolver;
  final PlaybackProxyService proxy;
  final PlayerBackend player;
  final _RecordingPreparedOpener preparedOpener;
}

final class _RecordingPreparedOpener
    implements SourcePlaybackPreparedRequestOpener {
  _RecordingPreparedOpener(this._delegate);

  final PlaybackCoordinatorPreparedRequestOpener _delegate;
  final List<PlaybackOpenRequest> requests = [];

  @override
  Future<SourcePlaybackPreparedOpenResult> openPreparedRequest({
    required SourcePlaybackOpenRequestCoordinatorResult openResult,
  }) {
    final request = openResult.request;
    if (request != null) {
      requests.add(request);
    }
    return _delegate.openPreparedRequest(openResult: openResult);
  }
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
        expression: 'article.source',
      ),
      fields: [
        _field('sourceKey', '.key'),
        _field('label', '.label'),
        _field('kind', '.kind'),
        _field('mediaUri', '.media'),
        _field('pageUri', '.page'),
      ],
      resultLimit: 2,
    ),
  ],
);

SourceFieldRule _field(String name, String selector) => SourceFieldRule(
  name: name,
  valueKind: SourceValueKind.text,
  selector: SourceSelector(kind: SourceSelectorKind.css, expression: selector),
  required: true,
);

SourcePlayableSourceFieldMapping _mapping() => SourcePlayableSourceFieldMapping(
  sourceKeyField: 'sourceKey',
  labelField: 'label',
  kindField: 'kind',
  mediaUriField: 'mediaUri',
  pageUriField: 'pageUri',
);

SourceFixture _fixture() => SourceFixture(
  initialUri: Uri.parse('https://example.com/watch/episode-1'),
  body:
      '<article class="source">'
      '<span class="key">backup</span>'
      '<span class="label">Backup HLS</span>'
      '<span class="kind">hls</span>'
      '<span class="media">https://cdn.example.com/video/backup.m3u8</span>'
      '<span class="page">https://example.com/watch/episode-1</span>'
      '</article>'
      '<article class="source">'
      '<span class="key">primary</span>'
      '<span class="label">Primary HLS</span>'
      '<span class="kind">hls</span>'
      '<span class="media">https://cdn.example.com/video/master.m3u8</span>'
      '<span class="page">https://example.com/watch/episode-1</span>'
      '</article>',
);

SourceEpisodeIdentity _episode({String episodeId = 'episode-1'}) =>
    SourceEpisodeIdentity(
      sourceId: 'example.anime',
      lineId: 'line-1',
      subjectId: 'subject-1',
      episodeId: episodeId,
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

SourceRuntimeResult _runtimeResult(
  SourcePackageManifest package, {
  SourceRuntimeStatus status = SourceRuntimeStatus.available,
}) => SourceRuntimeResult(
  packageId: package.packageId,
  packageVersion: package.version,
  programId: 'playback',
  status: status,
  records: [
    SourceRuntimeRecord({
      'sourceKey': 'primary',
      'label': 'Primary',
      'kind': 'hls',
      'mediaUri': 'https://cdn.example.com/video/master.m3u8',
      'pageUri': 'https://example.com/watch/episode-1',
    }),
  ],
  diagnostics: const [],
  consumedSteps: 1,
  selectorMatches: 1,
);

SourcePlayableSourceNormalizationStatus _normalizationStatusFor(
  SourceRuntimeStatus status,
) => switch (status) {
  SourceRuntimeStatus.available =>
    SourcePlayableSourceNormalizationStatus.available,
  SourceRuntimeStatus.notFound =>
    SourcePlayableSourceNormalizationStatus.notFound,
  SourceRuntimeStatus.disabled =>
    SourcePlayableSourceNormalizationStatus.disabled,
  SourceRuntimeStatus.consentRequired =>
    SourcePlayableSourceNormalizationStatus.consentRequired,
  SourceRuntimeStatus.incompatible =>
    SourcePlayableSourceNormalizationStatus.incompatible,
  SourceRuntimeStatus.failed => SourcePlayableSourceNormalizationStatus.failed,
};

SourcePlayableSourceNormalizationResult _normalizationResult(
  SourcePackageManifest package, {
  SourcePlayableSourceNormalizationStatus status =
      SourcePlayableSourceNormalizationStatus.available,
}) => SourcePlayableSourceNormalizationResult(
  packageId: package.packageId,
  packageVersion: package.version,
  programId: 'playback',
  status: status,
  results: status == SourcePlayableSourceNormalizationStatus.available
      ? [_source()]
      : const [],
  diagnostics: const [],
);

SourcePlayableSource _source() => SourcePlayableSource(
  episode: _episode(),
  sourceKey: 'primary',
  label: 'Primary',
  kind: WebCandidateKind.hls,
  mediaUri: Uri.parse('https://cdn.example.com/video/master.m3u8'),
  pageUri: Uri.parse('https://example.com/watch/episode-1'),
);

final class _RecordingRuntime implements SourcePackageRuntime {
  _RecordingRuntime(this.result);

  final SourceRuntimeResult result;
  int callCount = 0;

  @override
  SourceRuntimeResult executeFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
  }) {
    callCount++;
    return result;
  }
}

final class _RecordingNormalizer implements SourcePlayableSourceNormalizer {
  _RecordingNormalizer(this._resultFor);

  final SourcePlayableSourceNormalizationResult Function(SourceRuntimeResult)
  _resultFor;
  int callCount = 0;

  @override
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  }) {
    callCount++;
    return _resultFor(runtimeResult);
  }
}

final class _RecordingResolver implements PlaybackSessionResolver {
  _RecordingResolver({PlaybackSessionResolver? delegate})
    : _delegate = delegate ?? DefaultPlaybackSessionResolver();

  final PlaybackSessionResolver _delegate;
  final List<PlaybackSessionResolutionRequest> requests = [];

  @override
  Future<PlaybackSession> resolve(PlaybackSessionResolutionRequest request) {
    requests.add(request);
    return _delegate.resolve(request);
  }
}

final class _ThrowingResolver implements PlaybackSessionResolver {
  @override
  Future<PlaybackSession> resolve(PlaybackSessionResolutionRequest request) {
    throw StateError('https://upstream-secret.invalid/token=private');
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
  String get backendId => 'task030-test-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task030-test-player');

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

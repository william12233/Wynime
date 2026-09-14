import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/playback/playback_coordinator.dart';
import 'package:wynime/src/application/source_live_episode_coordinator.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_pipeline.dart';
import 'package:wynime/src/application/source_live_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_session_request_coordinator.dart';
import 'package:wynime/src/application/source_live_search_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_playback_route_selector.dart';
import 'package:wynime/src/application/source_playback_session_request_builder.dart';
import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/models/source_episode_normalization_models.dart';
import 'package:wynime/src/domain/models/source_episode_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_live_operations.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/source_search_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_search_normalization_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/domain/services/source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_episode_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';

import '../helpers/playback_test_support.dart' hide testSourcePolicy;
import '../helpers/source_rule_test_support.dart';

void main() {
  final factory = SourceLiveOperationPlanFactory(
    wynimeVersion: Version.parse('1.0.0'),
  );

  test('materializes all three existing live plan types without I/O', () {
    final installed = _installed();
    final episode = _episode();

    final search = factory.buildSearchPlan(
      installedPackage: installed,
      query: 'A&B?/秘密',
    );
    final episodes = factory.buildEpisodePlan(
      installedPackage: installed,
      episode: episode,
    );
    final playable = factory.buildPlayableSourcePlan(
      installedPackage: installed,
      episode: episode,
    );

    expect(search.status, SourceLiveOperationPlanFactoryStatus.ready);
    expect(search.plan, isA<SourceLiveSearchPlan>());
    expect(search.plan!.requestPlan.installedPackage, same(installed));
    expect(search.plan!.requestPlan.request.securityPolicy, same(_policy));
    expect(
      search.plan!.requestPlan.request.uri.queryParameters['q'],
      'A&B?/秘密',
    );
    expect(
      search.plan!.requestPlan.request.uri.toString(),
      isNot(contains('A&B')),
    );

    expect(episodes.status, SourceLiveOperationPlanFactoryStatus.ready);
    expect(episodes.plan, isA<SourceLiveEpisodePlan>());
    expect(
      episodes.plan!.requestPlan.request.uri.path,
      '/anime/example.anime/subject-1/episodes',
    );
    expect(episodes.plan!.mapping, same(_episodeMapping));

    expect(playable.status, SourceLiveOperationPlanFactoryStatus.ready);
    expect(playable.plan, isA<SourceLivePlayableSourcePlan>());
    expect(playable.plan!.episode, same(episode));
    expect(
      playable.plan!.requestPlan.request.uri.path,
      '/anime/subject-1/episodes/episode-1/sources',
    );
    expect(playable.plan!.mapping, same(_playableMapping));
  });

  test(
    'factory gates consent, disabled and compatibility before request build',
    () {
      final package = _package();

      final consent = factory.buildSearchPlan(
        installedPackage: InstalledSourcePackage(
          package: package,
          status: SourcePackageStatus.disabled,
          requiresConsent: true,
          requiresReconsent: false,
        ),
        query: 'secret',
      );
      expect(
        consent.status,
        SourceLiveOperationPlanFactoryStatus.consentRequired,
      );
      expect(consent.plan, isNull);
      expect(consent.toString(), isNot(contains('secret')));

      final disabled = factory.buildSearchPlan(
        installedPackage: _installed(status: SourcePackageStatus.disabled),
        query: 'secret',
      );
      expect(disabled.status, SourceLiveOperationPlanFactoryStatus.disabled);
      expect(disabled.reasonCode, 'package_disabled');

      final incompatiblePackage = _package(
        constraint: VersionConstraint.parse('^2.0.0'),
      );
      final incompatible = factory.buildSearchPlan(
        installedPackage: _installed(package: incompatiblePackage),
        query: 'secret',
      );
      expect(
        incompatible.status,
        SourceLiveOperationPlanFactoryStatus.incompatible,
      );
      expect(incompatible.reasonCode, 'incompatible_wynime_version');
    },
  );

  test('missing declarations and invalid typed inputs fail closed', () {
    final legacy = SourcePackageManifest(
      schemaVersion: 1,
      packageId: 'legacy.anime',
      displayName: 'Legacy Anime',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      securityPolicy: _policy,
      programs: [
        _program('search', ['subjectId', 'title']),
      ],
    );
    final missing = factory.buildSearchPlan(
      installedPackage: InstalledSourcePackage(
        package: legacy,
        status: SourcePackageStatus.enabled,
        requiresConsent: false,
        requiresReconsent: false,
      ),
      query: 'title',
    );
    expect(
      missing.status,
      SourceLiveOperationPlanFactoryStatus.operationNotFound,
    );
    expect(missing.reasonCode, 'operation_not_declared');

    final mismatch = factory.buildPlayableSourcePlan(
      installedPackage: _installed(),
      episode: SourceEpisodeIdentity(
        sourceId: 'other.source',
        lineId: 'line-1',
        subjectId: 'subject-1',
        episodeId: 'episode-1',
      ),
    );
    expect(mismatch.status, SourceLiveOperationPlanFactoryStatus.invalidInput);
    expect(mismatch.reasonCode, 'episode_identity_mismatch');
    expect(mismatch.plan, isNull);

    final invalidQuery = factory.buildSearchPlan(
      installedPackage: _installed(),
      query: 'secret\u0001',
    );
    expect(
      invalidQuery.status,
      SourceLiveOperationPlanFactoryStatus.invalidInput,
    );
    expect(invalidQuery.reasonCode, 'invalid_search_query');
  });

  test(
    'factory uses exact package policy and produces no parallel authority',
    () {
      final installed = _installed();
      final result = factory.buildPlayableSourcePlan(
        installedPackage: installed,
        episode: _episode(),
      );

      expect(result.status, SourceLiveOperationPlanFactoryStatus.ready);
      expect(
        result.plan!.requestPlan.identityKey,
        contains('example.anime@1.0.0'),
      );
      expect(result.plan!.requestPlan.programId, 'playable');
      expect(result.toRedactedDiagnostic()['hasPlan'], isTrue);
      expect(result.toString(), isNot(contains('https://')));
      expect(result.toString(), isNot(contains('subject-1')));
    },
  );

  test(
    'factory plans enter the existing coordinators through bounded fake transport',
    () async {
      final installed = _installed();
      final episode = _episode();
      final plans = [
        factory
            .buildSearchPlan(installedPackage: installed, query: 'anime')
            .plan!,
        factory
            .buildEpisodePlan(installedPackage: installed, episode: episode)
            .plan!,
        factory
            .buildPlayableSourcePlan(
              installedPackage: installed,
              episode: episode,
            )
            .plan!,
      ];
      final transport = _RecordingTransport();
      final runtime = SourceLiveHttpPackageRuntime(
        httpExecutor: SourceLiveHttpRequestExecutor(
          requestCoordinator: SourceLiveHttpRequestCoordinator(
            wynimeVersion: Version.parse('1.0.0'),
          ),
          transport: transport,
        ),
        fixtureRuntime: _FactoryFixtureRuntime(),
      );

      final search = await SourceLiveSearchCoordinator(
        runtime: runtime,
        normalizer: const DeclarativeSourceSearchNormalizer(),
      ).search(query: 'anime', plans: [plans[0] as SourceLiveSearchPlan]);
      final episodes = await SourceLiveEpisodeCoordinator(
        runtime: runtime,
        normalizer: const DeclarativeSourceEpisodeNormalizer(),
      ).listEpisodes(plans: [plans[1] as SourceLiveEpisodePlan]);
      final playable = await SourceLivePlayableSourceCoordinator(
        runtime: runtime,
        normalizer: const DeclarativeSourcePlayableSourceNormalizer(),
      ).listPlayableSources(plans: [plans[2] as SourceLivePlayableSourcePlan]);

      expect(search.status, SourceSearchCoordinatorStatus.available);
      expect(search.results.single.subjectId, 'subject-1');
      expect(episodes.status, SourceEpisodeCoordinatorStatus.available);
      expect(episodes.episodes.single.identity, episode);
      expect(playable.status, SourcePlayableSourceCoordinatorStatus.available);
      expect(playable.sources.single.kind.name, 'hls');
      expect(transport.requests, hasLength(3));
      expect(transport.requests[0].uri.queryParameters['q'], 'anime');
      expect(
        transport.requests[1].uri.path,
        '/anime/example.anime/subject-1/episodes',
      );
      expect(
        transport.requests[2].uri.path,
        '/anime/subject-1/episodes/episode-1/sources',
      );
    },
  );

  test(
    'factory playable plan traverses the accepted playback pipeline unchanged',
    () async {
      final installed = _installed();
      final episode = _episode();
      final generated = factory.buildPlayableSourcePlan(
        installedPackage: installed,
        episode: episode,
      );
      expect(generated.status, SourceLiveOperationPlanFactoryStatus.ready);
      final plan = generated.plan!;

      final transport = _RecordingTransport(_playableResponseBody());
      final resolver = _PipelineRecordingResolver();
      final proxy = _PipelineRecordingProxy();
      final player = _PipelineRecordingPlayer();
      final coordinator = PlaybackCoordinator(
        resolver: resolver,
        proxy: proxy,
        player: player,
      );
      addTearDown(coordinator.close);

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
      final result =
          await SourceLivePlaybackPipeline(
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
            sessionRequestCoordinator:
                SourceLivePlaybackSessionRequestCoordinator(
                  wynimeVersion: version,
                  builder:
                      const DeterministicSourcePlaybackSessionRequestBuilder(),
                ),
            openRequestCoordinator:
                const SourceLivePlaybackOpenRequestCoordinator(),
            preparedOpener:
                PlaybackCoordinatorLivePlaybackPreparedRequestOpener(
                  coordinator: coordinator,
                ),
          ).openLive(
            plans: [plan],
            adRemovalPlan: testAdRemovalPlan(episode),
            sourceEventSequence: 41,
            proxyBudget: testProxyBudget(),
            preference: SourcePlaybackRoutePreference(
              packageId: installed.package.packageId,
              packageVersion: installed.package.version,
              programId: plan.requestPlan.programId,
              sourceKey: 'primary',
            ),
            addressFamily: LoopbackAddressFamily.ipv6,
            refreshLeeway: const Duration(seconds: 41),
            maxAutomaticRefreshes: 2,
            episodeDuration: const Duration(minutes: 24),
          );

      expect(result.status, SourceLivePlaybackPipelineStatus.opened);
      expect(result.session, same(coordinator.currentSession));
      expect(result.session, same(player.openedSessions.single));
      expect(transport.requests, hasLength(1));
      expect(transport.requests.single, same(plan.requestPlan.request));
      expect(resolver.requests, hasLength(1));
      expect(resolver.requests.single.episode, same(episode));
      expect(
        resolver.requests.single.candidate.uri,
        Uri.parse('https://example.com/media/primary.m3u8'),
      );
      expect(resolver.requests.single.candidate.sourceEventSequence, 41);
      expect(resolver.requests.single.candidate.headers, isEmpty);
      expect(resolver.requests.single.cookies, isEmpty);
      expect(proxy.requests, hasLength(1));
      expect(proxy.requests.single.budget.maxResponseBytes, greaterThan(0));
      expect(proxy.requests.single.addressFamily, LoopbackAddressFamily.ipv6);
    },
  );
}

final _policy = testSourcePolicy();
final _searchMapping = SourceSearchFieldMapping(
  subjectIdField: 'subjectId',
  titleField: 'searchTitle',
);
final _episodeMapping = SourceEpisodeFieldMapping(
  lineIdField: 'lineId',
  subjectIdField: 'subjectId',
  episodeIdField: 'episodeId',
  titleField: 'episodeTitle',
);
final _playableMapping = SourcePlayableSourceFieldMapping(
  sourceKeyField: 'sourceKey',
  labelField: 'label',
  kindField: 'kind',
  mediaUriField: 'mediaUri',
  pageUriField: 'pageUri',
);

InstalledSourcePackage _installed({
  SourcePackageManifest? package,
  SourcePackageStatus status = SourcePackageStatus.enabled,
}) => InstalledSourcePackage(
  package: package ?? _package(),
  status: status,
  requiresConsent: false,
  requiresReconsent: false,
);

SourcePackageManifest _package({
  VersionConstraint? constraint,
}) => SourcePackageManifest(
  schemaVersion: 2,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: constraint ?? VersionConstraint.parse('^1.0.0'),
  securityPolicy: _policy,
  programs: [
    _program('search', ['subjectId', 'searchTitle']),
    _program('episode', ['lineId', 'subjectId', 'episodeId', 'episodeTitle']),
    _program('playable', ['sourceKey', 'label', 'kind', 'mediaUri', 'pageUri']),
  ],
  liveOperations: [
    SourcePackageLiveOperation(
      kind: SourcePackageLiveOperationKind.search,
      programId: 'search',
      uriTemplate: 'https://example.com/search?q={query}',
      mapping: _searchMapping,
    ),
    SourcePackageLiveOperation(
      kind: SourcePackageLiveOperationKind.episode,
      programId: 'episode',
      uriTemplate: 'https://example.com/anime/{sourceId}/{subjectId}/episodes',
      mapping: _episodeMapping,
    ),
    SourcePackageLiveOperation(
      kind: SourcePackageLiveOperationKind.playableSource,
      programId: 'playable',
      uriTemplate:
          'https://example.com/anime/{subjectId}/episodes/{episodeId}/sources',
      mapping: _playableMapping,
    ),
  ],
);

SourceEpisodeIdentity _episode() => SourceEpisodeIdentity(
  sourceId: 'example.anime',
  lineId: 'line-1',
  subjectId: 'subject-1',
  episodeId: 'episode-1',
);

SourceRuleProgram _program(String id, List<String> fields) => SourceRuleProgram(
  programId: id,
  documentKind: SourceDocumentKind.json,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.jsonPath,
    expression: r'$[*]',
  ),
  fields: fields
      .map(
        (name) => SourceFieldRule(
          name: name,
          valueKind: SourceValueKind.raw,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.jsonPath,
            expression: r'$.' + name,
          ),
        ),
      )
      .toList(growable: false),
  resultLimit: 20,
);

final class _RecordingTransport implements SourceHttpTransport {
  _RecordingTransport([this.body = 'bounded-fake-response']);

  final requests = <SourceHttpRequest>[];
  final String body;

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    return SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: request.uri,
        redirectChain: const [],
        body: body,
      ),
    );
  }

  @override
  Future<void> close() async {}
}

String _playableResponseBody() => jsonEncode([
  {
    'sourceKey': 'primary',
    'label': 'Primary HLS',
    'kind': 'hls',
    'mediaUri': 'https://example.com/media/primary.m3u8',
    'pageUri': 'https://example.com/watch/episode-1',
  },
]);

final class _PipelineRecordingResolver implements PlaybackSessionResolver {
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

final class _PipelineRecordingProxy implements PlaybackProxyService {
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

final class _PipelineRecordingPlayer implements PlayerBackend {
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final List<PlaybackSession> openedSessions = [];

  @override
  String get backendId => 'task053-pipeline-player';

  @override
  PlayerBackendKind get kind => PlayerBackendKind.media3;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async =>
      const PlayerBackendAvailability.available('task053-pipeline-player');

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

final class _FactoryFixtureRuntime implements SourcePackageRuntime {
  @override
  SourceRuntimeResult executeFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
  }) {
    final records = switch (programId) {
      'search' => [
        SourceRuntimeRecord({'subjectId': 'subject-1', 'searchTitle': 'Anime'}),
      ],
      'episode' => [
        SourceRuntimeRecord({
          'lineId': 'line-1',
          'subjectId': 'subject-1',
          'episodeId': 'episode-1',
          'episodeTitle': 'Episode 1',
        }),
      ],
      'playable' => [
        SourceRuntimeRecord({
          'sourceKey': 'main',
          'label': 'Main',
          'kind': 'hls',
          'mediaUri': 'https://cdn.example.com/video.m3u8',
          'pageUri': 'https://example.com/watch',
        }),
      ],
      _ => const <SourceRuntimeRecord>[],
    };
    return SourceRuntimeResult(
      packageId: installedPackage.package.packageId,
      packageVersion: installedPackage.package.version,
      programId: programId,
      status: SourceRuntimeStatus.available,
      records: records,
      diagnostics: const [],
      consumedSteps: 1,
      selectorMatches: records.length,
    );
  }
}

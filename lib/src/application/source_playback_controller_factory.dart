import 'package:pub_semver/pub_semver.dart';

import '../domain/models/bangumi_models.dart';
import '../domain/repositories/bangumi_local_store.dart';
import '../domain/repositories/source_playback_mapping_repository.dart';
import '../domain/repositories/watch_history_repository.dart';
import '../domain/services/playback_proxy.dart';
import '../infrastructure/playback/default_playback_session_resolver.dart';
import '../infrastructure/playback/loopback_playback_proxy.dart';
import '../infrastructure/source_rules/declarative_source_playable_source_normalizer.dart';
import '../infrastructure/source_rules/source_package_revision_calculator.dart';
import '../platform/playback/player_backend_factory.dart';
import '../platform/playback/playback_surface_host.dart';
import 'playback/playback_coordinator.dart';
import 'playback/playback_progress_service.dart';
import 'source_installed_live_episode_pipeline.dart';
import 'source_installed_live_playback_pipeline.dart';
import 'source_installed_live_search_pipeline.dart';
import 'source_installed_live_subject_pipeline.dart';
import 'source_live_http_package_runtime.dart';
import 'source_live_operation_plan_factory.dart';
import 'source_live_playable_source_coordinator.dart';
import 'source_live_playback_open_request_coordinator.dart';
import 'source_live_playback_pipeline.dart';
import 'source_live_playback_route_coordinator.dart';
import 'source_live_playback_session_request_coordinator.dart';
import 'source_package_startup_controller.dart';
import 'source_playback_route_coordinator.dart';
import 'source_playback_route_selector.dart';
import 'source_playback_session_request_builder.dart';
import 'source_live_playback_prepared_request_opener.dart';
import 'subject_source_playback_controller.dart';

/// Creates one page-scoped playback coordinator while reusing the bounded
/// package/search/subject infrastructure shared by the app.
final class SourcePlaybackControllerFactory {
  SourcePlaybackControllerFactory({
    required this.sourcePackages,
    required this.searchPipeline,
    required this.subjectPipeline,
    required this.episodePipeline,
    required this.runtime,
    required this.planFactory,
    required this.mappingRepository,
    required this.watchHistory,
    required this.bangumiStore,
    required this.wynimeVersion,
    this.revisionCalculator = const SourcePackageRevisionCalculator(),
  });

  final SourcePackageStartupController sourcePackages;
  final SourceInstalledLiveSearchPipeline searchPipeline;
  final SourceInstalledLiveSubjectPipeline subjectPipeline;
  final SourceInstalledLiveEpisodePipeline episodePipeline;
  final SourceLiveHttpPackageRuntime runtime;
  final SourceLiveOperationPlanFactory planFactory;
  final SourcePlaybackMappingRepository mappingRepository;
  final WatchHistoryRepository watchHistory;
  final BangumiLocalStore bangumiStore;
  final Version wynimeVersion;
  final SourcePackageRevisionCalculator revisionCalculator;

  SubjectSourcePlaybackController create(BangumiSubject subject) {
    final engineRouter = PlayerBackendFactory.create();
    final playbackCoordinator = PlaybackCoordinator(
      resolver: DefaultPlaybackSessionResolver(),
      proxy: LoopbackPlaybackProxyService(),
      player: engineRouter,
      progressService: PlaybackProgressService(
        history: watchHistory,
        bangumi: bangumiStore,
      ),
    );
    final playableCoordinator = SourceLivePlayableSourceCoordinator(
      runtime: runtime,
      normalizer: const DeclarativeSourcePlayableSourceNormalizer(),
    );
    final livePlayback = SourceLivePlaybackPipeline(
      playableSourceCoordinator: playableCoordinator,
      routeCoordinator: SourceLivePlaybackRouteCoordinator(
        wynimeVersion: wynimeVersion,
        routeCoordinator: const SourcePlaybackRouteCoordinator(
          selector: DeterministicSourcePlaybackRouteSelector(),
        ),
      ),
      sessionRequestCoordinator: SourceLivePlaybackSessionRequestCoordinator(
        wynimeVersion: wynimeVersion,
        builder: const DeterministicSourcePlaybackSessionRequestBuilder(),
      ),
      openRequestCoordinator: const SourceLivePlaybackOpenRequestCoordinator(),
      preparedOpener: PlaybackCoordinatorLivePlaybackPreparedRequestOpener(
        coordinator: playbackCoordinator,
      ),
    );
    final installedPlayback = SourceInstalledLivePlaybackPipeline(
      planFactory: planFactory,
      playbackPipeline: livePlayback,
      closeDelegate: () async {
        playableCoordinator.close();
        await playbackCoordinator.close();
      },
    );

    return SubjectSourcePlaybackController(
      subject: subject,
      sourcePackages: sourcePackages,
      searchPipeline: searchPipeline,
      subjectPipeline: subjectPipeline,
      episodePipeline: episodePipeline,
      playbackPipeline: installedPlayback,
      mappingRepository: mappingRepository,
      revisionCalculator: revisionCalculator,
      playbackCoordinator: playbackCoordinator,
      surfaceHost: PlatformPlaybackSurfaceHost(engineRouter),
      closePlayback: installedPlayback.close,
    );
  }

  static PlaybackProxyBudget defaultProxyBudget() => PlaybackProxyBudget(
    maxPlaylistBytes: 2 * 1024 * 1024,
    maxResponseBytes: 64 * 1024 * 1024,
    maxRequestHeaderBytes: 64 * 1024,
    maxCookieBytes: 64 * 1024,
    maxRedirects: 3,
    maxRegisteredResources: 2048,
    upstreamTimeout: const Duration(seconds: 30),
  );
}

import 'package:wynime/src/application/source_live_playable_source_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_open_request_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_prepared_request_opener.dart';
import 'package:wynime/src/application/source_live_playback_route_coordinator.dart';
import 'package:wynime/src/application/source_live_playback_session_request_coordinator.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

enum SourceLivePlaybackPipelineStatus { opened, notOpened }

enum SourceLivePlaybackPipelineFailureStage {
  playableSources,
  route,
  sessionRequest,
  openRequest,
  preparedOpen,
}

/// A bounded outcome for the complete live HTTP playback composition.
///
/// The result exposes only the first failed typed boundary. It never retains a
/// live response, request, cookie, header, URI or raw exception as diagnostic
/// data. A ready result contains only the session owned by the existing
/// [PlaybackCoordinator], through [preparedOpener].
final class SourceLivePlaybackPipelineResult {
  SourceLivePlaybackPipelineResult({
    required this.status,
    this.session,
    this.failureStage,
    this.playableStatus,
    this.routeStatus,
    this.sessionRequestStatus,
    this.openRequestStatus,
    this.preparedOpenStatus,
    this.reasonCode,
  }) {
    final isOpened = status == SourceLivePlaybackPipelineStatus.opened;
    final stageStatusCount = [
      playableStatus,
      routeStatus,
      sessionRequestStatus,
      openRequestStatus,
      preparedOpenStatus,
    ].where((value) => value != null).length;

    if (isOpened) {
      if (session == null ||
          failureStage != null ||
          stageStatusCount != 0 ||
          reasonCode != null) {
        throw ArgumentError(
          'An opened live pipeline result must contain only a session.',
        );
      }
      return;
    }

    if (session != null ||
        failureStage == null ||
        stageStatusCount != 1 ||
        reasonCode == null ||
        !_safeToken(reasonCode!)) {
      throw ArgumentError(
        'A rejected live pipeline result must contain one typed stage failure.',
      );
    }

    final stageMatches = switch (failureStage!) {
      SourceLivePlaybackPipelineFailureStage.playableSources =>
        playableStatus != null &&
            playableStatus != SourcePlayableSourceCoordinatorStatus.available &&
            playableStatus != SourcePlayableSourceCoordinatorStatus.partial,
      SourceLivePlaybackPipelineFailureStage.route =>
        routeStatus != null &&
            routeStatus != SourceLivePlaybackRouteStatus.selected,
      SourceLivePlaybackPipelineFailureStage.sessionRequest =>
        sessionRequestStatus != null &&
            sessionRequestStatus !=
                SourceLivePlaybackSessionRequestStatus.ready,
      SourceLivePlaybackPipelineFailureStage.openRequest =>
        openRequestStatus != null &&
            openRequestStatus !=
                SourceLivePlaybackOpenRequestCoordinatorStatus.ready,
      SourceLivePlaybackPipelineFailureStage.preparedOpen =>
        preparedOpenStatus ==
            SourceLivePlaybackPreparedOpenStatus.requestNotReady,
    };
    if (!stageMatches) {
      throw ArgumentError(
        'The live pipeline failure stage must match its typed status.',
      );
    }
  }

  final SourceLivePlaybackPipelineStatus status;
  final PlaybackSession? session;
  final SourceLivePlaybackPipelineFailureStage? failureStage;
  final SourcePlayableSourceCoordinatorStatus? playableStatus;
  final SourceLivePlaybackRouteStatus? routeStatus;
  final SourceLivePlaybackSessionRequestStatus? sessionRequestStatus;
  final SourceLivePlaybackOpenRequestCoordinatorStatus? openRequestStatus;
  final SourceLivePlaybackPreparedOpenStatus? preparedOpenStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasSession': session != null,
    'failureStage': failureStage?.name,
    'playableStatus': playableStatus?.name,
    'routeStatus': routeStatus?.name,
    'sessionRequestStatus': sessionRequestStatus?.name,
    'openRequestStatus': openRequestStatus?.name,
    'preparedOpenStatus': preparedOpenStatus?.name,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

/// Composes explicit live HTTP source plans through every playback boundary.
///
/// The input is snapshotted once so a one-shot or later-mutated iterable
/// cannot make playable listing and route validation observe different plans.
/// [SourceLivePlayableSourceCoordinator] remains the asynchronous transport
/// and stale-listing authority. [preparedOpener] owns the handoff to the
/// existing [PlaybackCoordinator], which remains the sole session, proxy,
/// player, progress and playback-operation authority.
final class SourceLivePlaybackPipeline {
  SourceLivePlaybackPipeline({
    required this.playableSourceCoordinator,
    required this.routeCoordinator,
    required this.sessionRequestCoordinator,
    required this.openRequestCoordinator,
    required this.preparedOpener,
  });

  static const maxPlans = 32;

  final SourceLivePlayableSourceCoordinator playableSourceCoordinator;
  final SourceLivePlaybackRouteCoordinator routeCoordinator;
  final SourceLivePlaybackSessionRequestCoordinator sessionRequestCoordinator;
  final SourceLivePlaybackOpenRequestCoordinator openRequestCoordinator;
  final SourceLivePlaybackPreparedRequestOpener preparedOpener;

  Future<SourceLivePlaybackPipelineResult> openLive({
    required Iterable<SourceLivePlayableSourcePlan> plans,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
    required PlaybackProxyBudget proxyBudget,
    SourcePlaybackRoutePreference? preference,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) async {
    final planList = <SourceLivePlayableSourcePlan>[];
    try {
      for (final plan in plans) {
        if (planList.length == maxPlans) {
          return _rejected(
            failureStage:
                SourceLivePlaybackPipelineFailureStage.playableSources,
            playableStatus: SourcePlayableSourceCoordinatorStatus.failed,
            reasonCode: 'too_many_source_plans',
          );
        }
        planList.add(plan);
      }
    } on Object {
      return _rejected(
        failureStage: SourceLivePlaybackPipelineFailureStage.playableSources,
        playableStatus: SourcePlayableSourceCoordinatorStatus.failed,
        reasonCode: 'invalid_source_plans',
      );
    }

    final playableResult = await playableSourceCoordinator.listPlayableSources(
      plans: planList,
    );
    if (playableResult.status !=
            SourcePlayableSourceCoordinatorStatus.available &&
        playableResult.status !=
            SourcePlayableSourceCoordinatorStatus.partial) {
      return _rejected(
        failureStage: SourceLivePlaybackPipelineFailureStage.playableSources,
        playableStatus: playableResult.status,
        reasonCode:
            playableResult.reasonCode ?? _playableReason(playableResult.status),
      );
    }

    final routeResult = routeCoordinator.selectRoute(
      plans: planList,
      playableResult: playableResult,
      preference: preference,
    );
    if (routeResult.status != SourceLivePlaybackRouteStatus.selected) {
      return _rejected(
        failureStage: SourceLivePlaybackPipelineFailureStage.route,
        routeStatus: routeResult.status,
        reasonCode: routeResult.reasonCode ?? _routeReason(routeResult.status),
      );
    }

    final sessionResult = sessionRequestCoordinator.buildRequest(
      routeResult: routeResult,
      adRemovalPlan: adRemovalPlan,
      sourceEventSequence: sourceEventSequence,
    );
    if (sessionResult.status != SourceLivePlaybackSessionRequestStatus.ready) {
      return _rejected(
        failureStage: SourceLivePlaybackPipelineFailureStage.sessionRequest,
        sessionRequestStatus: sessionResult.status,
        reasonCode:
            sessionResult.reasonCode ?? _sessionRequestReason(sessionResult),
      );
    }

    final openResult = openRequestCoordinator.buildOpenRequest(
      sessionResult: sessionResult,
      proxyBudget: proxyBudget,
      addressFamily: addressFamily,
      refreshLeeway: refreshLeeway,
      maxAutomaticRefreshes: maxAutomaticRefreshes,
      episodeDuration: episodeDuration,
      bangumiEpisode: bangumiEpisode,
    );
    if (openResult.status !=
        SourceLivePlaybackOpenRequestCoordinatorStatus.ready) {
      return _rejected(
        failureStage: SourceLivePlaybackPipelineFailureStage.openRequest,
        openRequestStatus: openResult.status,
        reasonCode:
            openResult.reasonCode ?? _openRequestReason(openResult.status),
      );
    }

    final preparedResult = await preparedOpener.openPreparedRequest(
      openResult: openResult,
    );
    if (preparedResult.status != SourceLivePlaybackPreparedOpenStatus.opened) {
      return _rejected(
        failureStage: SourceLivePlaybackPipelineFailureStage.preparedOpen,
        preparedOpenStatus: preparedResult.status,
        reasonCode: preparedResult.reasonCode ?? 'prepared_open_not_ready',
      );
    }

    return SourceLivePlaybackPipelineResult(
      status: SourceLivePlaybackPipelineStatus.opened,
      session: preparedResult.session,
    );
  }

  static SourceLivePlaybackPipelineResult _rejected({
    required SourceLivePlaybackPipelineFailureStage failureStage,
    SourcePlayableSourceCoordinatorStatus? playableStatus,
    SourceLivePlaybackRouteStatus? routeStatus,
    SourceLivePlaybackSessionRequestStatus? sessionRequestStatus,
    SourceLivePlaybackOpenRequestCoordinatorStatus? openRequestStatus,
    SourceLivePlaybackPreparedOpenStatus? preparedOpenStatus,
    required String reasonCode,
  }) => SourceLivePlaybackPipelineResult(
    status: SourceLivePlaybackPipelineStatus.notOpened,
    failureStage: failureStage,
    playableStatus: playableStatus,
    routeStatus: routeStatus,
    sessionRequestStatus: sessionRequestStatus,
    openRequestStatus: openRequestStatus,
    preparedOpenStatus: preparedOpenStatus,
    reasonCode: reasonCode,
  );

  static String _playableReason(
    SourcePlayableSourceCoordinatorStatus status,
  ) => switch (status) {
    SourcePlayableSourceCoordinatorStatus.available =>
      'live_playable_sources_available',
    SourcePlayableSourceCoordinatorStatus.partial => 'partial_source_results',
    SourcePlayableSourceCoordinatorStatus.notFound =>
      'source_playable_not_found',
    SourcePlayableSourceCoordinatorStatus.noSources => 'no_enabled_sources',
    SourcePlayableSourceCoordinatorStatus.failed => 'source_playable_failed',
  };

  static String _routeReason(SourceLivePlaybackRouteStatus status) =>
      switch (status) {
        SourceLivePlaybackRouteStatus.selected => 'route_selected',
        SourceLivePlaybackRouteStatus.notFound => 'live_route_not_found',
        SourceLivePlaybackRouteStatus.noSources => 'no_enabled_sources',
        SourceLivePlaybackRouteStatus.disabled => 'package_disabled',
        SourceLivePlaybackRouteStatus.consentRequired => 'consent_required',
        SourceLivePlaybackRouteStatus.incompatible =>
          'incompatible_wynime_version',
        SourceLivePlaybackRouteStatus.preferredSourceNotFound =>
          'preferred_source_not_found',
        SourceLivePlaybackRouteStatus.failed => 'live_route_failed',
      };

  static String _sessionRequestReason(
    SourceLivePlaybackSessionRequestResult result,
  ) => switch (result.status) {
    SourceLivePlaybackSessionRequestStatus.ready =>
      'live_session_request_ready',
    SourceLivePlaybackSessionRequestStatus.routeNotSelected =>
      'live_route_not_selected',
    SourceLivePlaybackSessionRequestStatus.consentRequired =>
      'consent_required',
    SourceLivePlaybackSessionRequestStatus.disabled => 'package_disabled',
    SourceLivePlaybackSessionRequestStatus.incompatible =>
      'incompatible_wynime_version',
    SourceLivePlaybackSessionRequestStatus.requestRejected =>
      'session_request_rejected',
    SourceLivePlaybackSessionRequestStatus.failed =>
      'live_session_request_failed',
  };

  static String _openRequestReason(
    SourceLivePlaybackOpenRequestCoordinatorStatus status,
  ) => switch (status) {
    SourceLivePlaybackOpenRequestCoordinatorStatus.ready =>
      'live_open_request_ready',
    SourceLivePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady =>
      'live_session_request_not_ready',
    SourceLivePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway =>
      'invalid_refresh_leeway',
    SourceLivePlaybackOpenRequestCoordinatorStatus.invalidAutomaticRefreshes =>
      'invalid_automatic_refreshes',
    SourceLivePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration =>
      'invalid_episode_duration',
    SourceLivePlaybackOpenRequestCoordinatorStatus.failed =>
      'live_open_request_failed',
  };
}

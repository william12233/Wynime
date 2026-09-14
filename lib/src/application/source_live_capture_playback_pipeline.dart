import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_live_capture_playable_source_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

import 'source_live_capture_playable_source_coordinator.dart';
import 'source_live_capture_playback_open_request_coordinator.dart';
import 'source_live_capture_playback_prepared_request_opener.dart';
import 'source_live_capture_playback_route_coordinator.dart';
import 'source_live_capture_playback_session_request_coordinator.dart';

enum SourceLiveCapturePlaybackPipelineStatus { opened, notOpened }

enum SourceLiveCapturePlaybackPipelineFailureStage {
  playableSources,
  route,
  sessionRequest,
  openRequest,
  preparedOpen,
}

/// A bounded outcome for the live capture playback composition.
///
/// The result exposes only the first failed typed boundary. It never retains a
/// source request, capture snapshot, cookie, header, URI or raw exception.
final class SourceLiveCapturePlaybackPipelineResult {
  SourceLiveCapturePlaybackPipelineResult({
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
    final isOpened = status == SourceLiveCapturePlaybackPipelineStatus.opened;
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
      SourceLiveCapturePlaybackPipelineFailureStage.playableSources =>
        playableStatus != null &&
            playableStatus != SourceLiveCapturePlayableSourceStatus.available,
      SourceLiveCapturePlaybackPipelineFailureStage.route =>
        routeStatus != null &&
            routeStatus != SourceLiveCapturePlaybackRouteStatus.selected,
      SourceLiveCapturePlaybackPipelineFailureStage.sessionRequest =>
        sessionRequestStatus != null &&
            sessionRequestStatus !=
                SourceLiveCapturePlaybackSessionRequestStatus.ready,
      SourceLiveCapturePlaybackPipelineFailureStage.openRequest =>
        openRequestStatus != null &&
            openRequestStatus !=
                SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready,
      SourceLiveCapturePlaybackPipelineFailureStage.preparedOpen =>
        preparedOpenStatus ==
            SourceLiveCapturePlaybackPreparedOpenStatus.requestNotReady,
    };
    if (!stageMatches) {
      throw ArgumentError(
        'The live pipeline failure stage must match its typed status.',
      );
    }
  }

  final SourceLiveCapturePlaybackPipelineStatus status;
  final PlaybackSession? session;
  final SourceLiveCapturePlaybackPipelineFailureStage? failureStage;
  final SourceLiveCapturePlayableSourceStatus? playableStatus;
  final SourceLiveCapturePlaybackRouteStatus? routeStatus;
  final SourceLiveCapturePlaybackSessionRequestStatus? sessionRequestStatus;
  final SourceLiveCapturePlaybackOpenRequestCoordinatorStatus?
  openRequestStatus;
  final SourceLiveCapturePlaybackPreparedOpenStatus? preparedOpenStatus;
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

/// Composes an accepted live capture through each typed playback boundary.
///
/// The existing [PlaybackCoordinator] remains the only session, proxy, player,
/// lifecycle, progress and generation authority, owned by [preparedOpener].
final class SourceLiveCapturePlaybackPipeline {
  SourceLiveCapturePlaybackPipeline({
    required this.playableSourceCoordinator,
    required this.routeCoordinator,
    required this.sessionRequestCoordinator,
    required this.openRequestCoordinator,
    required this.preparedOpener,
  });

  final SourceLiveCapturePlayableSourceCoordinator playableSourceCoordinator;
  final SourceLiveCapturePlaybackRouteCoordinator routeCoordinator;
  final SourceLiveCapturePlaybackSessionRequestCoordinator
  sessionRequestCoordinator;
  final SourceLiveCapturePlaybackOpenRequestCoordinator openRequestCoordinator;
  final SourceLiveCapturePlaybackPreparedRequestOpener preparedOpener;

  Future<SourceLiveCapturePlaybackPipelineResult> openLive({
    required SourceLiveCapturePlayableSourcePlan plan,
    required AdRemovalPlan adRemovalPlan,
    required PlaybackProxyBudget proxyBudget,
    String? preferredSourceKey,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) async {
    final playableResult = playableSourceCoordinator.normalize(plan);
    if (playableResult.status !=
        SourceLiveCapturePlayableSourceStatus.available) {
      return _rejected(
        failureStage:
            SourceLiveCapturePlaybackPipelineFailureStage.playableSources,
        playableStatus: playableResult.status,
        reasonCode:
            playableResult.reasonCode ?? _playableReason(playableResult.status),
      );
    }

    SourcePlaybackRoutePreference? preference;
    if (preferredSourceKey != null) {
      try {
        preference = SourcePlaybackRoutePreference(
          packageId: plan.installedPackage.package.packageId,
          packageVersion: plan.installedPackage.package.version,
          programId: plan.programId,
          sourceKey: preferredSourceKey,
        );
      } on Object {
        return _rejected(
          failureStage: SourceLiveCapturePlaybackPipelineFailureStage.route,
          routeStatus: SourceLiveCapturePlaybackRouteStatus.failed,
          reasonCode: 'invalid_preferred_source',
        );
      }
    }

    final routeResult = routeCoordinator.selectRoute(
      installedPackage: plan.installedPackage,
      playableResult: playableResult,
      preference: preference,
    );
    if (routeResult.status != SourceLiveCapturePlaybackRouteStatus.selected) {
      return _rejected(
        failureStage: SourceLiveCapturePlaybackPipelineFailureStage.route,
        routeStatus: routeResult.status,
        reasonCode: routeResult.reasonCode ?? _routeReason(routeResult.status),
      );
    }

    final sessionResult = sessionRequestCoordinator.buildRequest(
      installedPackage: plan.installedPackage,
      routeResult: routeResult,
      adRemovalPlan: adRemovalPlan,
    );
    if (sessionResult.status !=
        SourceLiveCapturePlaybackSessionRequestStatus.ready) {
      return _rejected(
        failureStage:
            SourceLiveCapturePlaybackPipelineFailureStage.sessionRequest,
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
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready) {
      return _rejected(
        failureStage: SourceLiveCapturePlaybackPipelineFailureStage.openRequest,
        openRequestStatus: openResult.status,
        reasonCode:
            openResult.reasonCode ?? _openRequestReason(openResult.status),
      );
    }

    final preparedResult = await preparedOpener.openPreparedRequest(
      openResult: openResult,
    );
    if (preparedResult.status !=
        SourceLiveCapturePlaybackPreparedOpenStatus.opened) {
      return _rejected(
        failureStage:
            SourceLiveCapturePlaybackPipelineFailureStage.preparedOpen,
        preparedOpenStatus: preparedResult.status,
        reasonCode: preparedResult.reasonCode ?? 'prepared_open_not_ready',
      );
    }

    return SourceLiveCapturePlaybackPipelineResult(
      status: SourceLiveCapturePlaybackPipelineStatus.opened,
      session: preparedResult.session,
    );
  }

  static SourceLiveCapturePlaybackPipelineResult _rejected({
    required SourceLiveCapturePlaybackPipelineFailureStage failureStage,
    SourceLiveCapturePlayableSourceStatus? playableStatus,
    SourceLiveCapturePlaybackRouteStatus? routeStatus,
    SourceLiveCapturePlaybackSessionRequestStatus? sessionRequestStatus,
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus? openRequestStatus,
    SourceLiveCapturePlaybackPreparedOpenStatus? preparedOpenStatus,
    required String reasonCode,
  }) => SourceLiveCapturePlaybackPipelineResult(
    status: SourceLiveCapturePlaybackPipelineStatus.notOpened,
    failureStage: failureStage,
    playableStatus: playableStatus,
    routeStatus: routeStatus,
    sessionRequestStatus: sessionRequestStatus,
    openRequestStatus: openRequestStatus,
    preparedOpenStatus: preparedOpenStatus,
    reasonCode: reasonCode,
  );

  static String _playableReason(SourceLiveCapturePlayableSourceStatus status) =>
      switch (status) {
        SourceLiveCapturePlayableSourceStatus.available =>
          'live_playable_sources_available',
        SourceLiveCapturePlayableSourceStatus.notFound =>
          'live_playable_sources_not_found',
        SourceLiveCapturePlayableSourceStatus.consentRequired =>
          'consent_required',
        SourceLiveCapturePlayableSourceStatus.disabled => 'package_disabled',
        SourceLiveCapturePlayableSourceStatus.incompatible =>
          'incompatible_wynime_version',
        SourceLiveCapturePlayableSourceStatus.failed => 'live_playable_failed',
      };

  static String _routeReason(SourceLiveCapturePlaybackRouteStatus status) =>
      switch (status) {
        SourceLiveCapturePlaybackRouteStatus.selected => 'route_selected',
        SourceLiveCapturePlaybackRouteStatus.notFound => 'live_route_not_found',
        SourceLiveCapturePlaybackRouteStatus.consentRequired =>
          'consent_required',
        SourceLiveCapturePlaybackRouteStatus.disabled => 'package_disabled',
        SourceLiveCapturePlaybackRouteStatus.incompatible =>
          'incompatible_wynime_version',
        SourceLiveCapturePlaybackRouteStatus.preferredSourceNotFound =>
          'preferred_source_not_found',
        SourceLiveCapturePlaybackRouteStatus.failed => 'live_route_failed',
      };

  static String _sessionRequestReason(
    SourceLiveCapturePlaybackSessionRequestResult result,
  ) => switch (result.status) {
    SourceLiveCapturePlaybackSessionRequestStatus.ready =>
      'live_session_request_ready',
    SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected =>
      'live_route_not_selected',
    SourceLiveCapturePlaybackSessionRequestStatus.consentRequired =>
      'consent_required',
    SourceLiveCapturePlaybackSessionRequestStatus.disabled =>
      'package_disabled',
    SourceLiveCapturePlaybackSessionRequestStatus.incompatible =>
      'incompatible_wynime_version',
    SourceLiveCapturePlaybackSessionRequestStatus.failed =>
      'live_session_request_failed',
  };

  static String _openRequestReason(
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus status,
  ) => switch (status) {
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready =>
      'live_open_request_ready',
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
        .sessionRequestNotReady =>
      'live_session_request_not_ready',
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
        .invalidRefreshLeeway =>
      'invalid_refresh_leeway',
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
        .invalidAutomaticRefreshes =>
      'invalid_automatic_refreshes',
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
        .invalidEpisodeDuration =>
      'invalid_episode_duration',
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.failed =>
      'live_open_request_failed',
  };
}

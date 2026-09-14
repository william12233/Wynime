import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playable_source_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

import 'source_playable_source_coordinator.dart';
import 'source_playback_open_request_coordinator.dart';
import 'source_playback_prepared_request_opener.dart';
import 'source_playback_route_coordinator.dart';
import 'source_playback_session_request_coordinator.dart';

enum SourcePlaybackFixturePipelineStatus { opened, notOpened }

enum SourcePlaybackFixturePipelineFailureStage {
  playableSources,
  route,
  sessionRequest,
  openRequest,
  preparedOpen,
}

/// A bounded outcome for the fixture-only source playback composition.
///
/// The result exposes only the typed status of the first failed boundary. It
/// never retains a source URI, source response, request, cookie, header or raw
/// exception as diagnostic data.
final class SourcePlaybackFixturePipelineResult {
  SourcePlaybackFixturePipelineResult({
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
    final isOpened = status == SourcePlaybackFixturePipelineStatus.opened;
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
          'An opened fixture pipeline result must contain only a session.',
        );
      }
      return;
    }

    if (session != null ||
        failureStage == null ||
        stageStatusCount != 1 ||
        reasonCode == null ||
        !_isSafeToken(reasonCode!)) {
      throw ArgumentError(
        'A rejected fixture pipeline result must contain one typed stage failure.',
      );
    }

    final stageMatches = switch (failureStage!) {
      SourcePlaybackFixturePipelineFailureStage.playableSources =>
        playableStatus != null &&
            playableStatus != SourcePlayableSourceCoordinatorStatus.available &&
            playableStatus != SourcePlayableSourceCoordinatorStatus.partial,
      SourcePlaybackFixturePipelineFailureStage.route =>
        routeStatus != null &&
            routeStatus != SourcePlaybackRouteCoordinatorStatus.selected,
      SourcePlaybackFixturePipelineFailureStage.sessionRequest =>
        sessionRequestStatus != null &&
            sessionRequestStatus !=
                SourcePlaybackSessionRequestCoordinatorStatus.ready,
      SourcePlaybackFixturePipelineFailureStage.openRequest =>
        openRequestStatus != null &&
            openRequestStatus !=
                SourcePlaybackOpenRequestCoordinatorStatus.ready,
      SourcePlaybackFixturePipelineFailureStage.preparedOpen =>
        preparedOpenStatus == SourcePlaybackPreparedOpenStatus.requestNotReady,
    };
    if (!stageMatches) {
      throw ArgumentError(
        'The fixture pipeline failure stage must match its typed status.',
      );
    }
  }

  final SourcePlaybackFixturePipelineStatus status;
  final PlaybackSession? session;
  final SourcePlaybackFixturePipelineFailureStage? failureStage;
  final SourcePlayableSourceCoordinatorStatus? playableStatus;
  final SourcePlaybackRouteCoordinatorStatus? routeStatus;
  final SourcePlaybackSessionRequestCoordinatorStatus? sessionRequestStatus;
  final SourcePlaybackOpenRequestCoordinatorStatus? openRequestStatus;
  final SourcePlaybackPreparedOpenStatus? preparedOpenStatus;
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
}

/// Composes the fixture-only source path through every typed playback
/// boundary. The existing PlaybackCoordinator remains the only execution and
/// session authority, owned by [preparedOpener].
final class SourcePlaybackFixturePipeline {
  SourcePlaybackFixturePipeline({
    required this.playableSourceCoordinator,
    required this.routeCoordinator,
    required this.sessionRequestCoordinator,
    required this.openRequestCoordinator,
    required this.preparedOpener,
  });

  final SourcePlayableSourceCoordinator playableSourceCoordinator;
  final SourcePlaybackRouteCoordinator routeCoordinator;
  final SourcePlaybackSessionRequestCoordinator sessionRequestCoordinator;
  final SourcePlaybackOpenRequestCoordinator openRequestCoordinator;
  final SourcePlaybackPreparedRequestOpener preparedOpener;

  Future<SourcePlaybackFixturePipelineResult> openFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
    required SourcePlayableSourceFieldMapping mapping,
    required SourceEpisodeIdentity episode,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
    required PlaybackProxyBudget proxyBudget,
    String? preferredSourceKey,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) async {
    final SourcePlayableSourcePlan plan;
    try {
      plan = SourcePlayableSourcePlan(
        installedPackage: installedPackage,
        programId: programId,
        fixture: fixture,
        episode: episode,
        mapping: mapping,
      );
    } on Object {
      return _rejected(
        failureStage: SourcePlaybackFixturePipelineFailureStage.playableSources,
        playableStatus: SourcePlayableSourceCoordinatorStatus.failed,
        reasonCode: 'invalid_source_plan',
      );
    }

    final playableResult = playableSourceCoordinator.listPlayableSources(
      plans: [plan],
    );
    if (playableResult.status !=
            SourcePlayableSourceCoordinatorStatus.available &&
        playableResult.status !=
            SourcePlayableSourceCoordinatorStatus.partial) {
      return _rejected(
        failureStage: SourcePlaybackFixturePipelineFailureStage.playableSources,
        playableStatus: playableResult.status,
        reasonCode:
            playableResult.reasonCode ?? _playableReason(playableResult.status),
      );
    }

    SourcePlaybackRoutePreference? preference;
    if (preferredSourceKey != null) {
      try {
        preference = SourcePlaybackRoutePreference(
          packageId: installedPackage.package.packageId,
          packageVersion: installedPackage.package.version,
          programId: plan.programId,
          sourceKey: preferredSourceKey,
        );
      } on Object {
        return _rejected(
          failureStage: SourcePlaybackFixturePipelineFailureStage.route,
          routeStatus: SourcePlaybackRouteCoordinatorStatus.failed,
          reasonCode: 'invalid_preferred_source',
        );
      }
    }

    final routeResult = routeCoordinator.selectRoute(
      playableResult: playableResult,
      preference: preference,
    );
    if (routeResult.status != SourcePlaybackRouteCoordinatorStatus.selected) {
      return _rejected(
        failureStage: SourcePlaybackFixturePipelineFailureStage.route,
        routeStatus: routeResult.status,
        reasonCode: routeResult.reasonCode ?? _routeReason(routeResult.status),
      );
    }

    final sessionResult = sessionRequestCoordinator.buildRequest(
      routeResult: routeResult,
      package: installedPackage.package,
      adRemovalPlan: adRemovalPlan,
      sourceEventSequence: sourceEventSequence,
    );
    if (sessionResult.status !=
        SourcePlaybackSessionRequestCoordinatorStatus.ready) {
      return _rejected(
        failureStage: SourcePlaybackFixturePipelineFailureStage.sessionRequest,
        sessionRequestStatus: sessionResult.status,
        reasonCode:
            sessionResult.reasonCode ??
            _sessionRequestReason(sessionResult.status),
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
    if (openResult.status != SourcePlaybackOpenRequestCoordinatorStatus.ready) {
      return _rejected(
        failureStage: SourcePlaybackFixturePipelineFailureStage.openRequest,
        openRequestStatus: openResult.status,
        reasonCode:
            openResult.reasonCode ?? _openRequestReason(openResult.status),
      );
    }

    final preparedResult = await preparedOpener.openPreparedRequest(
      openResult: openResult,
    );
    if (preparedResult.status != SourcePlaybackPreparedOpenStatus.opened) {
      return _rejected(
        failureStage: SourcePlaybackFixturePipelineFailureStage.preparedOpen,
        preparedOpenStatus: preparedResult.status,
        reasonCode: preparedResult.reasonCode ?? 'prepared_open_not_ready',
      );
    }

    return SourcePlaybackFixturePipelineResult(
      status: SourcePlaybackFixturePipelineStatus.opened,
      session: preparedResult.session,
    );
  }

  static SourcePlaybackFixturePipelineResult _rejected({
    required SourcePlaybackFixturePipelineFailureStage failureStage,
    SourcePlayableSourceCoordinatorStatus? playableStatus,
    SourcePlaybackRouteCoordinatorStatus? routeStatus,
    SourcePlaybackSessionRequestCoordinatorStatus? sessionRequestStatus,
    SourcePlaybackOpenRequestCoordinatorStatus? openRequestStatus,
    SourcePlaybackPreparedOpenStatus? preparedOpenStatus,
    required String reasonCode,
  }) => SourcePlaybackFixturePipelineResult(
    status: SourcePlaybackFixturePipelineStatus.notOpened,
    failureStage: failureStage,
    playableStatus: playableStatus,
    routeStatus: routeStatus,
    sessionRequestStatus: sessionRequestStatus,
    openRequestStatus: openRequestStatus,
    preparedOpenStatus: preparedOpenStatus,
    reasonCode: reasonCode,
  );

  static String _playableReason(SourcePlayableSourceCoordinatorStatus status) {
    return switch (status) {
      SourcePlayableSourceCoordinatorStatus.available =>
        'playable_sources_available',
      SourcePlayableSourceCoordinatorStatus.partial =>
        'partial_playable_sources',
      SourcePlayableSourceCoordinatorStatus.notFound =>
        'playable_sources_not_found',
      SourcePlayableSourceCoordinatorStatus.noSources => 'no_enabled_sources',
      SourcePlayableSourceCoordinatorStatus.failed => 'playable_sources_failed',
    };
  }

  static String _routeReason(SourcePlaybackRouteCoordinatorStatus status) {
    return switch (status) {
      SourcePlaybackRouteCoordinatorStatus.selected => 'route_selected',
      SourcePlaybackRouteCoordinatorStatus.notFound => 'route_not_found',
      SourcePlaybackRouteCoordinatorStatus.noSources => 'no_enabled_sources',
      SourcePlaybackRouteCoordinatorStatus.disabled => 'route_disabled',
      SourcePlaybackRouteCoordinatorStatus.consentRequired =>
        'route_consent_required',
      SourcePlaybackRouteCoordinatorStatus.incompatible => 'route_incompatible',
      SourcePlaybackRouteCoordinatorStatus.preferredSourceNotFound =>
        'preferred_source_not_found',
      SourcePlaybackRouteCoordinatorStatus.failed => 'route_failed',
    };
  }

  static String _sessionRequestReason(
    SourcePlaybackSessionRequestCoordinatorStatus status,
  ) {
    return switch (status) {
      SourcePlaybackSessionRequestCoordinatorStatus.ready =>
        'session_request_ready',
      SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected =>
        'route_not_selected',
      SourcePlaybackSessionRequestCoordinatorStatus.requestRejected =>
        'session_request_rejected',
      SourcePlaybackSessionRequestCoordinatorStatus.failed =>
        'session_request_failed',
    };
  }

  static String _openRequestReason(
    SourcePlaybackOpenRequestCoordinatorStatus status,
  ) {
    return switch (status) {
      SourcePlaybackOpenRequestCoordinatorStatus.ready => 'open_request_ready',
      SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady =>
        'session_request_not_ready',
      SourcePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway =>
        'invalid_refresh_leeway',
      SourcePlaybackOpenRequestCoordinatorStatus.invalidAutomaticRefreshes =>
        'invalid_automatic_refreshes',
      SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration =>
        'invalid_episode_duration',
      SourcePlaybackOpenRequestCoordinatorStatus.failed =>
        'open_request_failed',
    };
  }
}

bool _isSafeToken(String value) =>
    value.isNotEmpty &&
    value.length <= 64 &&
    RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);

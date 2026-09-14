import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

import 'playback/playback_coordinator.dart';

enum SourceLivePlaybackOpenRequestCoordinatorStatus {
  ready,
  sessionRequestNotReady,
  invalidRefreshLeeway,
  invalidAutomaticRefreshes,
  invalidEpisodeDuration,
  failed,
}

/// The bounded result of composing a live session request into an open
/// request. It does not represent an opened or resolved playback session.
final class SourceLivePlaybackOpenRequestCoordinatorResult {
  SourceLivePlaybackOpenRequestCoordinatorResult({
    required this.status,
    this.request,
    this.sessionStatus,
    this.routeStatus,
    this.requestStatus,
    this.reasonCode,
  }) {
    final isReady =
        status == SourceLivePlaybackOpenRequestCoordinatorStatus.ready;
    if (isReady &&
        (request == null ||
            sessionStatus != SourceLivePlaybackSessionRequestStatus.ready ||
            routeStatus != SourceLivePlaybackRouteStatus.selected ||
            requestStatus != SourcePlaybackSessionRequestBuildStatus.ready ||
            reasonCode != null)) {
      throw ArgumentError(
        'A ready result must contain only a ready live open request.',
      );
    }
    if (!isReady && request != null) {
      throw ArgumentError('A non-ready result cannot contain an open request.');
    }
    if (status ==
            SourceLivePlaybackOpenRequestCoordinatorStatus
                .sessionRequestNotReady &&
        (sessionStatus == null ||
            sessionStatus == SourceLivePlaybackSessionRequestStatus.ready ||
            !_upstreamStatusMatches(
              sessionStatus!,
              routeStatus,
              requestStatus,
            ))) {
      throw ArgumentError(
        'A session-request rejection must retain a non-ready live state.',
      );
    }
    if (status !=
            SourceLivePlaybackOpenRequestCoordinatorStatus
                .sessionRequestNotReady &&
        (sessionStatus != SourceLivePlaybackSessionRequestStatus.ready ||
            routeStatus != SourceLivePlaybackRouteStatus.selected ||
            requestStatus != SourcePlaybackSessionRequestBuildStatus.ready)) {
      throw ArgumentError(
        'An open-request option result must follow a ready live session request.',
      );
    }
    if (!isReady && (reasonCode == null || !_safeToken(reasonCode!))) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'A non-ready result requires a bounded diagnostic token.',
      );
    }
  }

  final SourceLivePlaybackOpenRequestCoordinatorStatus status;
  final PlaybackOpenRequest? request;
  final SourceLivePlaybackSessionRequestStatus? sessionStatus;
  final SourceLivePlaybackRouteStatus? routeStatus;
  final SourcePlaybackSessionRequestBuildStatus? requestStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRequest': request != null,
    'sessionStatus': sessionStatus?.name,
    'routeStatus': routeStatus?.name,
    'requestStatus': requestStatus?.name,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);

  static bool _upstreamStatusMatches(
    SourceLivePlaybackSessionRequestStatus status,
    SourceLivePlaybackRouteStatus? routeStatus,
    SourcePlaybackSessionRequestBuildStatus? requestStatus,
  ) => switch (status) {
    SourceLivePlaybackSessionRequestStatus.routeNotSelected =>
      routeStatus != null &&
          routeStatus != SourceLivePlaybackRouteStatus.selected &&
          requestStatus == null,
    SourceLivePlaybackSessionRequestStatus.consentRequired ||
    SourceLivePlaybackSessionRequestStatus.disabled ||
    SourceLivePlaybackSessionRequestStatus.incompatible =>
      routeStatus == null && requestStatus == null,
    SourceLivePlaybackSessionRequestStatus.requestRejected =>
      routeStatus == SourceLivePlaybackRouteStatus.selected &&
          requestStatus != null &&
          requestStatus != SourcePlaybackSessionRequestBuildStatus.ready,
    SourceLivePlaybackSessionRequestStatus.failed =>
      routeStatus == SourceLivePlaybackRouteStatus.selected &&
          requestStatus != SourcePlaybackSessionRequestBuildStatus.ready,
    SourceLivePlaybackSessionRequestStatus.ready => false,
  };
}

/// Composes a ready live session request with the existing open-request
/// contract. Resolver, session, proxy and player ownership stay downstream.
final class SourceLivePlaybackOpenRequestCoordinator {
  const SourceLivePlaybackOpenRequestCoordinator();

  SourceLivePlaybackOpenRequestCoordinatorResult buildOpenRequest({
    required SourceLivePlaybackSessionRequestResult sessionResult,
    required PlaybackProxyBudget proxyBudget,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) {
    if (sessionResult.status != SourceLivePlaybackSessionRequestStatus.ready) {
      return SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: sessionResult.status,
        routeStatus: sessionResult.routeStatus,
        requestStatus: sessionResult.requestStatus,
        reasonCode: sessionResult.reasonCode ?? _sessionReason(sessionResult),
      );
    }
    if (refreshLeeway.isNegative) {
      return _optionFailure(
        SourceLivePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway,
        'invalid_refresh_leeway',
      );
    }
    if (maxAutomaticRefreshes < 0 || maxAutomaticRefreshes > 3) {
      return _optionFailure(
        SourceLivePlaybackOpenRequestCoordinatorStatus
            .invalidAutomaticRefreshes,
        'invalid_automatic_refreshes',
      );
    }
    if (episodeDuration?.isNegative == true) {
      return _optionFailure(
        SourceLivePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
        'invalid_episode_duration',
      );
    }

    try {
      return SourceLivePlaybackOpenRequestCoordinatorResult(
        status: SourceLivePlaybackOpenRequestCoordinatorStatus.ready,
        request: PlaybackOpenRequest(
          resolution: sessionResult.request!,
          proxyBudget: proxyBudget,
          addressFamily: addressFamily,
          refreshLeeway: refreshLeeway,
          maxAutomaticRefreshes: maxAutomaticRefreshes,
          episodeDuration: episodeDuration,
          bangumiEpisode: bangumiEpisode,
        ),
        sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLivePlaybackRouteStatus.selected,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
      );
    } on Object {
      return _optionFailure(
        SourceLivePlaybackOpenRequestCoordinatorStatus.failed,
        'live_open_request_build_failed',
      );
    }
  }

  static SourceLivePlaybackOpenRequestCoordinatorResult _optionFailure(
    SourceLivePlaybackOpenRequestCoordinatorStatus status,
    String reasonCode,
  ) => SourceLivePlaybackOpenRequestCoordinatorResult(
    status: status,
    sessionStatus: SourceLivePlaybackSessionRequestStatus.ready,
    routeStatus: SourceLivePlaybackRouteStatus.selected,
    requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
    reasonCode: reasonCode,
  );

  static String _sessionReason(SourceLivePlaybackSessionRequestResult result) =>
      switch (result.status) {
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
}

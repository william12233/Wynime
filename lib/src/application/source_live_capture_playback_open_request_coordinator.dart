import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_session_request_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

import 'playback/playback_coordinator.dart';

enum SourceLiveCapturePlaybackOpenRequestCoordinatorStatus {
  ready,
  sessionRequestNotReady,
  invalidRefreshLeeway,
  invalidAutomaticRefreshes,
  invalidEpisodeDuration,
  failed,
}

/// The bounded result of composing an accepted live session request into an
/// open request. It does not represent an opened or resolved playback session.
final class SourceLiveCapturePlaybackOpenRequestCoordinatorResult {
  SourceLiveCapturePlaybackOpenRequestCoordinatorResult({
    required this.status,
    this.request,
    this.sessionStatus,
    this.routeStatus,
    this.reasonCode,
  }) {
    final isReady =
        status == SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready;
    if (isReady &&
        (request == null ||
            sessionStatus !=
                SourceLiveCapturePlaybackSessionRequestStatus.ready ||
            routeStatus != SourceLiveCapturePlaybackRouteStatus.selected ||
            reasonCode != null)) {
      throw ArgumentError(
        'A ready result must contain only a ready live open request.',
      );
    }
    if (!isReady && request != null) {
      throw ArgumentError('A non-ready result cannot contain an open request.');
    }
    if (status ==
            SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
                .sessionRequestNotReady &&
        (sessionStatus == null ||
            sessionStatus ==
                SourceLiveCapturePlaybackSessionRequestStatus.ready ||
            !_sessionStatusMatches(sessionStatus!, routeStatus))) {
      throw ArgumentError(
        'A session-request rejection must retain a non-ready live state.',
      );
    }
    if (status !=
            SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
                .sessionRequestNotReady &&
        (sessionStatus != SourceLiveCapturePlaybackSessionRequestStatus.ready ||
            routeStatus != SourceLiveCapturePlaybackRouteStatus.selected)) {
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

  final SourceLiveCapturePlaybackOpenRequestCoordinatorStatus status;
  final PlaybackOpenRequest? request;
  final SourceLiveCapturePlaybackSessionRequestStatus? sessionStatus;
  final SourceLiveCapturePlaybackRouteStatus? routeStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRequest': request != null,
    'sessionStatus': sessionStatus?.name,
    'routeStatus': routeStatus?.name,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);

  static bool _sessionStatusMatches(
    SourceLiveCapturePlaybackSessionRequestStatus status,
    SourceLiveCapturePlaybackRouteStatus? routeStatus,
  ) => switch (status) {
    SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected =>
      routeStatus != null &&
          routeStatus != SourceLiveCapturePlaybackRouteStatus.selected,
    SourceLiveCapturePlaybackSessionRequestStatus.consentRequired ||
    SourceLiveCapturePlaybackSessionRequestStatus.disabled ||
    SourceLiveCapturePlaybackSessionRequestStatus.incompatible =>
      routeStatus == null ||
          routeStatus != SourceLiveCapturePlaybackRouteStatus.selected,
    SourceLiveCapturePlaybackSessionRequestStatus.failed =>
      routeStatus == SourceLiveCapturePlaybackRouteStatus.selected,
    SourceLiveCapturePlaybackSessionRequestStatus.ready => false,
  };
}

/// Composes a ready live session-request result with the existing open-request
/// contract. Resolver, session, proxy and player ownership stay downstream.
final class SourceLiveCapturePlaybackOpenRequestCoordinator {
  const SourceLiveCapturePlaybackOpenRequestCoordinator();

  SourceLiveCapturePlaybackOpenRequestCoordinatorResult buildOpenRequest({
    required SourceLiveCapturePlaybackSessionRequestResult sessionResult,
    required PlaybackProxyBudget proxyBudget,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) {
    if (sessionResult.status !=
        SourceLiveCapturePlaybackSessionRequestStatus.ready) {
      return SourceLiveCapturePlaybackOpenRequestCoordinatorResult(
        status: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .sessionRequestNotReady,
        sessionStatus: sessionResult.status,
        routeStatus: sessionResult.routeStatus,
        reasonCode: sessionResult.reasonCode ?? _sessionReason(sessionResult),
      );
    }
    if (refreshLeeway.isNegative) {
      return _optionFailure(
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .invalidRefreshLeeway,
        'invalid_refresh_leeway',
      );
    }
    if (maxAutomaticRefreshes < 0 || maxAutomaticRefreshes > 3) {
      return _optionFailure(
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .invalidAutomaticRefreshes,
        'invalid_automatic_refreshes',
      );
    }
    if (episodeDuration?.isNegative == true) {
      return _optionFailure(
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
            .invalidEpisodeDuration,
        'invalid_episode_duration',
      );
    }

    try {
      return SourceLiveCapturePlaybackOpenRequestCoordinatorResult(
        status: SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready,
        request: PlaybackOpenRequest(
          resolution: sessionResult.request!,
          proxyBudget: proxyBudget,
          addressFamily: addressFamily,
          refreshLeeway: refreshLeeway,
          maxAutomaticRefreshes: maxAutomaticRefreshes,
          episodeDuration: episodeDuration,
          bangumiEpisode: bangumiEpisode,
        ),
        sessionStatus: SourceLiveCapturePlaybackSessionRequestStatus.ready,
        routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
      );
    } on Object {
      return _optionFailure(
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.failed,
        'live_open_request_build_failed',
      );
    }
  }

  static SourceLiveCapturePlaybackOpenRequestCoordinatorResult _optionFailure(
    SourceLiveCapturePlaybackOpenRequestCoordinatorStatus status,
    String reasonCode,
  ) => SourceLiveCapturePlaybackOpenRequestCoordinatorResult(
    status: status,
    sessionStatus: SourceLiveCapturePlaybackSessionRequestStatus.ready,
    routeStatus: SourceLiveCapturePlaybackRouteStatus.selected,
    reasonCode: reasonCode,
  );

  static String _sessionReason(
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
}

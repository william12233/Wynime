import 'package:wynime/src/domain/models/bangumi_episode_target.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

import 'playback/playback_coordinator.dart';

enum SourcePlaybackOpenRequestCoordinatorStatus {
  ready,
  sessionRequestNotReady,
  invalidRefreshLeeway,
  invalidAutomaticRefreshes,
  invalidEpisodeDuration,
  failed,
}

/// The bounded result of composing a validated session request into an open
/// request. It does not represent an opened or resolved playback session.
final class SourcePlaybackOpenRequestCoordinatorResult {
  SourcePlaybackOpenRequestCoordinatorResult({
    required this.status,
    this.request,
    this.sessionStatus,
    this.requestStatus,
    this.reasonCode,
  }) {
    final isReady = status == SourcePlaybackOpenRequestCoordinatorStatus.ready;
    if (isReady &&
        (request == null ||
            sessionStatus !=
                SourcePlaybackSessionRequestCoordinatorStatus.ready ||
            requestStatus != SourcePlaybackSessionRequestBuildStatus.ready ||
            reasonCode != null)) {
      throw ArgumentError(
        'A ready result must contain only a ready open request.',
      );
    }
    if (!isReady && request != null) {
      throw ArgumentError('A non-ready result cannot contain an open request.');
    }
    if (status ==
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady &&
        (sessionStatus == null ||
            sessionStatus ==
                SourcePlaybackSessionRequestCoordinatorStatus.ready ||
            !_upstreamStatusMatches(sessionStatus!, requestStatus))) {
      throw ArgumentError(
        'A session-request rejection must contain a non-ready session state.',
      );
    }
    if (status !=
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady &&
        (sessionStatus != SourcePlaybackSessionRequestCoordinatorStatus.ready ||
            requestStatus != SourcePlaybackSessionRequestBuildStatus.ready)) {
      throw ArgumentError(
        'An open-request option result must follow a ready session request.',
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

  final SourcePlaybackOpenRequestCoordinatorStatus status;
  final PlaybackOpenRequest? request;
  final SourcePlaybackSessionRequestCoordinatorStatus? sessionStatus;
  final SourcePlaybackSessionRequestBuildStatus? requestStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRequest': request != null,
    'sessionStatus': sessionStatus?.name,
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
    SourcePlaybackSessionRequestCoordinatorStatus status,
    SourcePlaybackSessionRequestBuildStatus? requestStatus,
  ) => switch (status) {
    SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected =>
      requestStatus == null,
    SourcePlaybackSessionRequestCoordinatorStatus.requestRejected =>
      requestStatus != null &&
          requestStatus != SourcePlaybackSessionRequestBuildStatus.ready,
    SourcePlaybackSessionRequestCoordinatorStatus.failed =>
      requestStatus == null,
    SourcePlaybackSessionRequestCoordinatorStatus.ready => false,
  };
}

/// Composes a ready session-request result with the existing open-request
/// contract. Resolver, session, proxy and player ownership stay downstream.
final class SourcePlaybackOpenRequestCoordinator {
  const SourcePlaybackOpenRequestCoordinator();

  SourcePlaybackOpenRequestCoordinatorResult buildOpenRequest({
    required SourcePlaybackSessionRequestCoordinatorResult sessionResult,
    required PlaybackProxyBudget proxyBudget,
    LoopbackAddressFamily addressFamily = LoopbackAddressFamily.ipv4,
    Duration refreshLeeway = const Duration(seconds: 30),
    int maxAutomaticRefreshes = 1,
    Duration? episodeDuration,
    BangumiEpisodeTarget? bangumiEpisode,
  }) {
    if (sessionResult.status !=
        SourcePlaybackSessionRequestCoordinatorStatus.ready) {
      return SourcePlaybackOpenRequestCoordinatorResult(
        status:
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady,
        sessionStatus: sessionResult.status,
        requestStatus: sessionResult.requestStatus,
        reasonCode: sessionResult.reasonCode ?? _sessionReason(sessionResult),
      );
    }
    if (refreshLeeway.isNegative) {
      return _optionFailure(
        SourcePlaybackOpenRequestCoordinatorStatus.invalidRefreshLeeway,
        'invalid_refresh_leeway',
      );
    }
    if (maxAutomaticRefreshes < 0 || maxAutomaticRefreshes > 3) {
      return _optionFailure(
        SourcePlaybackOpenRequestCoordinatorStatus.invalidAutomaticRefreshes,
        'invalid_automatic_refreshes',
      );
    }
    if (episodeDuration?.isNegative == true) {
      return _optionFailure(
        SourcePlaybackOpenRequestCoordinatorStatus.invalidEpisodeDuration,
        'invalid_episode_duration',
      );
    }

    try {
      return SourcePlaybackOpenRequestCoordinatorResult(
        status: SourcePlaybackOpenRequestCoordinatorStatus.ready,
        request: PlaybackOpenRequest(
          resolution: sessionResult.request!,
          proxyBudget: proxyBudget,
          addressFamily: addressFamily,
          refreshLeeway: refreshLeeway,
          maxAutomaticRefreshes: maxAutomaticRefreshes,
          episodeDuration: episodeDuration,
          bangumiEpisode: bangumiEpisode,
        ),
        sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
        requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
      );
    } on Object {
      return _optionFailure(
        SourcePlaybackOpenRequestCoordinatorStatus.failed,
        'open_request_build_failed',
      );
    }
  }

  static SourcePlaybackOpenRequestCoordinatorResult _optionFailure(
    SourcePlaybackOpenRequestCoordinatorStatus status,
    String reasonCode,
  ) => SourcePlaybackOpenRequestCoordinatorResult(
    status: status,
    sessionStatus: SourcePlaybackSessionRequestCoordinatorStatus.ready,
    requestStatus: SourcePlaybackSessionRequestBuildStatus.ready,
    reasonCode: reasonCode,
  );

  static String _sessionReason(
    SourcePlaybackSessionRequestCoordinatorResult result,
  ) {
    return switch (result.status) {
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
}

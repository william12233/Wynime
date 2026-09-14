import 'package:wynime/src/domain/models/source_playback_route_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';

enum SourcePlaybackSessionRequestCoordinatorStatus {
  ready,
  routeNotSelected,
  requestRejected,
  failed,
}

/// The bounded result of composing a selected route into a resolver request.
final class SourcePlaybackSessionRequestCoordinatorResult {
  SourcePlaybackSessionRequestCoordinatorResult({
    required this.status,
    this.request,
    this.routeStatus,
    this.requestStatus,
    this.reasonCode,
  }) {
    final isReady =
        status == SourcePlaybackSessionRequestCoordinatorStatus.ready;
    if (isReady &&
        (request == null ||
            routeStatus != SourcePlaybackRouteCoordinatorStatus.selected ||
            requestStatus != SourcePlaybackSessionRequestBuildStatus.ready ||
            reasonCode != null)) {
      throw ArgumentError(
        'A ready result must contain only a ready session request.',
      );
    }
    if (!isReady && request != null) {
      throw ArgumentError(
        'A non-ready result cannot contain a session request.',
      );
    }
    if (status ==
            SourcePlaybackSessionRequestCoordinatorStatus.routeNotSelected &&
        (routeStatus == null ||
            routeStatus == SourcePlaybackRouteCoordinatorStatus.selected ||
            requestStatus != null)) {
      throw ArgumentError(
        'A route-not-selected result must contain a non-selected route state.',
      );
    }
    if (status ==
            SourcePlaybackSessionRequestCoordinatorStatus.requestRejected &&
        (routeStatus != SourcePlaybackRouteCoordinatorStatus.selected ||
            requestStatus == null ||
            requestStatus == SourcePlaybackSessionRequestBuildStatus.ready)) {
      throw ArgumentError(
        'A request-rejected result must contain a non-ready build status.',
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

  final SourcePlaybackSessionRequestCoordinatorStatus status;
  final PlaybackSessionResolutionRequest? request;
  final SourcePlaybackRouteCoordinatorStatus? routeStatus;
  final SourcePlaybackSessionRequestBuildStatus? requestStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRequest': request != null,
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
}

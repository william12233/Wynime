import 'source_live_playback_route_models.dart';
import 'source_playback_session_request_models.dart';
import '../services/playback_session_resolver.dart';

enum SourceLivePlaybackSessionRequestStatus {
  ready,
  routeNotSelected,
  consentRequired,
  disabled,
  incompatible,
  requestRejected,
  failed,
}

/// The bounded result of handing a selected live HTTP route to the existing
/// playback-session resolver contract.
final class SourceLivePlaybackSessionRequestResult {
  SourceLivePlaybackSessionRequestResult({
    required this.status,
    this.request,
    this.routeStatus,
    this.requestStatus,
    this.reasonCode,
  }) {
    final isReady = status == SourceLivePlaybackSessionRequestStatus.ready;
    if (isReady &&
        (request == null ||
            routeStatus != SourceLivePlaybackRouteStatus.selected ||
            requestStatus != SourcePlaybackSessionRequestBuildStatus.ready ||
            reasonCode != null)) {
      throw ArgumentError(
        'A ready result must contain only a ready resolver request.',
      );
    }
    if (!isReady && request != null) {
      throw ArgumentError(
        'A non-ready result cannot contain a resolver request.',
      );
    }
    if (status == SourceLivePlaybackSessionRequestStatus.routeNotSelected &&
        (routeStatus == null ||
            routeStatus == SourceLivePlaybackRouteStatus.selected ||
            requestStatus != null)) {
      throw ArgumentError(
        'A route-not-selected result must contain a non-selected route state.',
      );
    }
    if (status == SourceLivePlaybackSessionRequestStatus.requestRejected &&
        (routeStatus != SourceLivePlaybackRouteStatus.selected ||
            requestStatus == null ||
            requestStatus == SourcePlaybackSessionRequestBuildStatus.ready)) {
      throw ArgumentError(
        'A request rejection must retain the selected route and build status.',
      );
    }
    if (status == SourceLivePlaybackSessionRequestStatus.failed &&
        (routeStatus != SourceLivePlaybackRouteStatus.selected ||
            requestStatus == SourcePlaybackSessionRequestBuildStatus.ready)) {
      throw ArgumentError(
        'A failed live session request must retain the selected route state.',
      );
    }
    if (status == SourceLivePlaybackSessionRequestStatus.consentRequired ||
        status == SourceLivePlaybackSessionRequestStatus.disabled ||
        status == SourceLivePlaybackSessionRequestStatus.incompatible) {
      if (routeStatus != null || requestStatus != null) {
        throw ArgumentError(
          'A package lifecycle block must not contain route or builder state.',
        );
      }
    }
    if (!isReady && (reasonCode == null || !_safeToken(reasonCode!))) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'A non-ready result requires a bounded diagnostic token.',
      );
    }
  }

  final SourceLivePlaybackSessionRequestStatus status;
  final PlaybackSessionResolutionRequest? request;
  final SourceLivePlaybackRouteStatus? routeStatus;
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

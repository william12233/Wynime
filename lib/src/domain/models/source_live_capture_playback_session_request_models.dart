import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';

enum SourceLiveCapturePlaybackSessionRequestStatus {
  ready,
  routeNotSelected,
  consentRequired,
  disabled,
  incompatible,
  failed,
}

/// The bounded result of handing an accepted live route to the existing
/// playback-session resolver contract.
final class SourceLiveCapturePlaybackSessionRequestResult {
  SourceLiveCapturePlaybackSessionRequestResult({
    required this.status,
    this.request,
    this.routeStatus,
    this.reasonCode,
  }) {
    final isReady =
        status == SourceLiveCapturePlaybackSessionRequestStatus.ready;
    if (isReady &&
        (request == null ||
            routeStatus != SourceLiveCapturePlaybackRouteStatus.selected ||
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
    if (status ==
            SourceLiveCapturePlaybackSessionRequestStatus.routeNotSelected &&
        (routeStatus == null ||
            routeStatus == SourceLiveCapturePlaybackRouteStatus.selected)) {
      throw ArgumentError(
        'A route-not-selected result must contain a non-selected route state.',
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

  final SourceLiveCapturePlaybackSessionRequestStatus status;
  final PlaybackSessionResolutionRequest? request;
  final SourceLiveCapturePlaybackRouteStatus? routeStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasRequest': request != null,
    'routeStatus': routeStatus?.name,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_playback_session_request_models.dart';

import 'playback/playback_coordinator.dart';
import 'source_live_capture_playback_open_request_coordinator.dart';

enum SourceLiveCapturePlaybackPreparedOpenStatus { opened, requestNotReady }

/// The bounded result of passing a prepared live open request to the existing
/// playback coordinator.
final class SourceLiveCapturePlaybackPreparedOpenResult {
  SourceLiveCapturePlaybackPreparedOpenResult({
    required this.status,
    this.session,
    this.openRequestStatus,
    this.sessionStatus,
    this.routeStatus,
    this.reasonCode,
  }) {
    final isOpened =
        status == SourceLiveCapturePlaybackPreparedOpenStatus.opened;
    if (isOpened &&
        (session == null ||
            openRequestStatus != null ||
            sessionStatus != null ||
            routeStatus != null ||
            reasonCode != null)) {
      throw ArgumentError('An opened result must contain only a session.');
    }
    if (!isOpened && session != null) {
      throw ArgumentError('A not-ready result cannot contain a session.');
    }
    if (!isOpened &&
        (openRequestStatus == null ||
            openRequestStatus ==
                SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready ||
            reasonCode == null ||
            !_safeToken(reasonCode!))) {
      throw ArgumentError(
        'A not-ready result requires a bounded non-ready request status.',
      );
    }
    if (!isOpened &&
        openRequestStatus ==
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
    if (!isOpened &&
        openRequestStatus !=
            SourceLiveCapturePlaybackOpenRequestCoordinatorStatus
                .sessionRequestNotReady &&
        (sessionStatus != SourceLiveCapturePlaybackSessionRequestStatus.ready ||
            routeStatus != SourceLiveCapturePlaybackRouteStatus.selected)) {
      throw ArgumentError(
        'An option rejection must retain a ready live session state.',
      );
    }
  }

  final SourceLiveCapturePlaybackPreparedOpenStatus status;
  final PlaybackSession? session;
  final SourceLiveCapturePlaybackOpenRequestCoordinatorStatus?
  openRequestStatus;
  final SourceLiveCapturePlaybackSessionRequestStatus? sessionStatus;
  final SourceLiveCapturePlaybackRouteStatus? routeStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasSession': session != null,
    'openRequestStatus': openRequestStatus?.name,
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

/// Opens only a ready live request through the existing PlaybackCoordinator.
/// Session, proxy, player, progress and generation ownership stay there.
abstract interface class SourceLiveCapturePlaybackPreparedRequestOpener {
  Future<SourceLiveCapturePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLiveCapturePlaybackOpenRequestCoordinatorResult openResult,
  });
}

/// The live prepared-request handoff implementation. It adds no second
/// playback lifecycle or error boundary.
final class PlaybackCoordinatorLiveCapturePreparedRequestOpener
    implements SourceLiveCapturePlaybackPreparedRequestOpener {
  const PlaybackCoordinatorLiveCapturePreparedRequestOpener({
    required this.coordinator,
  });

  final PlaybackCoordinator coordinator;

  @override
  Future<SourceLiveCapturePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLiveCapturePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    if (openResult.status !=
        SourceLiveCapturePlaybackOpenRequestCoordinatorStatus.ready) {
      return SourceLiveCapturePlaybackPreparedOpenResult(
        status: SourceLiveCapturePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: openResult.status,
        sessionStatus: openResult.sessionStatus,
        routeStatus: openResult.routeStatus,
        reasonCode: openResult.reasonCode ?? 'live_open_request_not_ready',
      );
    }

    final session = await coordinator.open(openResult.request!);
    return SourceLiveCapturePlaybackPreparedOpenResult(
      status: SourceLiveCapturePlaybackPreparedOpenStatus.opened,
      session: session,
    );
  }
}

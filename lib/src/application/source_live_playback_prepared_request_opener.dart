import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_live_playback_route_models.dart';
import 'package:wynime/src/domain/models/source_live_playback_session_request_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';

import 'playback/playback_coordinator.dart';
import 'source_live_playback_open_request_coordinator.dart';

enum SourceLivePlaybackPreparedOpenStatus { opened, requestNotReady }

/// The bounded result of passing a live HTTP open request to the existing
/// playback coordinator.
final class SourceLivePlaybackPreparedOpenResult {
  SourceLivePlaybackPreparedOpenResult({
    required this.status,
    this.session,
    this.openRequestStatus,
    this.sessionStatus,
    this.routeStatus,
    this.requestStatus,
    this.reasonCode,
  }) {
    final isOpened = status == SourceLivePlaybackPreparedOpenStatus.opened;
    if (isOpened &&
        (session == null ||
            openRequestStatus != null ||
            sessionStatus != null ||
            routeStatus != null ||
            requestStatus != null ||
            reasonCode != null)) {
      throw ArgumentError('An opened result must contain only a session.');
    }
    if (!isOpened && session != null) {
      throw ArgumentError('A not-ready result cannot contain a session.');
    }
    if (!isOpened &&
        (openRequestStatus == null ||
            openRequestStatus ==
                SourceLivePlaybackOpenRequestCoordinatorStatus.ready ||
            reasonCode == null ||
            !_safeToken(reasonCode!))) {
      throw ArgumentError(
        'A not-ready result requires a bounded non-ready request status.',
      );
    }
    if (!isOpened &&
        openRequestStatus ==
            SourceLivePlaybackOpenRequestCoordinatorStatus
                .sessionRequestNotReady &&
        (sessionStatus == null ||
            sessionStatus == SourceLivePlaybackSessionRequestStatus.ready ||
            !_sessionStatusMatches(
              sessionStatus!,
              routeStatus,
              requestStatus,
            ))) {
      throw ArgumentError(
        'A session-request rejection must retain a non-ready live state.',
      );
    }
    if (!isOpened &&
        openRequestStatus !=
            SourceLivePlaybackOpenRequestCoordinatorStatus
                .sessionRequestNotReady &&
        (sessionStatus != SourceLivePlaybackSessionRequestStatus.ready ||
            routeStatus != SourceLivePlaybackRouteStatus.selected ||
            requestStatus != SourcePlaybackSessionRequestBuildStatus.ready)) {
      throw ArgumentError(
        'An option rejection must retain a ready live session-request state.',
      );
    }
  }

  final SourceLivePlaybackPreparedOpenStatus status;
  final PlaybackSession? session;
  final SourceLivePlaybackOpenRequestCoordinatorStatus? openRequestStatus;
  final SourceLivePlaybackSessionRequestStatus? sessionStatus;
  final SourceLivePlaybackRouteStatus? routeStatus;
  final SourcePlaybackSessionRequestBuildStatus? requestStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasSession': session != null,
    'openRequestStatus': openRequestStatus?.name,
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

  static bool _sessionStatusMatches(
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

/// The prepared-request handoff port for live HTTP composition.
///
/// Only a ready request may reach the existing playback coordinator. Session,
/// proxy, player, progress and stale-operation ownership stay there.
abstract interface class SourceLivePlaybackPreparedRequestOpener {
  Future<SourceLivePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLivePlaybackOpenRequestCoordinatorResult openResult,
  });
}

/// Opens only a ready live HTTP request through the existing
/// `PlaybackCoordinator`.
final class PlaybackCoordinatorLivePlaybackPreparedRequestOpener
    implements SourceLivePlaybackPreparedRequestOpener {
  const PlaybackCoordinatorLivePlaybackPreparedRequestOpener({
    required this.coordinator,
  });

  final PlaybackCoordinator coordinator;

  @override
  Future<SourceLivePlaybackPreparedOpenResult> openPreparedRequest({
    required SourceLivePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    if (openResult.status !=
        SourceLivePlaybackOpenRequestCoordinatorStatus.ready) {
      return SourceLivePlaybackPreparedOpenResult(
        status: SourceLivePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: openResult.status,
        sessionStatus: openResult.sessionStatus,
        routeStatus: openResult.routeStatus,
        requestStatus: openResult.requestStatus,
        reasonCode: openResult.reasonCode ?? 'live_open_request_not_ready',
      );
    }

    final session = await coordinator.open(openResult.request!);
    return SourceLivePlaybackPreparedOpenResult(
      status: SourceLivePlaybackPreparedOpenStatus.opened,
      session: session,
    );
  }
}

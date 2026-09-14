import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_coordinator_models.dart';
import 'package:wynime/src/domain/models/source_playback_session_request_models.dart';

import 'playback/playback_coordinator.dart';
import 'source_playback_open_request_coordinator.dart';

enum SourcePlaybackPreparedOpenStatus { opened, requestNotReady }

/// The bounded result of passing a prepared open request to the existing
/// playback coordinator.
final class SourcePlaybackPreparedOpenResult {
  SourcePlaybackPreparedOpenResult({
    required this.status,
    this.session,
    this.openRequestStatus,
    this.sessionStatus,
    this.sessionRequestStatus,
    this.reasonCode,
  }) {
    final isOpened = status == SourcePlaybackPreparedOpenStatus.opened;
    if (isOpened &&
        (session == null ||
            openRequestStatus != null ||
            sessionStatus != null ||
            sessionRequestStatus != null ||
            reasonCode != null)) {
      throw ArgumentError('An opened result must contain only a session.');
    }
    if (!isOpened && session != null) {
      throw ArgumentError('A not-ready result cannot contain a session.');
    }
    if (!isOpened &&
        (openRequestStatus == null ||
            openRequestStatus ==
                SourcePlaybackOpenRequestCoordinatorStatus.ready ||
            reasonCode == null ||
            !_safeToken(reasonCode!))) {
      throw ArgumentError(
        'A not-ready result requires a bounded non-ready request status.',
      );
    }
    if (!isOpened &&
        openRequestStatus ==
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady &&
        (sessionStatus == null ||
            sessionStatus ==
                SourcePlaybackSessionRequestCoordinatorStatus.ready ||
            !_sessionStatusMatches(sessionStatus!, sessionRequestStatus))) {
      throw ArgumentError(
        'A session-request rejection must retain a non-ready session status.',
      );
    }
    if (!isOpened &&
        openRequestStatus !=
            SourcePlaybackOpenRequestCoordinatorStatus.sessionRequestNotReady &&
        (sessionStatus != SourcePlaybackSessionRequestCoordinatorStatus.ready ||
            sessionRequestStatus !=
                SourcePlaybackSessionRequestBuildStatus.ready)) {
      throw ArgumentError(
        'An option rejection must retain a ready session-request status.',
      );
    }
  }

  final SourcePlaybackPreparedOpenStatus status;
  final PlaybackSession? session;
  final SourcePlaybackOpenRequestCoordinatorStatus? openRequestStatus;
  final SourcePlaybackSessionRequestCoordinatorStatus? sessionStatus;
  final SourcePlaybackSessionRequestBuildStatus? sessionRequestStatus;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'status': status.name,
    'hasSession': session != null,
    'openRequestStatus': openRequestStatus?.name,
    'sessionStatus': sessionStatus?.name,
    'sessionRequestStatus': sessionRequestStatus?.name,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);

  static bool _sessionStatusMatches(
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

/// The prepared-request handoff port used by fixture composition.
///
/// Implementations must pass a ready request to the existing playback
/// coordinator and must not create a second session lifecycle.
abstract interface class SourcePlaybackPreparedRequestOpener {
  Future<SourcePlaybackPreparedOpenResult> openPreparedRequest({
    required SourcePlaybackOpenRequestCoordinatorResult openResult,
  });
}

/// Opens only a prepared request through the existing PlaybackCoordinator.
/// Session, proxy, player, progress and generation ownership stay there.
final class PlaybackCoordinatorPreparedRequestOpener
    implements SourcePlaybackPreparedRequestOpener {
  const PlaybackCoordinatorPreparedRequestOpener({required this.coordinator});

  final PlaybackCoordinator coordinator;

  @override
  Future<SourcePlaybackPreparedOpenResult> openPreparedRequest({
    required SourcePlaybackOpenRequestCoordinatorResult openResult,
  }) async {
    if (openResult.status != SourcePlaybackOpenRequestCoordinatorStatus.ready) {
      return SourcePlaybackPreparedOpenResult(
        status: SourcePlaybackPreparedOpenStatus.requestNotReady,
        openRequestStatus: openResult.status,
        sessionStatus: openResult.sessionStatus,
        sessionRequestStatus: openResult.requestStatus,
        reasonCode: openResult.reasonCode ?? 'open_request_not_ready',
      );
    }

    final session = await coordinator.open(openResult.request!);
    return SourcePlaybackPreparedOpenResult(
      status: SourcePlaybackPreparedOpenStatus.opened,
      session: session,
    );
  }
}

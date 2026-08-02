enum PlaybackState {
  idle,
  opening,
  buffering,
  ready,
  playing,
  paused,
  ended,
  closed,
  failed,
}

enum PlaybackFailureKind {
  authorization,
  sessionExpired,
  network,
  manifest,
  decoder,
  renderer,
  sourceUnavailable,
  unsupported,
  cancelled,
  unknown,
}

enum PlaybackErrorStage { resolve, proxy, manifest, player, renderer }

final class PlaybackFailure {
  PlaybackFailure({
    required String code,
    required this.kind,
    required this.stage,
    required this.retryable,
    required this.shouldRefreshSession,
    this.httpStatus,
  }) : code = _requiredCode(code) {
    final status = httpStatus;
    if (status != null && (status < 100 || status > 599)) {
      throw ArgumentError.value(
        status,
        'httpStatus',
        'Must be a valid HTTP status.',
      );
    }
    if (shouldRefreshSession &&
        kind != PlaybackFailureKind.authorization &&
        kind != PlaybackFailureKind.sessionExpired) {
      throw ArgumentError(
        'Only authorization or expiry failures may request session refresh.',
      );
    }
  }

  final String code;
  final PlaybackFailureKind kind;
  final PlaybackErrorStage stage;
  final bool retryable;
  final bool shouldRefreshSession;
  final int? httpStatus;

  Map<String, Object?> toDiagnostic() => {
    'code': code,
    'kind': kind.name,
    'stage': stage.name,
    'retryable': retryable,
    'shouldRefreshSession': shouldRefreshSession,
    'httpStatus': httpStatus,
  };

  @override
  String toString() => toDiagnostic().toString();
}

final class PlaybackEvent {
  PlaybackEvent({
    required this.sequence,
    required this.state,
    this.position = Duration.zero,
    this.bufferedPosition = Duration.zero,
    this.failure,
  }) {
    if (sequence < 0) {
      throw ArgumentError.value(sequence, 'sequence', 'Must not be negative.');
    }
    if (position.isNegative || bufferedPosition.isNegative) {
      throw ArgumentError('Playback positions must not be negative.');
    }
    if (state == PlaybackState.failed && failure == null) {
      throw ArgumentError('A failed playback event requires a failure.');
    }
    if (state != PlaybackState.failed && failure != null) {
      throw ArgumentError('Only failed playback events may contain a failure.');
    }
  }

  final int sequence;
  final PlaybackState state;
  final Duration position;
  final Duration bufferedPosition;
  final PlaybackFailure? failure;
}

String _requiredCode(String value) {
  final normalized = value.trim().toLowerCase();
  if (normalized.isEmpty ||
      normalized.length > 96 ||
      !RegExp(r'^[a-z0-9][a-z0-9._-]*$').hasMatch(normalized)) {
    throw ArgumentError.value(value, 'code', 'Invalid diagnostic code.');
  }
  return normalized;
}

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
    this.volume,
    this.rate,
    this.audioTrackId,
    this.subtitleTrackId,
    this.timelineMapIdentity,
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
    final eventVolume = volume;
    if (eventVolume != null &&
        (!eventVolume.isFinite || eventVolume < 0 || eventVolume > 1)) {
      throw ArgumentError.value(
        eventVolume,
        'volume',
        'Must be between 0 and 1.',
      );
    }
    final eventRate = rate;
    if (eventRate != null &&
        (!eventRate.isFinite || eventRate < 0.25 || eventRate > 4)) {
      throw ArgumentError.value(
        eventRate,
        'rate',
        'Must be between 0.25 and 4.',
      );
    }
    _optionalEventText(audioTrackId, 'audioTrackId', 256);
    _optionalEventText(subtitleTrackId, 'subtitleTrackId', 256);
    _optionalEventText(timelineMapIdentity, 'timelineMapIdentity', 1024);
  }

  final int sequence;
  final PlaybackState state;
  final Duration position;
  final Duration bufferedPosition;
  final PlaybackFailure? failure;
  final double? volume;
  final double? rate;
  final String? audioTrackId;
  final String? subtitleTrackId;
  final String? timelineMapIdentity;
}

void _optionalEventText(String? value, String name, int maxLength) {
  if (value == null) {
    return;
  }
  final normalized = value.trim();
  if (normalized.isEmpty ||
      normalized.length > maxLength ||
      normalized.codeUnits.any((unit) => unit < 0x20 || unit == 0x7f)) {
    throw ArgumentError.value(value, name, 'Invalid event value.');
  }
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

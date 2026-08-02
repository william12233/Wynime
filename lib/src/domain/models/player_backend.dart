enum PlayerBackendAvailabilityStatus { available, unavailable, probeFailed }

final class PlayerBackendAvailability {
  const PlayerBackendAvailability({
    required this.status,
    required this.backendId,
    this.reasonCode,
  }) : assert(backendId != ''),
       assert(
         status != PlayerBackendAvailabilityStatus.available ||
             reasonCode == null,
       );

  const PlayerBackendAvailability.available(String backendId)
    : this(
        status: PlayerBackendAvailabilityStatus.available,
        backendId: backendId,
      );

  const PlayerBackendAvailability.unavailable(
    String backendId, {
    required String reasonCode,
  }) : this(
         status: PlayerBackendAvailabilityStatus.unavailable,
         backendId: backendId,
         reasonCode: reasonCode,
       );

  const PlayerBackendAvailability.probeFailed(
    String backendId, {
    required String reasonCode,
  }) : this(
         status: PlayerBackendAvailabilityStatus.probeFailed,
         backendId: backendId,
         reasonCode: reasonCode,
       );

  final PlayerBackendAvailabilityStatus status;
  final String backendId;
  final String? reasonCode;

  bool get isAvailable => status == PlayerBackendAvailabilityStatus.available;
}

final class PlayerTrackSelection {
  const PlayerTrackSelection.unset() : isSpecified = false, id = null;

  const PlayerTrackSelection.disabled() : isSpecified = true, id = null;

  PlayerTrackSelection.selected(String value)
    : isSpecified = true,
      id = _requiredIdentifier(value, 'trackId');

  final bool isSpecified;
  final String? id;

  bool get isDisabled => isSpecified && id == null;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is PlayerTrackSelection &&
          isSpecified == other.isSpecified &&
          id == other.id;

  @override
  int get hashCode => Object.hash(isSpecified, id);
}

final class PlayerPlaybackState {
  PlayerPlaybackState({
    this.position = Duration.zero,
    this.bufferedPosition = Duration.zero,
    this.isPlaying = true,
    this.volume = 1,
    this.rate = 1,
    this.audioTrack = const PlayerTrackSelection.unset(),
    this.subtitleTrack = const PlayerTrackSelection.unset(),
    String? timelineMapIdentity,
  }) : timelineMapIdentity = _optionalIdentity(timelineMapIdentity) {
    if (position.isNegative || bufferedPosition.isNegative) {
      throw ArgumentError('Playback positions must not be negative.');
    }
    if (!volume.isFinite || volume < 0 || volume > 1) {
      throw ArgumentError.value(volume, 'volume', 'Must be between 0 and 1.');
    }
    if (!rate.isFinite || rate < 0.25 || rate > 4) {
      throw ArgumentError.value(rate, 'rate', 'Must be between 0.25 and 4.');
    }
  }

  final Duration position;
  final Duration bufferedPosition;
  final bool isPlaying;
  final double volume;
  final double rate;
  final PlayerTrackSelection audioTrack;
  final PlayerTrackSelection subtitleTrack;
  final String? timelineMapIdentity;

  PlayerPlaybackState copyWith({
    Duration? position,
    Duration? bufferedPosition,
    bool? isPlaying,
    double? volume,
    double? rate,
    PlayerTrackSelection? audioTrack,
    PlayerTrackSelection? subtitleTrack,
    Object? timelineMapIdentity = _notProvided,
  }) => PlayerPlaybackState(
    position: position ?? this.position,
    bufferedPosition: bufferedPosition ?? this.bufferedPosition,
    isPlaying: isPlaying ?? this.isPlaying,
    volume: volume ?? this.volume,
    rate: rate ?? this.rate,
    audioTrack: audioTrack ?? this.audioTrack,
    subtitleTrack: subtitleTrack ?? this.subtitleTrack,
    timelineMapIdentity: identical(timelineMapIdentity, _notProvided)
        ? this.timelineMapIdentity
        : timelineMapIdentity as String?,
  );
}

const Object _notProvided = Object();

String _requiredIdentifier(String value, String name) {
  final normalized = value.trim();
  if (normalized.isEmpty ||
      normalized.length > 256 ||
      normalized.codeUnits.any((unit) => unit < 0x20 || unit == 0x7f)) {
    throw ArgumentError.value(value, name, 'Invalid track identifier.');
  }
  return normalized;
}

String? _optionalIdentity(String? value) {
  if (value == null) {
    return null;
  }
  final normalized = value.trim();
  if (normalized.isEmpty ||
      normalized.length > 1024 ||
      normalized.codeUnits.any((unit) => unit < 0x20 || unit == 0x7f)) {
    throw ArgumentError.value(
      value,
      'timelineMapIdentity',
      'Invalid timeline map identity.',
    );
  }
  return normalized;
}

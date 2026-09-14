final class WatchProgress {
  WatchProgress({
    required this.progressId,
    required this.sourceId,
    required this.lineId,
    required this.subjectId,
    required this.episodeId,
    required this.position,
    required this.duration,
    required this.isCompleted,
    required this.updatedAt,
    this.playerBackendId,
    this.timelineMapId,
  }) {
    _requireNonEmpty(progressId, 'progressId');
    _requireNonEmpty(sourceId, 'sourceId');
    _requireNonEmpty(lineId, 'lineId');
    _requireNonEmpty(subjectId, 'subjectId');
    _requireNonEmpty(episodeId, 'episodeId');
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
    if (duration.isNegative) {
      throw ArgumentError.value(duration, 'duration', 'Must not be negative.');
    }
    if (duration != Duration.zero && position > duration) {
      throw ArgumentError.value(
        position,
        'position',
        'Must not exceed a known duration.',
      );
    }
  }

  /// Creates a valid progress value from a persisted or external snapshot.
  ///
  /// Database rows are expected to be valid, but this boundary is deliberately
  /// tolerant of old/corrupt position values so opening Continue Watching can
  /// never crash because a position was negative or beyond a known duration.
  factory WatchProgress.sanitized({
    required String progressId,
    required String sourceId,
    required String lineId,
    required String subjectId,
    required String episodeId,
    required Duration position,
    required Duration duration,
    required bool isCompleted,
    required DateTime updatedAt,
    String? playerBackendId,
    String? timelineMapId,
  }) {
    final safeDuration = duration.isNegative ? Duration.zero : duration;
    final safePosition = position.isNegative
        ? Duration.zero
        : safeDuration == Duration.zero || position <= safeDuration
        ? position
        : safeDuration;
    return WatchProgress(
      progressId: progressId,
      sourceId: sourceId,
      lineId: lineId,
      subjectId: subjectId,
      episodeId: episodeId,
      position: safePosition,
      duration: safeDuration,
      isCompleted: isCompleted,
      updatedAt: updatedAt,
      playerBackendId: playerBackendId,
      timelineMapId: timelineMapId,
    );
  }

  final String progressId;
  final String sourceId;
  final String lineId;
  final String subjectId;
  final String episodeId;
  final Duration position;
  final Duration duration;
  final bool isCompleted;
  final DateTime updatedAt;
  final String? playerBackendId;
  final String? timelineMapId;

  static void _requireNonEmpty(String value, String name) {
    if (value.trim().isEmpty) {
      throw ArgumentError.value(value, name, 'Must not be empty.');
    }
  }
}

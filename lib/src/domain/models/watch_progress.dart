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

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
  }) : assert(progressId.trim().isNotEmpty, 'progressId must not be empty.'),
       assert(sourceId.trim().isNotEmpty, 'sourceId must not be empty.'),
       assert(lineId.trim().isNotEmpty, 'lineId must not be empty.'),
       assert(subjectId.trim().isNotEmpty, 'subjectId must not be empty.'),
       assert(episodeId.trim().isNotEmpty, 'episodeId must not be empty.'),
       assert(!position.isNegative, 'position must not be negative.'),
       assert(!duration.isNegative, 'duration must not be negative.'),
       assert(
         duration == Duration.zero || position <= duration,
         'position must not exceed a known duration.',
       );

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
}

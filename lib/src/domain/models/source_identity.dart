final class SourceEpisodeIdentity {
  SourceEpisodeIdentity({
    required this.sourceId,
    required this.lineId,
    required this.subjectId,
    required this.episodeId,
  }) : assert(sourceId.trim().isNotEmpty, 'sourceId must not be empty.'),
       assert(lineId.trim().isNotEmpty, 'lineId must not be empty.'),
       assert(subjectId.trim().isNotEmpty, 'subjectId must not be empty.'),
       assert(episodeId.trim().isNotEmpty, 'episodeId must not be empty.');

  final String sourceId;
  final String lineId;
  final String subjectId;
  final String episodeId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SourceEpisodeIdentity &&
          sourceId == other.sourceId &&
          lineId == other.lineId &&
          subjectId == other.subjectId &&
          episodeId == other.episodeId;

  @override
  int get hashCode => Object.hash(sourceId, lineId, subjectId, episodeId);
}

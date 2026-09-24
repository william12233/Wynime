import 'source_models.dart';

enum EpisodeNumberingMode {
  exact,
  seasonRelative,
  cumulativeAbsolute,
  inferredOffset,
  manual,
}

enum EpisodeMappingStatus { automatic, selectionRequired, notFound }

final class EpisodeMappingEvidence {
  const EpisodeMappingEvidence({
    required this.code,
    this.offset,
    this.alignedEpisodeCount,
  });

  final String code;
  final double? offset;
  final int? alignedEpisodeCount;
}

/// A formal, provenance-safe mapping between a Bangumi ordinal and one source
/// episode. It contains no URI, cookie, header, token, or response body.
final class EpisodeMapping {
  const EpisodeMapping({
    required this.sourceEpisode,
    required this.bangumiSort,
    required this.sourceNumber,
    required this.numberingMode,
    required this.seasonRelativeNumber,
    required this.absoluteNumber,
    required this.offset,
    required this.evidence,
  });

  final SourceEpisode sourceEpisode;
  final double bangumiSort;
  final double sourceNumber;
  final EpisodeNumberingMode numberingMode;
  final double? seasonRelativeNumber;
  final double? absoluteNumber;
  final double? offset;
  final List<EpisodeMappingEvidence> evidence;
}

final class EpisodeMappingResult {
  const EpisodeMappingResult({
    required this.status,
    this.selected,
    this.candidates = const [],
    this.reason,
  });

  final EpisodeMappingStatus status;
  final EpisodeMapping? selected;
  final List<EpisodeMapping> candidates;
  final String? reason;
}

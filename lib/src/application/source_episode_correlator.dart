import '../domain/models/bangumi_episode_type.dart';
import '../domain/models/bangumi_models.dart';
import '../domain/models/source_identity.dart';
import '../domain/models/source_models.dart';
import 'source_episode_ordinal.dart';

enum SourceEpisodeCorrelationStatus { automatic, selectionRequired, notFound }

final class SourceEpisodeCorrelationResult {
  const SourceEpisodeCorrelationResult({
    required this.status,
    required this.candidates,
    this.selected,
    this.reason,
  });

  final SourceEpisodeCorrelationStatus status;
  final List<SourceEpisode> candidates;
  final SourceEpisodeIdentity? selected;
  final String? reason;
}

/// Correlates only main-story Bangumi episodes to one exact source ordinal.
/// Every other case is explicit-selection work.
final class SourceEpisodeCorrelator {
  const SourceEpisodeCorrelator();

  SourceEpisodeCorrelationResult correlate({
    required BangumiEpisode episode,
    required SourceSubjectDetails details,
  }) {
    if (!BangumiEpisodeTypeCode.isMainStory(episode.type)) {
      return SourceEpisodeCorrelationResult(
        status: SourceEpisodeCorrelationStatus.selectionRequired,
        candidates: _allEpisodes(details),
        reason: 'episode_type_not_auto_mappable',
      );
    }

    final expected = SourceEpisodeOrdinal.canonicalBangumiSort(episode.sort);
    if (expected.isEmpty) {
      return const SourceEpisodeCorrelationResult(
        status: SourceEpisodeCorrelationStatus.selectionRequired,
        candidates: [],
        reason: 'episode_sort_invalid',
      );
    }
    final candidates = <SourceEpisode>[];
    for (final line in details.lines) {
      for (final sourceEpisode in line.episodes) {
        final ordinal = SourceEpisodeOrdinal.parse(sourceEpisode.title);
        if (ordinal?.canonical == expected) candidates.add(sourceEpisode);
      }
    }
    final unique = <SourceEpisodeIdentity, SourceEpisode>{
      for (final candidate in candidates) candidate.identity: candidate,
    };
    final values = unique.values.toList(growable: false);
    if (values.length == 1) {
      return SourceEpisodeCorrelationResult(
        status: SourceEpisodeCorrelationStatus.automatic,
        candidates: values,
        selected: values.single.identity,
        reason: 'exact_main_story_ordinal',
      );
    }
    return SourceEpisodeCorrelationResult(
      status: values.isEmpty
          ? SourceEpisodeCorrelationStatus.notFound
          : SourceEpisodeCorrelationStatus.selectionRequired,
      candidates: values,
      reason: values.isEmpty
          ? 'episode_not_found'
          : 'episode_ordinal_ambiguous',
    );
  }

  List<SourceEpisode> _allEpisodes(SourceSubjectDetails details) => [
    for (final line in details.lines) ...line.episodes,
  ];
}

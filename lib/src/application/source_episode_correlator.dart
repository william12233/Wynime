import '../domain/models/bangumi_episode_type.dart';
import '../domain/models/bangumi_models.dart';
import '../domain/models/episode_mapping.dart';
import '../domain/models/source_identity.dart';
import '../domain/models/source_models.dart';

enum SourceEpisodeCorrelationStatus { automatic, selectionRequired, notFound }

final class SourceEpisodeCorrelationResult {
  SourceEpisodeCorrelationResult({
    required this.status,
    required Iterable<SourceEpisode> candidates,
    this.selected,
    this.reason,
    EpisodeMappingResult? mappingResult,
  }) : candidates = List<SourceEpisode>.unmodifiable(candidates),
       mappingResult =
           mappingResult ??
           EpisodeMappingResult(
             status: switch (status) {
               SourceEpisodeCorrelationStatus.automatic =>
                 EpisodeMappingStatus.automatic,
               SourceEpisodeCorrelationStatus.selectionRequired =>
                 EpisodeMappingStatus.selectionRequired,
               SourceEpisodeCorrelationStatus.notFound =>
                 EpisodeMappingStatus.notFound,
             },
             reason: reason,
           );

  final SourceEpisodeCorrelationStatus status;
  final List<SourceEpisode> candidates;
  final SourceEpisodeIdentity? selected;
  final String? reason;
  final EpisodeMappingResult mappingResult;

  EpisodeMapping? get mapping => mappingResult.selected;

  List<EpisodeMapping> get mappings => mappingResult.candidates;
}

/// Correlates Bangumi main-story episodes with source labels while retaining
/// explicit evidence for cumulative/absolute numbering. A non-main Bangumi
/// episode remains manual-selection work.
final class SourceEpisodeCorrelator {
  const SourceEpisodeCorrelator();

  SourceEpisodeCorrelationResult correlate({
    required BangumiEpisode episode,
    required SourceSubjectDetails details,
    Iterable<BangumiEpisode> bangumiEpisodes = const [],
  }) {
    final allEpisodes = _allEpisodes(details);
    if (!BangumiEpisodeTypeCode.isMainStory(episode.type)) {
      return _selection(
        candidates: allEpisodes,
        reason: 'episode_type_not_auto_mappable',
      );
    }

    if (!episode.sort.isFinite || episode.sort < 0) {
      return _selection(
        candidates: allEpisodes,
        reason: allEpisodes.isEmpty
            ? 'episode_sort_invalid'
            : 'episode_manual_selection_required',
      );
    }

    // A loaded Bangumi season is stronger evidence than a coincidental raw
    // ordinal. For example, Bangumi 13..25 paired with source 1..13 must map
    // Bangumi 13 to source 1, not to the source episode also labelled 13.
    final uniqueInferred = _uniqueMappings(
      _inferFromContext(
        episode: episode,
        details: details,
        bangumiEpisodes: bangumiEpisodes,
      ),
    );
    if (uniqueInferred.length == 1) {
      return _automatic(
        uniqueInferred.single,
        reason: 'episode_offset_inferred_from_ordered_context',
      );
    }
    if (uniqueInferred.length > 1) {
      return _selection(
        candidates: uniqueInferred.map((mapping) => mapping.sourceEpisode),
        mappings: uniqueInferred,
        reason: 'episode_offset_ambiguous',
      );
    }

    final exact = <EpisodeMapping>[];
    for (final sourceEpisode in allEpisodes) {
      final number = sourceEpisode.episodeNumber;
      if (number == null ||
          sourceEpisode.episodeKind != SourceEpisodeKind.main) {
        continue;
      }
      if (_sameNumber(number, episode.sort)) {
        exact.add(
          _mapping(
            episode: episode,
            sourceEpisode: sourceEpisode,
            mode: EpisodeNumberingMode.exact,
            offset: 0,
            evidence: const [
              EpisodeMappingEvidence(code: 'exact_source_label_number'),
            ],
          ),
        );
      }
    }
    final uniqueExact = _uniqueMappings(exact);
    if (uniqueExact.length == 1) {
      return _automatic(uniqueExact.single, reason: 'exact_main_story_ordinal');
    }
    if (uniqueExact.length > 1) {
      return _selection(
        candidates: uniqueExact.map((mapping) => mapping.sourceEpisode),
        mappings: uniqueExact,
        reason: 'episode_ordinal_ambiguous',
      );
    }

    return _selection(
      candidates: allEpisodes,
      reason: allEpisodes.isEmpty
          ? 'episode_not_found'
          : 'episode_manual_selection_required',
      status: allEpisodes.isEmpty
          ? SourceEpisodeCorrelationStatus.notFound
          : SourceEpisodeCorrelationStatus.selectionRequired,
    );
  }

  SourceEpisodeCorrelationResult _automatic(
    EpisodeMapping mapping, {
    required String reason,
  }) => SourceEpisodeCorrelationResult(
    status: SourceEpisodeCorrelationStatus.automatic,
    candidates: [mapping.sourceEpisode],
    selected: mapping.sourceEpisode.identity,
    reason: reason,
    mappingResult: EpisodeMappingResult(
      status: EpisodeMappingStatus.automatic,
      selected: mapping,
      candidates: [mapping],
      reason: reason,
    ),
  );

  SourceEpisodeCorrelationResult _selection({
    required Iterable<SourceEpisode> candidates,
    required String reason,
    SourceEpisodeCorrelationStatus? status,
    Iterable<EpisodeMapping> mappings = const [],
  }) {
    final candidateList = List<SourceEpisode>.unmodifiable(candidates);
    final mappingList = List<EpisodeMapping>.unmodifiable(mappings);
    final resolvedStatus =
        status ?? SourceEpisodeCorrelationStatus.selectionRequired;
    return SourceEpisodeCorrelationResult(
      status: resolvedStatus,
      candidates: candidateList,
      reason: reason,
      mappingResult: EpisodeMappingResult(
        status: resolvedStatus == SourceEpisodeCorrelationStatus.notFound
            ? EpisodeMappingStatus.notFound
            : EpisodeMappingStatus.selectionRequired,
        candidates: mappingList,
        reason: reason,
      ),
    );
  }

  EpisodeMapping _mapping({
    required BangumiEpisode episode,
    required SourceEpisode sourceEpisode,
    required EpisodeNumberingMode mode,
    required double offset,
    required Iterable<EpisodeMappingEvidence> evidence,
  }) {
    final sourceNumber = sourceEpisode.episodeNumber!;
    return EpisodeMapping(
      sourceEpisode: sourceEpisode,
      bangumiSort: episode.sort,
      sourceNumber: sourceNumber,
      numberingMode: mode,
      seasonRelativeNumber: sourceNumber,
      absoluteNumber: episode.sort,
      offset: offset,
      evidence: List<EpisodeMappingEvidence>.unmodifiable(evidence),
    );
  }

  List<EpisodeMapping> _uniqueMappings(Iterable<EpisodeMapping> values) {
    final unique = <SourceEpisodeIdentity, EpisodeMapping>{};
    for (final mapping in values) {
      unique[mapping.sourceEpisode.identity] = mapping;
    }
    return unique.values.toList(growable: false);
  }

  List<EpisodeMapping> _inferFromContext({
    required BangumiEpisode episode,
    required SourceSubjectDetails details,
    required Iterable<BangumiEpisode> bangumiEpisodes,
  }) {
    final bangumiMainEpisodes =
        bangumiEpisodes
            .where(
              (candidate) => BangumiEpisodeTypeCode.isMainStory(candidate.type),
            )
            .where(
              (candidate) => candidate.sort.isFinite && candidate.sort >= 0,
            )
            .toList(growable: false)
          ..sort((left, right) => left.sort.compareTo(right.sort));
    if (bangumiMainEpisodes.length < 2) return const [];

    final inferred = <EpisodeMapping>[];
    for (final line in details.lines) {
      final sourceMainEpisodes =
          line.episodes
              .where(
                (candidate) =>
                    candidate.episodeNumber != null &&
                    candidate.episodeKind == SourceEpisodeKind.main,
              )
              .toList(growable: false)
            ..sort(
              (left, right) =>
                  left.episodeNumber!.compareTo(right.episodeNumber!),
            );
      if (sourceMainEpisodes.length != bangumiMainEpisodes.length ||
          sourceMainEpisodes.length < 2) {
        continue;
      }
      final offsets = <double>{};
      for (var index = 0; index < sourceMainEpisodes.length; index++) {
        offsets.add(
          bangumiMainEpisodes[index].sort -
              sourceMainEpisodes[index].episodeNumber!,
        );
      }
      if (offsets.length != 1) continue;
      final offset = offsets.single;
      final sourceNumber = episode.sort - offset;
      final sourceEpisode = sourceMainEpisodes.firstWhereOrNull(
        (candidate) => _sameNumber(candidate.episodeNumber!, sourceNumber),
      );
      if (sourceEpisode == null) continue;
      inferred.add(
        _mapping(
          episode: episode,
          sourceEpisode: sourceEpisode,
          mode: _inferredMode(
            offset: offset,
            bangumiFirst: bangumiMainEpisodes.first.sort,
            sourceFirst: sourceMainEpisodes.first.episodeNumber!,
          ),
          offset: offset,
          evidence: [
            const EpisodeMappingEvidence(code: 'source_label_number'),
            EpisodeMappingEvidence(
              code: 'ordered_episode_index_alignment',
              offset: offset,
              alignedEpisodeCount: sourceMainEpisodes.length,
            ),
          ],
        ),
      );
    }
    return inferred;
  }

  List<SourceEpisode> _allEpisodes(SourceSubjectDetails details) => [
    for (final line in details.lines) ...line.episodes,
  ];

  static EpisodeNumberingMode _inferredMode({
    required double offset,
    required double bangumiFirst,
    required double sourceFirst,
  }) {
    if (_sameNumber(offset, 0)) return EpisodeNumberingMode.seasonRelative;
    if (bangumiFirst > 1 && sourceFirst <= 1) {
      return EpisodeNumberingMode.cumulativeAbsolute;
    }
    return EpisodeNumberingMode.inferredOffset;
  }

  static bool _sameNumber(double left, double right) =>
      (left - right).abs() < 0.000001;
}

extension<T> on Iterable<T> {
  T? firstWhereOrNull(bool Function(T value) test) {
    for (final value in this) {
      if (test(value)) return value;
    }
    return null;
  }
}

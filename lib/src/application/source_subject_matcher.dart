import '../domain/models/source_identity.dart';
import '../domain/models/source_models.dart';
import '../domain/models/bangumi_models.dart';
import '../infrastructure/source_rules/source_title_normalizer.dart';

enum SourceSubjectMatchStatus { matched, selectionRequired, notFound }

final class SourceSubjectMatchResult {
  const SourceSubjectMatchResult({
    required this.status,
    required this.candidates,
  });

  final SourceSubjectMatchStatus status;
  final List<SourceSearchResult> candidates;

  SourceSubjectIdentity? get identity =>
      status == SourceSubjectMatchStatus.matched && candidates.length == 1
      ? SourceSubjectIdentity(
          sourceId: candidates.single.sourceId,
          subjectId: candidates.single.subjectId,
        )
      : null;
}

/// Exact normalized-title matcher. Ambiguous and missing results remain
/// explicit states and never fall back to the first search result.
final class SourceSubjectMatcher {
  const SourceSubjectMatcher({this.normalizer = const SourceTitleNormalizer()});

  final SourceTitleNormalizer normalizer;

  SourceSubjectMatchResult match({
    required BangumiSubject subject,
    required Iterable<SourceSearchResult> results,
    Iterable<String> alternateTitles = const [],
  }) {
    final acceptedTitles = {
      normalizer.normalize(subject.name),
      normalizer.normalize(subject.nameCn),
      ...alternateTitles.map(normalizer.normalize),
    }..remove('');
    final unique = <String, SourceSearchResult>{};
    for (final result in results) {
      if (!acceptedTitles.contains(normalizer.normalize(result.title))) {
        continue;
      }
      final identity = SourceSubjectIdentity(
        sourceId: result.sourceId,
        subjectId: result.subjectId,
      );
      unique[identity.identityKey] = result;
    }
    final candidates = unique.values.toList(growable: false);
    return SourceSubjectMatchResult(
      status: switch (candidates.length) {
        0 => SourceSubjectMatchStatus.notFound,
        1 => SourceSubjectMatchStatus.matched,
        _ => SourceSubjectMatchStatus.selectionRequired,
      },
      candidates: candidates,
    );
  }
}

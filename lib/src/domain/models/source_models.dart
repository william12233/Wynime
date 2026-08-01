import 'package:wynime/src/domain/models/source_identity.dart';

final class SourceSearchResult {
  SourceSearchResult({
    required this.sourceId,
    required this.subjectId,
    required this.title,
  }) : assert(sourceId.trim().isNotEmpty, 'sourceId must not be empty.'),
       assert(subjectId.trim().isNotEmpty, 'subjectId must not be empty.'),
       assert(title.trim().isNotEmpty, 'title must not be empty.');

  final String sourceId;
  final String subjectId;
  final String title;
}

final class SourceEpisode {
  SourceEpisode({required this.identity, required this.title})
    : assert(title.trim().isNotEmpty, 'title must not be empty.');

  final SourceEpisodeIdentity identity;
  final String title;
}

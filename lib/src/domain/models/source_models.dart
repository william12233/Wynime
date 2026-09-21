import 'package:wynime/src/domain/models/source_identity.dart';

import 'dart:collection';

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

final class SourceSubjectLine {
  SourceSubjectLine({
    required this.identity,
    required this.lineId,
    required String title,
    required Iterable<SourceEpisode> episodes,
  }) : title = _requiredText(title, 'title', 256),
       episodes = UnmodifiableListView(_boundedEpisodes(episodes)) {
    for (final episode in this.episodes) {
      if (episode.identity.sourceId != identity.sourceId ||
          episode.identity.subjectId != identity.subjectId ||
          episode.identity.lineId != lineId) {
        throw ArgumentError(
          'A subject line may only contain episodes from its own identity.',
        );
      }
    }
  }

  final SourceSubjectIdentity identity;
  final String lineId;
  final String title;
  final UnmodifiableListView<SourceEpisode> episodes;

  static List<SourceEpisode> _boundedEpisodes(Iterable<SourceEpisode> values) {
    final result = <SourceEpisode>[];
    for (final value in values) {
      if (result.length == 512) {
        throw ArgumentError.value(
          values,
          'episodes',
          'A source line may contain at most 512 episodes.',
        );
      }
      result.add(value);
    }
    return List<SourceEpisode>.unmodifiable(result);
  }
}

final class SourceSubjectDetails {
  SourceSubjectDetails({
    required this.identity,
    required String title,
    Map<String, String> metadata = const {},
    required Iterable<SourceSubjectLine> lines,
  }) : title = _requiredText(title, 'title', 256),
       metadata = _freezeMetadata(metadata),
       lines = UnmodifiableListView(_boundedLines(lines)) {
    for (final line in this.lines) {
      if (line.identity != identity) {
        throw ArgumentError(
          'A subject detail result may only contain its own source identity.',
        );
      }
    }
  }

  final SourceSubjectIdentity identity;
  final String title;
  final UnmodifiableMapView<String, String> metadata;
  final UnmodifiableListView<SourceSubjectLine> lines;

  static UnmodifiableMapView<String, String> _freezeMetadata(
    Map<String, String> values,
  ) {
    if (values.length > 32) {
      throw ArgumentError.value(
        values,
        'metadata',
        'Subject metadata may contain at most 32 fields.',
      );
    }
    final result = <String, String>{};
    for (final entry in values.entries) {
      final key = _requiredText(entry.key, 'metadata key', 64);
      final value = _requiredText(entry.value, 'metadata value', 512);
      result[key] = value;
    }
    return UnmodifiableMapView(Map<String, String>.unmodifiable(result));
  }

  static List<SourceSubjectLine> _boundedLines(
    Iterable<SourceSubjectLine> values,
  ) {
    final result = <SourceSubjectLine>[];
    for (final value in values) {
      if (result.length == 32) {
        throw ArgumentError.value(
          values,
          'lines',
          'A subject may contain at most 32 source lines.',
        );
      }
      result.add(value);
    }
    return List<SourceSubjectLine>.unmodifiable(result);
  }
}

String _requiredText(String value, String name, int maxLength) {
  final normalized = value.trim();
  if (normalized.isEmpty ||
      normalized.length > maxLength ||
      normalized != value) {
    throw ArgumentError.value(
      value,
      name,
      'Must be a trimmed non-empty value of at most $maxLength characters.',
    );
  }
  if (normalized.codeUnits.any(
    (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
  )) {
    throw ArgumentError.value(value, name, 'Must not contain controls.');
  }
  return normalized;
}

import 'package:wynime/src/domain/models/source_identity.dart';

import 'dart:collection';

enum SourceEpisodeKind { main, special, ova, ona, movie, unknown }

/// The bounded, source-owned interpretation of one episode label.
///
/// The raw label is retained for diagnostics and explicit user selection. The
/// numeric value is deliberately optional because SP/OVA and other source
/// labels do not always have an ordinal.
final class SourceEpisodeLabel {
  factory SourceEpisodeLabel.parse(String rawLabel) {
    final raw = rawLabel.trim();
    final folded = _foldEpisodeLabel(raw);
    final upper = folded.toUpperCase();
    final kind = switch (true) {
      _ when RegExp(r'^(?:SP|SPECIAL|特別|番外)').hasMatch(upper) =>
        SourceEpisodeKind.special,
      _ when RegExp(r'^(?:OVA|OAD)').hasMatch(upper) => SourceEpisodeKind.ova,
      _ when RegExp(r'^ONA').hasMatch(upper) => SourceEpisodeKind.ona,
      _ when RegExp(r'^(?:MOVIE|劇場版)').hasMatch(upper) =>
        SourceEpisodeKind.movie,
      _ => SourceEpisodeKind.main,
    };
    final numberMatch =
        RegExp(
          r'^(?:episode|ep)\s*([0-9]+(?:\.[0-9]+)?)\s*(?:話|话|集)?$',
          caseSensitive: false,
        ).firstMatch(folded) ??
        RegExp(
          r'^(?:sp|special|ova|oad|ona)\s*([0-9]+(?:\.[0-9]+)?)?\s*'
          r'(?:話|话|集)?$',
          caseSensitive: false,
        ).firstMatch(folded) ??
        RegExp(
          r'^第\s*([0-9]+(?:\.[0-9]+)?)\s*(?:話|话|集)?$',
        ).firstMatch(folded) ??
        RegExp(r'^([0-9]+(?:\.[0-9]+)?)\s*(?:話|话|集)?$').firstMatch(folded);
    return SourceEpisodeLabel(
      rawLabel: raw,
      number: numberMatch == null
          ? null
          : double.tryParse(numberMatch.group(1) ?? ''),
      kind: kind,
    );
  }

  SourceEpisodeLabel({
    required String rawLabel,
    this.number,
    this.kind = SourceEpisodeKind.unknown,
  }) : rawLabel = _requiredEpisodeText(rawLabel, 'rawLabel', 256) {
    if (number != null && (!number!.isFinite || number! < 0)) {
      throw ArgumentError.value(
        number,
        'number',
        'Must be finite and non-negative.',
      );
    }
  }

  final String rawLabel;
  final double? number;
  final SourceEpisodeKind kind;

  String? get canonicalNumber {
    final value = number;
    if (value == null) return null;
    final text = value.toString();
    if (!text.contains('.')) return text;
    return text
        .replaceFirst(RegExp(r'0+$'), '')
        .replaceFirst(RegExp(r'\.$'), '');
  }

  static String _foldEpisodeLabel(String value) {
    final folded = StringBuffer();
    for (final unit in value.codeUnits) {
      if (unit == 0x3000) {
        folded.write(' ');
      } else if (unit >= 0xff10 && unit <= 0xff19) {
        folded.writeCharCode(unit - 0xfee0);
      } else if (unit >= 0xff01 && unit <= 0xff5e) {
        folded.writeCharCode(unit - 0xfee0);
      } else {
        folded.writeCharCode(unit);
      }
    }
    return folded.toString().replaceAll(RegExp(r'\s+'), ' ').trim();
  }
}

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
  SourceEpisode({
    required this.identity,
    required this.title,
    SourceEpisodeLabel? label,
    double? episodeNumber,
    SourceEpisodeKind? episodeKind,
  }) : label =
           label ??
           _episodeLabel(
             title,
             episodeNumber: episodeNumber,
             episodeKind: episodeKind,
           ),
       assert(title.trim().isNotEmpty, 'title must not be empty.') {
    if (episodeNumber != null &&
        (!episodeNumber.isFinite || episodeNumber < 0)) {
      throw ArgumentError.value(
        episodeNumber,
        'episodeNumber',
        'Must be finite and non-negative.',
      );
    }
  }

  final SourceEpisodeIdentity identity;
  final String title;

  /// The exact bounded source label before ordinal normalization.
  final SourceEpisodeLabel label;

  String get rawLabel => label.rawLabel;

  double? get episodeNumber => label.number;

  SourceEpisodeKind get episodeKind => label.kind;

  static SourceEpisodeLabel _episodeLabel(
    String title, {
    double? episodeNumber,
    SourceEpisodeKind? episodeKind,
  }) {
    final parsed = SourceEpisodeLabel.parse(title);
    return SourceEpisodeLabel(
      rawLabel: title,
      number: episodeNumber ?? parsed.number,
      kind: episodeKind ?? parsed.kind,
    );
  }
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

String _requiredEpisodeText(String value, String name, int maxLength) {
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

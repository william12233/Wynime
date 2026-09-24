import '../domain/models/source_models.dart';

/// One strict, canonical source episode ordinal.
final class SourceEpisodeOrdinal {
  const SourceEpisodeOrdinal._(this.canonical);

  final String canonical;

  static SourceEpisodeOrdinal? parse(String value) {
    final parsed = SourceEpisodeLabel.parse(value);
    if (parsed.kind != SourceEpisodeKind.main) return null;
    final canonical = parsed.canonicalNumber;
    return canonical == null ? null : SourceEpisodeOrdinal._(canonical);
  }

  static String canonicalBangumiSort(double value) {
    if (!value.isFinite || value < 0) return '';
    final text = value.toString();
    return parse(text)?.canonical ?? '';
  }
}

/// One strict, canonical source episode ordinal.
final class SourceEpisodeOrdinal {
  const SourceEpisodeOrdinal._(this.canonical);

  final String canonical;

  static SourceEpisodeOrdinal? parse(String value) {
    var candidate = value.trim();
    final folded = StringBuffer();
    for (final unit in candidate.codeUnits) {
      if (unit == 0x3000) {
        folded.write(' ');
      } else if (unit >= 0xff01 && unit <= 0xff5e) {
        folded.writeCharCode(unit - 0xfee0);
      } else {
        folded.writeCharCode(unit);
      }
    }
    candidate = folded.toString().trim();
    candidate = candidate.replaceFirst(
      RegExp(r'^(?:episode|ep)\s+', caseSensitive: false),
      '',
    );
    candidate = candidate.replaceFirst(RegExp(r'^第'), '');
    candidate = candidate.replaceFirst(RegExp(r'[話话集]$'), '');
    if (!RegExp(r'^(?:0|[0-9]+)(?:\.[0-9]+)?$').hasMatch(candidate)) {
      return null;
    }
    final parts = candidate.split('.');
    final integer = parts.first.replaceFirst(RegExp(r'^0+(?=[0-9])'), '');
    if (parts.length == 1) return SourceEpisodeOrdinal._(integer);
    final fraction = parts.last.replaceFirst(RegExp(r'0+$'), '');
    return SourceEpisodeOrdinal._(
      fraction.isEmpty ? integer : '$integer.$fraction',
    );
  }

  static String canonicalBangumiSort(double value) {
    if (!value.isFinite || value < 0) return '';
    final text = value.toString();
    return parse(text)?.canonical ?? '';
  }
}

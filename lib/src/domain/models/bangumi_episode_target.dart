/// An explicit Bangumi identity supplied by the episode-to-Bangumi mapping.
///
/// A source episode must never be converted to a Bangumi episode by guessing
/// from an episode number or by assuming that source IDs are Bangumi IDs.
final class BangumiEpisodeTarget {
  BangumiEpisodeTarget({required String subjectId, required String episodeId})
    : subjectId = _requiredId(subjectId, 'subjectId'),
      episodeId = _requiredId(episodeId, 'episodeId');

  final String subjectId;
  final String episodeId;
}

String _requiredId(String value, String name) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.length > 128) {
    throw ArgumentError.value(
      value,
      name,
      'Must contain between 1 and 128 characters.',
    );
  }
  if (normalized.codeUnits.any((unit) => unit < 0x20 || unit == 0x7f)) {
    throw ArgumentError.value(value, name, 'Must not contain controls.');
  }
  return normalized;
}

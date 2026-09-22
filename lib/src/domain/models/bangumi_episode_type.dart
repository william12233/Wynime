/// Bangumi v0 episode type codes.
///
/// Only type 0 is eligible for automatic source episode correlation. Other
/// values and unknown future values must use explicit user selection.
final class BangumiEpisodeTypeCode {
  const BangumiEpisodeTypeCode._();

  static const mainStory = 0;
  static const special = 1;
  static const opening = 2;
  static const ending = 3;
  static const promo = 4;
  static const mad = 5;
  static const other = 6;

  static bool isMainStory(int rawType) => rawType == mainStory;
}

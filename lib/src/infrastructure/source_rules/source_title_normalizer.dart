/// Conservative title normalization for deterministic source matching.
///
/// This intentionally does not translate, romanize, infer seasons or apply
/// fuzzy similarity. It only makes the same textual title representation
/// comparable across common fullwidth and punctuation forms.
final class SourceTitleNormalizer {
  const SourceTitleNormalizer();

  String normalize(String value) {
    final folded = StringBuffer();
    for (final unit in value.trim().codeUnits) {
      if (unit == 0x3000) {
        folded.write(' ');
      } else if (unit >= 0xff01 && unit <= 0xff5e) {
        folded.writeCharCode(unit - 0xfee0);
      } else if (unit >= 0x41 && unit <= 0x5a) {
        folded.writeCharCode(unit + 0x20);
      } else {
        folded.writeCharCode(unit);
      }
    }

    final asciiLowered = folded.toString().replaceAllMapped(
      RegExp(r'[A-Z]'),
      (match) => match[0]!.toLowerCase(),
    );
    final punctuationNormalized = asciiLowered.replaceAllMapped(
      RegExp(r'[\u3001\u3002,，。.!！?？:：;；/／\\_\-—–·・()[\]{}【】「」『』]'),
      (_) => ' ',
    );
    return punctuationNormalized.replaceAll(RegExp(r'\s+'), ' ').trim();
  }
}

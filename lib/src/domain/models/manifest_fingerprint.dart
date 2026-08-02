final class ManifestFingerprint {
  ManifestFingerprint({required this.algorithm, required this.value})
    : assert(algorithm.trim().isNotEmpty, 'algorithm must not be empty.'),
      assert(value.trim().isNotEmpty, 'value must not be empty.');

  final String algorithm;
  final String value;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ManifestFingerprint &&
          algorithm == other.algorithm &&
          value == other.value;

  @override
  int get hashCode => Object.hash(algorithm, value);

  @override
  String toString() => '$algorithm:$value';
}

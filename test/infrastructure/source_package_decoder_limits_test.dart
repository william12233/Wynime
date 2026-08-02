import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';

void main() {
  const decoder = SourcePackageDecoder();

  test('source package decoding fails before parsing oversized input', () {
    final oversized = ' ' * (SourcePackageDecoder.maxPackageBytes + 1);

    expect(
      () => decoder.decode(oversized),
      throwsA(
        isA<SourcePackageFormatException>().having(
          (error) => error.message,
          'message',
          contains('exceeds'),
        ),
      ),
    );
  });
}

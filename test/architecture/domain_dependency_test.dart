import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'domain layer does not import Flutter or outer implementation packages',
    () {
      final domainFiles = Directory('lib/src/domain')
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart'));

      const forbiddenImports = [
        'package:flutter/',
        'package:flutter_localizations/',
        'package:drift/',
        'dart:io',
        'dart:ffi',
      ];

      for (final file in domainFiles) {
        final content = file.readAsStringSync();
        for (final forbiddenImport in forbiddenImports) {
          expect(
            content,
            isNot(contains(forbiddenImport)),
            reason: '${file.path} must remain a pure Domain file.',
          );
        }
      }
    },
  );
}

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Phase 2 source rules remain fixture-only and non-executable', () {
    final files = Directory('lib/src/infrastructure/source_rules')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    const forbidden = [
      "import 'dart:io'",
      "import 'dart:ffi'",
      'package:http/',
      'package:dio/',
      'package:webview_flutter/',
      'package:flutter_inappwebview/',
      'dart:mirrors',
      'Process.',
      'HttpClient',
      'Socket',
      'Isolate.spawnUri',
    ];

    for (final file in files) {
      final content = file.readAsStringSync();
      for (final token in forbidden) {
        expect(
          content,
          isNot(contains(token)),
          reason: '${file.path} must remain offline and non-executable.',
        );
      }
    }
  });
}

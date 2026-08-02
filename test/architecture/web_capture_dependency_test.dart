import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('web capture policy core remains plugin and platform independent', () {
    final files = [
      File('lib/src/domain/models/web_capture_models.dart'),
      File('lib/src/domain/services/web_source_browser.dart'),
      ...Directory('lib/src/infrastructure/web_capture')
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart')),
    ];

    const forbidden = [
      "import 'dart:io'",
      "import 'dart:ffi'",
      'package:flutter/',
      'package:flutter_inappwebview/',
      'package:webview_flutter/',
      'package:webview_windows/',
      'MethodChannel',
      'Process.',
      'HttpClient',
      'Socket',
    ];

    for (final file in files) {
      final content = file.readAsStringSync();
      for (final token in forbidden) {
        expect(
          content,
          isNot(contains(token)),
          reason: '${file.path} must remain a pure typed capture boundary.',
        );
      }
    }
  });

  test('WebView plugin imports remain isolated to the platform boundary', () {
    final dartFiles = Directory('lib/src')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    for (final file in dartFiles) {
      final content = file.readAsStringSync();
      if (!content.contains('package:flutter_inappwebview/')) {
        continue;
      }
      final normalizedPath = file.path.replaceAll('\\', '/');
      expect(
        normalizedPath,
        startsWith('lib/src/platform/web_capture/'),
        reason: '${file.path} imports the WebView plugin outside Platform.',
      );
    }
  });
}

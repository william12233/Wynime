import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('production player keeps source WebView acquisition transient', () {
    final content = File(
      'lib/src/presentation/playback/player_page.dart',
    ).readAsStringSync();

    expect(content, contains('_BackgroundAcquisitionHost'));
    expect(content, contains('SourceLineSelector'));
    expect(content, contains('sourcePlayableFallback?.hasPendingCapture'));
    expect(
      content,
      isNot(
        contains(
          'Positioned.fill(\n                child: widget.sourcePlayableFallback',
        ),
      ),
    );
    expect(content, isNot(contains('請使用來源頁面的公開播放控制')));
    expect(content, isNot(contains('VisibleSourceWebPage')));
    expect(content, isNot(contains('SourceWebsitePlayer')));
  });

  test(
    'production playback surface is native, not a WebView video surface',
    () {
      final content = File(
        'lib/src/presentation/playback/player_page.dart',
      ).readAsStringSync();
      expect(content, contains('surfaceHost?.build(context)'));
      expect(content, isNot(contains('InAppWebView(')));
    },
  );
}

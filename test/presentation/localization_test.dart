import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  testWidgets(
    'supports Traditional Chinese, Simplified Chinese, Japanese, and English',
    (tester) async {
      const expectations = <Locale, String>{
        Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hant'): '收藏庫',
        Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hans'): '收藏库',
        Locale('ja'): 'ライブラリ',
        Locale('en'): 'Library',
      };

      await tester.binding.setSurfaceSize(const Size(1024, 768));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      for (final entry in expectations.entries) {
        await tester.pumpWidget(WynimeApp(locale: entry.key));
        await tester.pumpAndSettle();
        expect(find.text(entry.value), findsOneWidget);
      }
    },
  );
}

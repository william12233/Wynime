import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  testWidgets(
    'supports Traditional Chinese, Simplified Chinese, Japanese, and English',
    (tester) async {
      final expectations = <({Locale locale, String label})>[
        (
          locale: const Locale.fromSubtags(
            languageCode: 'zh',
            scriptCode: 'Hant',
          ),
          label: '收藏庫',
        ),
        (
          locale: const Locale.fromSubtags(
            languageCode: 'zh',
            scriptCode: 'Hans',
          ),
          label: '收藏库',
        ),
        (locale: const Locale('ja'), label: 'ライブラリ'),
        (locale: const Locale('en'), label: 'Library'),
      ];

      await tester.binding.setSurfaceSize(const Size(1024, 768));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      for (final expectation in expectations) {
        await tester.pumpWidget(WynimeApp(locale: expectation.locale));
        await tester.pumpAndSettle();
        expect(find.text(expectation.label), findsOneWidget);
      }
    },
  );
}

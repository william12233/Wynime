import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  Future<void> pumpApp(
    WidgetTester tester, {
    Size size = const Size(360, 800),
  }) async {
    await tester.binding.setSurfaceSize(size);
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const WynimeApp(locale: Locale('en')));
    await tester.pumpAndSettle();
  }

  testWidgets('search keeps a submitted query local without active sources', (
    tester,
  ) async {
    await pumpApp(tester);

    await tester.tap(find.byIcon(Icons.search_outlined));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(EditableText), 'example title');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(find.text('No active sources'), findsOneWidget);
    expect(
      find.text(
        'The query stays local. Install and explicitly enable a reviewed source package before searching.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('library filter controls are available on compact layout', (
    tester,
  ) async {
    await pumpApp(tester);

    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.byIcon(Icons.video_library_outlined),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.byWidgetPredicate((widget) => widget is SegmentedButton),
      findsOneWidget,
    );

    await tester.tap(find.text('Watching'));
    await tester.pumpAndSettle();
    expect(find.text('Your library is empty'), findsOneWidget);
  });

  testWidgets('privacy diagnostics remain off until explicitly enabled', (
    tester,
  ) async {
    await pumpApp(tester, size: const Size(1024, 768));

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    final before = tester.widget<SwitchListTile>(find.byType(SwitchListTile));
    expect(before.value, isFalse);

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    final after = tester.widget<SwitchListTile>(find.byType(SwitchListTile));
    expect(after.value, isTrue);
  });
}

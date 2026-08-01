import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  Future<void> expectGolden(
    WidgetTester tester, {
    required Size size,
    required String fileName,
  }) async {
    await tester.binding.setSurfaceSize(size);
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const WynimeApp(locale: Locale('en')));
    await tester.pumpAndSettle();
    await expectLater(
      find.byType(WynimeApp),
      matchesGoldenFile('goldens/$fileName.png'),
    );
  }

  testWidgets('Android 360x800 shell', (tester) async {
    await expectGolden(
      tester,
      size: const Size(360, 800),
      fileName: 'app_shell_360x800',
    );
  }, tags: 'golden');

  testWidgets('Android 412x915 shell', (tester) async {
    await expectGolden(
      tester,
      size: const Size(412, 915),
      fileName: 'app_shell_412x915',
    );
  }, tags: 'golden');

  testWidgets('Windows 1024x768 shell', (tester) async {
    await expectGolden(
      tester,
      size: const Size(1024, 768),
      fileName: 'app_shell_1024x768',
    );
  }, tags: 'golden');

  testWidgets('Windows 1440x900 shell', (tester) async {
    await expectGolden(
      tester,
      size: const Size(1440, 900),
      fileName: 'app_shell_1440x900',
    );
  }, tags: 'golden');
}

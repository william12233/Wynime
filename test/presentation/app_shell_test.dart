import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  Future<void> pumpAtSize(WidgetTester tester, Size size) async {
    await tester.binding.setSurfaceSize(size);
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const WynimeApp(locale: Locale('en')));
    await tester.pumpAndSettle();
  }

  testWidgets('uses bottom navigation for compact width', (tester) async {
    await pumpAtSize(tester, const Size(360, 800));

    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.byType(NavigationRail), findsNothing);
  });

  testWidgets('uses collapsed rail for medium width', (tester) async {
    await pumpAtSize(tester, const Size(600, 800));

    final rail = tester.widget<NavigationRail>(find.byType(NavigationRail));
    expect(rail.extended, isFalse);
    expect(find.byType(NavigationBar), findsNothing);
  });

  testWidgets('uses expanded rail for expanded width', (tester) async {
    await pumpAtSize(tester, const Size(1024, 768));

    final rail = tester.widget<NavigationRail>(find.byType(NavigationRail));
    expect(rail.extended, isTrue);
    expect(find.byType(NavigationBar), findsNothing);
  });

  testWidgets('navigation changes the selected page', (tester) async {
    await pumpAtSize(tester, const Size(360, 800));

    await tester.tap(find.byIcon(Icons.search_outlined));
    await tester.pumpAndSettle();

    expect(find.text('Source access is unavailable'), findsOneWidget);
  });
}

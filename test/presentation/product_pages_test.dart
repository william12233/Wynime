import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_authentication.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';

import '../helpers/test_database.dart';

void main() {
  Future<void> pumpApp(
    WidgetTester tester, {
    Size size = const Size(360, 800),
    BangumiSessionController? bangumi,
  }) async {
    await tester.binding.setSurfaceSize(size);
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      WynimeApp(locale: const Locale('en'), bangumi: bangumi),
    );
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

  testWidgets('library renders poster artwork for every collection entry', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final database = openTestDatabase();
    addTearDown(database.close);
    final controller = BangumiSessionController(
      authentication: const UnavailableBangumiAuthentication(),
      store: DriftBangumiLocalStore(database),
      clientFactory: (_) => throw StateError('client must not be created'),
    );

    await tester.pumpWidget(
      WynimeApp(
        locale: const Locale('en'),
        bangumi: controller,
        onReady: () async {
          controller.collections = [
            BangumiCollectionEntry(
              subjectId: '42',
              status: BangumiCollectionStatus.watching,
              nameCn: '作品',
              imageUrl: Uri.parse('https://lain.bgm.tv/pic/cover/c/42.jpg'),
            ),
            BangumiCollectionEntry(
              subjectId: '43',
              status: BangumiCollectionStatus.completed,
              nameCn: '沒有封面的作品',
            ),
          ];
          controller.notifyListeners();
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.byIcon(Icons.video_library_outlined),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('bangumi-collection-artwork-42')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('bangumi-collection-artwork-43')),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.movie_creation_outlined), findsNWidgets(2));
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

  testWidgets('unconfigured Bangumi does not show a login action', (
    tester,
  ) async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final controller = BangumiSessionController(
      authentication: const UnavailableBangumiAuthentication(),
      store: DriftBangumiLocalStore(database),
      availability: BangumiAvailability.unavailable,
      clientFactory: (_) => throw StateError('client must not be created'),
    );
    await pumpApp(tester, size: const Size(1024, 768), bangumi: controller);

    expect(find.text('Bangumi is not enabled'), findsOneWidget);
    expect(find.byKey(const ValueKey('bangumi-login')), findsNothing);

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    expect(find.text('Bangumi is not enabled'), findsOneWidget);
    expect(find.byKey(const ValueKey('bangumi-settings-login')), findsNothing);
  });
}

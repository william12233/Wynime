import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/presentation/playback/player_page.dart';

void main() {
  testWidgets('HTTP 502 offers retry and line switch without source chrome', (
    tester,
  ) async {
    var retryCount = 0;
    var switchCount = 0;
    await tester.pumpWidget(
      _localizedApp(
        PlaybackFailurePanel(
          code: 'http_status_502',
          onRetry: () => retryCount++,
          onSwitchLine: () => switchCount++,
        ),
      ),
    );

    expect(find.text('This line is temporarily unavailable'), findsOneWidget);
    expect(
      find.text('The current line returned HTTP 502. Retry or switch lines.'),
      findsOneWidget,
    );
    expect(find.text('Retry'), findsOneWidget);
    expect(find.text('Switch line'), findsOneWidget);
    expect(find.text('稀飯動漫'), findsNothing);

    await tester.tap(find.text('Retry'));
    await tester.tap(find.text('Switch line'));
    expect(retryCount, 1);
    expect(switchCount, 1);
  });

  testWidgets('non-502 failure keeps the generic retry-only state', (
    tester,
  ) async {
    await tester.pumpWidget(
      _localizedApp(
        PlaybackFailurePanel(code: 'source_http_failed', onRetry: () {}),
      ),
    );

    expect(find.text('Playback source unavailable'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
    expect(find.text('Switch line'), findsNothing);
  });
}

Widget _localizedApp(Widget child) => MaterialApp(
  locale: const Locale('en'),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: child),
);

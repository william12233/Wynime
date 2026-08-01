import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/design_system/theme/wynime_theme.dart';
import 'package:wynime/src/presentation/shell/responsive_app_shell.dart';

class WynimeApp extends StatelessWidget {
  const WynimeApp({super.key, this.locale});

  final Locale? locale;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      locale: locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      theme: WynimeTheme.light(),
      darkTheme: WynimeTheme.dark(),
      themeMode: ThemeMode.system,
      home: const ResponsiveAppShell(),
    );
  }
}

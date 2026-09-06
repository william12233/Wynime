import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/domain/models/app_settings.dart';
import 'package:wynime/src/design_system/theme/wynime_theme.dart';
import 'package:wynime/src/presentation/shell/responsive_app_shell.dart';

class WynimeApp extends StatefulWidget {
  const WynimeApp({super.key, this.locale});

  final Locale? locale;

  @override
  State<WynimeApp> createState() => _WynimeAppState();
}

class _WynimeAppState extends State<WynimeApp> {
  late AppSettings _settings;

  @override
  void initState() {
    super.initState();
    _settings = AppSettings.defaults(DateTime.now());
  }

  void _updateSettings(AppSettings settings) {
    setState(() => _settings = settings);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      locale: widget.locale ?? _localeFor(_settings.interfaceLanguage),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      theme: WynimeTheme.light(),
      darkTheme: WynimeTheme.dark(),
      themeMode: _themeModeFor(_settings.theme),
      home: ResponsiveAppShell(
        settings: _settings,
        onSettingsChanged: _updateSettings,
      ),
    );
  }
}

Locale? _localeFor(InterfaceLanguagePreference preference) =>
    switch (preference) {
      InterfaceLanguagePreference.system => null,
      InterfaceLanguagePreference.zhHant => Locale.fromSubtags(
        languageCode: 'zh',
        scriptCode: 'Hant',
      ),
      InterfaceLanguagePreference.zhHans => Locale.fromSubtags(
        languageCode: 'zh',
        scriptCode: 'Hans',
      ),
      InterfaceLanguagePreference.ja => const Locale('ja'),
      InterfaceLanguagePreference.en => const Locale('en'),
    };

ThemeMode _themeModeFor(ThemePreference preference) => switch (preference) {
  ThemePreference.system => ThemeMode.system,
  ThemePreference.light => ThemeMode.light,
  ThemePreference.dark => ThemeMode.dark,
};

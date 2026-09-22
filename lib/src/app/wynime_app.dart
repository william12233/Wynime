import 'dart:async';

import 'package:flutter/material.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/application/source_installed_live_search_pipeline.dart';
import 'package:wynime/src/application/source_package_startup_controller.dart';
import 'package:wynime/src/application/source_registry_controller.dart';
import 'package:wynime/src/application/subject_source_playback_controller.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/domain/models/app_settings.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/repositories/watch_history_repository.dart';
import 'package:wynime/src/design_system/theme/wynime_theme.dart';
import 'package:wynime/src/presentation/shell/responsive_app_shell.dart';

class WynimeApp extends StatefulWidget {
  const WynimeApp({
    super.key,
    this.locale,
    this.bangumi,
    this.sourcePackages,
    this.sourceSearchPipeline,
    this.sourcePlaybackControllerFactory,
    this.sourceRegistry,
    this.watchHistory,
    this.softwareUpdates,
    this.onReady,
    this.onDispose,
  });

  final Locale? locale;
  final BangumiSessionController? bangumi;
  final SourcePackageStartupController? sourcePackages;
  final SourceInstalledLiveSearchPipeline? sourceSearchPipeline;
  final SubjectSourcePlaybackController Function(BangumiSubject subject)?
  sourcePlaybackControllerFactory;
  final SourceRegistryController? sourceRegistry;
  final WatchHistoryRepository? watchHistory;
  final SoftwareUpdateController? softwareUpdates;
  final Future<void> Function()? onReady;
  final VoidCallback? onDispose;

  @override
  State<WynimeApp> createState() => _WynimeAppState();
}

class _WynimeAppState extends State<WynimeApp> {
  late AppSettings _settings;

  @override
  void initState() {
    super.initState();
    _settings = AppSettings.defaults(DateTime.now());
    unawaited(_initializeServices());
  }

  Future<void> _initializeServices() async {
    await widget.bangumi?.initialize();
    await widget.sourcePackages?.initialize();
    unawaited(widget.sourceRegistry?.initialize());
    await widget.softwareUpdates?.initialize();
    final softwareUpdates = widget.softwareUpdates;
    if (softwareUpdates != null) {
      unawaited(_checkForUpdates(softwareUpdates));
    }
    await widget.onReady?.call();
  }

  Future<void> _checkForUpdates(SoftwareUpdateController controller) async {
    await controller.check(automatic: true);
  }

  @override
  void dispose() {
    // Mark the package controller closed synchronously, then let any already
    // queued durable mutation finish before the database is closed. This
    // avoids a lifecycle write racing the app-owned database teardown.
    final sourcePackagesClose = widget.sourcePackages?.close();
    unawaited(_disposeServices(sourcePackagesClose));
    super.dispose();
  }

  Future<void> _disposeServices(Future<void>? sourcePackagesClose) async {
    try {
      await widget.bangumi?.close();
    } finally {
      try {
        await sourcePackagesClose;
      } finally {
        widget.bangumi?.dispose();
        widget.sourcePackages?.dispose();
        widget.sourceRegistry?.dispose();
        widget.softwareUpdates?.dispose();
        widget.onDispose?.call();
      }
    }
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
        bangumi: widget.bangumi,
        sourcePackages: widget.sourcePackages,
        sourceSearchPipeline: widget.sourceSearchPipeline,
        sourcePlaybackControllerFactory: widget.sourcePlaybackControllerFactory,
        sourceRegistry: widget.sourceRegistry,
        watchHistory: widget.watchHistory,
        softwareUpdates: widget.softwareUpdates,
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

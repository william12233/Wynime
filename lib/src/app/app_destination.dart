import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';

enum AppDestination {
  home,
  search,
  library,
  downloads,
  sources,
  settings;

  IconData get icon => switch (this) {
    AppDestination.home => Icons.home_outlined,
    AppDestination.search => Icons.search_outlined,
    AppDestination.library => Icons.video_library_outlined,
    AppDestination.downloads => Icons.download_outlined,
    AppDestination.sources => Icons.hub_outlined,
    AppDestination.settings => Icons.settings_outlined,
  };

  IconData get selectedIcon => switch (this) {
    AppDestination.home => Icons.home,
    AppDestination.search => Icons.search,
    AppDestination.library => Icons.video_library,
    AppDestination.downloads => Icons.download,
    AppDestination.sources => Icons.hub,
    AppDestination.settings => Icons.settings,
  };

  String label(AppLocalizations localizations) => switch (this) {
    AppDestination.home => localizations.navigationHome,
    AppDestination.search => localizations.navigationSearch,
    AppDestination.library => localizations.navigationLibrary,
    AppDestination.downloads => localizations.navigationDownloads,
    AppDestination.sources => localizations.navigationSources,
    AppDestination.settings => localizations.navigationSettings,
  };

  String description(AppLocalizations localizations) => switch (this) {
    AppDestination.home => localizations.homePlaceholder,
    AppDestination.search => localizations.searchPlaceholder,
    AppDestination.library => localizations.libraryPlaceholder,
    AppDestination.downloads => localizations.downloadsPlaceholder,
    AppDestination.sources => localizations.sourcesPlaceholder,
    AppDestination.settings => localizations.settingsPlaceholder,
  };
}

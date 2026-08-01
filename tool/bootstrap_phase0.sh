#!/usr/bin/env bash
set -uo pipefail

FLUTTER_VERSION="3.44.8"
FLUTTER_SHA256="672089e001571a9fbb209a495c583580c0c6c73ef98999264ba07fa93ace332d"
FLUTTER_ARCHIVE="flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
FLUTTER_URL="https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/${FLUTTER_ARCHIVE}"
WORK_ROOT="${RUNNER_TEMP:-/tmp}/wynime-phase0"
SDK_ROOT="${WORK_ROOT}/sdk"
TEMPLATE_ROOT="${WORK_ROOT}/template"
LOG_FILE="docs/phase0-bootstrap.log"
RESULTS_FILE="${WORK_ROOT}/results.txt"
OVERALL_STATUS="PASS"

rm -rf "${WORK_ROOT}"
mkdir -p "${SDK_ROOT}" "${TEMPLATE_ROOT}" docs
: > "${LOG_FILE}"
: > "${RESULTS_FILE}"

record() {
  printf '%s\n' "$*" | tee -a "${LOG_FILE}"
}

run_check() {
  local name="$1"
  shift
  record ""
  record "## ${name}"
  if "$@" >>"${LOG_FILE}" 2>&1; then
    printf '%s|PASS\n' "${name}" >>"${RESULTS_FILE}"
    record "${name}: PASS"
  else
    local status=$?
    printf '%s|FAIL (%s)\n' "${name}" "${status}" >>"${RESULTS_FILE}"
    record "${name}: FAIL (${status})"
    OVERALL_STATUS="FAIL"
  fi
}

record "Downloading Flutter ${FLUTTER_VERSION} stable from the official Flutter release archive."
curl --fail --location --retry 3 --output "${WORK_ROOT}/${FLUTTER_ARCHIVE}" "${FLUTTER_URL}" >>"${LOG_FILE}" 2>&1
printf '%s  %s\n' "${FLUTTER_SHA256}" "${WORK_ROOT}/${FLUTTER_ARCHIVE}" | sha256sum --check - >>"${LOG_FILE}" 2>&1
tar -xf "${WORK_ROOT}/${FLUTTER_ARCHIVE}" -C "${SDK_ROOT}"
export PATH="${SDK_ROOT}/flutter/bin:${PATH}"

flutter config --no-analytics >>"${LOG_FILE}" 2>&1
dart --disable-analytics >>"${LOG_FILE}" 2>&1 || true
flutter --version >>"${LOG_FILE}" 2>&1

flutter create \
  --platforms=android,windows \
  --org io.github.william12233 \
  --project-name wynime \
  --description "Wynime cross-platform animation source player." \
  "${TEMPLATE_ROOT}" >>"${LOG_FILE}" 2>&1

rm -rf android windows
cp -R "${TEMPLATE_ROOT}/android" android
cp -R "${TEMPLATE_ROOT}/windows" windows
cp "${TEMPLATE_ROOT}/.metadata" .metadata
cp "${TEMPLATE_ROOT}/.gitignore" .gitignore
cp "${TEMPLATE_ROOT}/analysis_options.yaml" analysis_options.yaml
cp "${TEMPLATE_ROOT}/pubspec.yaml" pubspec.yaml

python3 - <<'PY'
from pathlib import Path

pubspec = Path("pubspec.yaml")
text = pubspec.read_text(encoding="utf-8")
dependency_anchor = "  flutter:\n    sdk: flutter\n"
if dependency_anchor not in text:
    raise SystemExit("Generated pubspec dependency anchor was not found.")
text = text.replace(
    dependency_anchor,
    dependency_anchor
    + "  flutter_localizations:\n"
    + "    sdk: flutter\n"
    + "  intl: any\n",
    1,
)
flutter_anchor = "\nflutter:\n"
if flutter_anchor not in text:
    raise SystemExit("Generated pubspec Flutter section was not found.")
text = text.replace(flutter_anchor, "\nflutter:\n  generate: true\n", 1)
pubspec.write_text(text, encoding="utf-8")

gitignore = Path(".gitignore")
ignore_text = gitignore.read_text(encoding="utf-8")
ignore_text += (
    "\n# Generated localization sources\n"
    "/lib/l10n/app_localizations*.dart\n"
    "\n# Phase 0 bootstrap command log\n"
    "/docs/phase0-bootstrap.log\n"
)
gitignore.write_text(ignore_text, encoding="utf-8")

manifest = Path("android/app/src/main/AndroidManifest.xml")
manifest_text = manifest.read_text(encoding="utf-8")
manifest_text = manifest_text.replace('android:label="wynime"', 'android:label="Wynime"')
manifest.write_text(manifest_text, encoding="utf-8")

main_cpp = Path("windows/runner/main.cpp")
main_cpp_text = main_cpp.read_text(encoding="utf-8")
main_cpp_text = main_cpp_text.replace('L"wynime"', 'L"Wynime"', 1)
main_cpp.write_text(main_cpp_text, encoding="utf-8")

runner_rc = Path("windows/runner/Runner.rc")
runner_rc_text = runner_rc.read_text(encoding="utf-8")
runner_rc_text = runner_rc_text.replace(
    'VALUE "FileDescription", "wynime" "\\0"',
    'VALUE "FileDescription", "Wynime" "\\0"',
)
runner_rc_text = runner_rc_text.replace(
    'VALUE "ProductName", "wynime" "\\0"',
    'VALUE "ProductName", "Wynime" "\\0"',
)
runner_rc.write_text(runner_rc_text, encoding="utf-8")
PY

rm -rf lib test
mkdir -p .
cat > l10n.yaml <<'WYNIME_EOF_L10N_YAML'
arb-dir: lib/l10n
template-arb-file: app_en.arb
output-localization-file: app_localizations.dart
output-class: AppLocalizations
synthetic-package: false
nullable-getter: false
required-resource-attributes: true
format: true
WYNIME_EOF_L10N_YAML
mkdir -p lib
cat > lib/main.dart <<'WYNIME_EOF_LIB_MAIN_DART'
import 'package:flutter/widgets.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const WynimeApp());
}
WYNIME_EOF_LIB_MAIN_DART
mkdir -p lib/src/app
cat > lib/src/app/wynime_app.dart <<'WYNIME_EOF_LIB_SRC_APP_WYNIME_APP_DART'
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
WYNIME_EOF_LIB_SRC_APP_WYNIME_APP_DART
mkdir -p lib/src/app
cat > lib/src/app/app_destination.dart <<'WYNIME_EOF_LIB_SRC_APP_APP_DESTINATION_DART'
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
WYNIME_EOF_LIB_SRC_APP_APP_DESTINATION_DART
mkdir -p lib/src/design_system/tokens
cat > lib/src/design_system/tokens/breakpoints.dart <<'WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_BREAKPOINTS_DART'
enum WynimeWindowClass { compact, medium, expanded }

abstract final class WynimeBreakpoints {
  static const double compactUpperBound = 600;
  static const double mediumUpperBound = 1024;

  static WynimeWindowClass classify(double logicalWidth) {
    assert(logicalWidth >= 0, 'logicalWidth must not be negative.');
    if (logicalWidth < compactUpperBound) {
      return WynimeWindowClass.compact;
    }
    if (logicalWidth < mediumUpperBound) {
      return WynimeWindowClass.medium;
    }
    return WynimeWindowClass.expanded;
  }
}
WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_BREAKPOINTS_DART
mkdir -p lib/src/design_system/tokens
cat > lib/src/design_system/tokens/spacing.dart <<'WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_SPACING_DART'
abstract final class WynimeSpacing {
  static const double xxs = 4;
  static const double xs = 8;
  static const double sm = 12;
  static const double md = 16;
  static const double lg = 24;
  static const double xl = 32;
  static const double xxl = 48;
}
WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_SPACING_DART
mkdir -p lib/src/design_system/tokens
cat > lib/src/design_system/tokens/radii.dart <<'WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_RADII_DART'
abstract final class WynimeRadii {
  static const double small = 8;
  static const double medium = 12;
  static const double large = 20;
  static const double pill = 999;
}
WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_RADII_DART
mkdir -p lib/src/design_system/tokens
cat > lib/src/design_system/tokens/motion.dart <<'WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_MOTION_DART'
import 'package:flutter/animation.dart';

abstract final class WynimeMotion {
  static const Duration fast = Duration(milliseconds: 120);
  static const Duration standard = Duration(milliseconds: 220);
  static const Duration emphasized = Duration(milliseconds: 320);
  static const Curve standardCurve = Curves.easeOutCubic;
}
WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_MOTION_DART
mkdir -p lib/src/design_system/tokens
cat > lib/src/design_system/tokens/dimensions.dart <<'WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_DIMENSIONS_DART'
abstract final class WynimeDimensions {
  static const double mediumRailWidth = 80;
  static const double expandedRailWidth = 240;
  static const double contentMaxWidth = 1200;
  static const double minimumTouchTarget = 48;
}
WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_TOKENS_DIMENSIONS_DART
mkdir -p lib/src/design_system/theme
cat > lib/src/design_system/theme/wynime_theme.dart <<'WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_THEME_WYNIME_THEME_DART'
import 'package:flutter/material.dart';
import 'package:wynime/src/design_system/tokens/radii.dart';

abstract final class WynimeTheme {
  static const Color _seed = Color(0xFF5267A8);

  static ThemeData light() => _build(Brightness.light);

  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: _seed,
      brightness: brightness,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: colorScheme.surface,
      cardTheme: CardThemeData(
        elevation: 0,
        color: colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(WynimeRadii.large),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        elevation: 0,
        backgroundColor: colorScheme.surfaceContainer,
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: colorScheme.surfaceContainer,
        indicatorShape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(WynimeRadii.medium),
        ),
      ),
    );
  }
}
WYNIME_EOF_LIB_SRC_DESIGN_SYSTEM_THEME_WYNIME_THEME_DART
mkdir -p lib/src/presentation/pages
cat > lib/src/presentation/pages/placeholder_page.dart <<'WYNIME_EOF_LIB_SRC_PRESENTATION_PAGES_PLACEHOLDER_PAGE_DART'
import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/design_system/tokens/spacing.dart';

class PlaceholderPage extends StatelessWidget {
  const PlaceholderPage({
    required this.icon,
    required this.title,
    required this.description,
    super.key,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(WynimeSpacing.lg),
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: WynimeDimensions.contentMaxWidth,
            ),
            child: Semantics(
              container: true,
              header: true,
              label: title,
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(WynimeSpacing.xl),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        icon,
                        size: 56,
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(height: WynimeSpacing.lg),
                      Text(
                        title,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineMedium,
                      ),
                      const SizedBox(height: WynimeSpacing.sm),
                      Text(
                        description,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyLarge?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: WynimeSpacing.lg),
                      Chip(
                        avatar: const Icon(Icons.construction, size: 18),
                        label: Text(localizations.phaseZeroLabel),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
WYNIME_EOF_LIB_SRC_PRESENTATION_PAGES_PLACEHOLDER_PAGE_DART
mkdir -p lib/src/presentation/shell
cat > lib/src/presentation/shell/responsive_app_shell.dart <<'WYNIME_EOF_LIB_SRC_PRESENTATION_SHELL_RESPONSIVE_APP_SHELL_DART'
import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/app/app_destination.dart';
import 'package:wynime/src/design_system/tokens/breakpoints.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/presentation/pages/placeholder_page.dart';

class ResponsiveAppShell extends StatefulWidget {
  const ResponsiveAppShell({super.key});

  @override
  State<ResponsiveAppShell> createState() => _ResponsiveAppShellState();
}

class _ResponsiveAppShellState extends State<ResponsiveAppShell> {
  int _selectedIndex = 0;

  void _selectDestination(int index) {
    if (_selectedIndex == index) {
      return;
    }
    setState(() => _selectedIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final destinations = AppDestination.values;
    final selectedDestination = destinations[_selectedIndex];

    final page = PlaceholderPage(
      key: ValueKey(selectedDestination),
      icon: selectedDestination.selectedIcon,
      title: selectedDestination.label(localizations),
      description: selectedDestination.description(localizations),
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final windowClass = WynimeBreakpoints.classify(constraints.maxWidth);

        return switch (windowClass) {
          WynimeWindowClass.compact => Scaffold(
              appBar: AppBar(
                title: Text(selectedDestination.label(localizations)),
              ),
              body: page,
              bottomNavigationBar: NavigationBar(
                selectedIndex: _selectedIndex,
                labelBehavior:
                    NavigationDestinationLabelBehavior.onlyShowSelected,
                onDestinationSelected: _selectDestination,
                destinations: [
                  for (final destination in destinations)
                    NavigationDestination(
                      icon: Icon(destination.icon),
                      selectedIcon: Icon(destination.selectedIcon),
                      label: destination.label(localizations),
                    ),
                ],
              ),
            ),
          WynimeWindowClass.medium => _RailShell(
              selectedIndex: _selectedIndex,
              onDestinationSelected: _selectDestination,
              destinations: destinations,
              localizations: localizations,
              page: page,
              extended: false,
            ),
          WynimeWindowClass.expanded => _RailShell(
              selectedIndex: _selectedIndex,
              onDestinationSelected: _selectDestination,
              destinations: destinations,
              localizations: localizations,
              page: page,
              extended: true,
            ),
        };
      },
    );
  }
}

class _RailShell extends StatelessWidget {
  const _RailShell({
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
    required this.localizations,
    required this.page,
    required this.extended,
  });

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<AppDestination> destinations;
  final AppLocalizations localizations;
  final Widget page;
  final bool extended;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: [
          SizedBox(
            width: extended
                ? WynimeDimensions.expandedRailWidth
                : WynimeDimensions.mediumRailWidth,
            child: NavigationRail(
              extended: extended,
              minExtendedWidth: WynimeDimensions.expandedRailWidth,
              selectedIndex: selectedIndex,
              onDestinationSelected: onDestinationSelected,
              labelType:
                  extended ? NavigationRailLabelType.none : NavigationRailLabelType.selected,
              leading: Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Tooltip(
                  message: localizations.appTitle,
                  child: const Icon(Icons.play_circle_fill_rounded, size: 32),
                ),
              ),
              destinations: [
                for (final destination in destinations)
                  NavigationRailDestination(
                    icon: Icon(destination.icon),
                    selectedIcon: Icon(destination.selectedIcon),
                    label: Text(destination.label(localizations)),
                  ),
              ],
            ),
          ),
          const VerticalDivider(width: 1),
          Expanded(child: page),
        ],
      ),
    );
  }
}
WYNIME_EOF_LIB_SRC_PRESENTATION_SHELL_RESPONSIVE_APP_SHELL_DART
mkdir -p lib/src/domain/models
cat > lib/src/domain/models/manifest_fingerprint.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_MANIFEST_FINGERPRINT_DART'
final class ManifestFingerprint {
  ManifestFingerprint({required this.algorithm, required this.value})
      : assert(algorithm.trim().isNotEmpty, 'algorithm must not be empty.'),
        assert(value.trim().isNotEmpty, 'value must not be empty.');

  final String algorithm;
  final String value;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ManifestFingerprint &&
          algorithm == other.algorithm &&
          value == other.value;

  @override
  int get hashCode => Object.hash(algorithm, value);

  @override
  String toString() => '$algorithm:$value';
}
WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_MANIFEST_FINGERPRINT_DART
mkdir -p lib/src/domain/models
cat > lib/src/domain/models/source_identity.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_SOURCE_IDENTITY_DART'
final class SourceEpisodeIdentity {
  SourceEpisodeIdentity({
    required this.sourceId,
    required this.lineId,
    required this.subjectId,
    required this.episodeId,
  })  : assert(sourceId.trim().isNotEmpty, 'sourceId must not be empty.'),
        assert(lineId.trim().isNotEmpty, 'lineId must not be empty.'),
        assert(subjectId.trim().isNotEmpty, 'subjectId must not be empty.'),
        assert(episodeId.trim().isNotEmpty, 'episodeId must not be empty.');

  final String sourceId;
  final String lineId;
  final String subjectId;
  final String episodeId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SourceEpisodeIdentity &&
          sourceId == other.sourceId &&
          lineId == other.lineId &&
          subjectId == other.subjectId &&
          episodeId == other.episodeId;

  @override
  int get hashCode => Object.hash(sourceId, lineId, subjectId, episodeId);
}
WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_SOURCE_IDENTITY_DART
mkdir -p lib/src/domain/models
cat > lib/src/domain/models/ad_removal_plan.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_AD_REMOVAL_PLAN_DART'
import 'dart:collection';

import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

enum AdRemovalMode { off, safe, smart, aggressive }

final class AdRemovalPlanKey {
  const AdRemovalPlanKey({
    required this.episode,
    required this.manifestFingerprint,
  });

  final SourceEpisodeIdentity episode;
  final ManifestFingerprint manifestFingerprint;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AdRemovalPlanKey &&
          episode == other.episode &&
          manifestFingerprint == other.manifestFingerprint;

  @override
  int get hashCode => Object.hash(episode, manifestFingerprint);
}

final class MediaRange {
  MediaRange({required this.start, required this.end})
      : assert(!start.isNegative, 'start must not be negative.'),
        assert(end > start, 'end must be after start.');

  final Duration start;
  final Duration end;
}

final class TimelineMapEntry {
  TimelineMapEntry({
    required this.sourceStart,
    required this.sourceEnd,
    required this.outputStart,
  })  : assert(!sourceStart.isNegative, 'sourceStart must not be negative.'),
        assert(sourceEnd > sourceStart, 'sourceEnd must be after sourceStart.'),
        assert(!outputStart.isNegative, 'outputStart must not be negative.');

  final Duration sourceStart;
  final Duration sourceEnd;
  final Duration outputStart;
}

final class AdRemovalPlan {
  AdRemovalPlan({
    required this.key,
    required this.mode,
    Iterable<MediaRange> removedRanges = const [],
    Iterable<TimelineMapEntry> timeline = const [],
  })  : removedRanges = UnmodifiableListView(removedRanges),
        timeline = UnmodifiableListView(timeline);

  final AdRemovalPlanKey key;
  final AdRemovalMode mode;
  final UnmodifiableListView<MediaRange> removedRanges;
  final UnmodifiableListView<TimelineMapEntry> timeline;
}
WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_AD_REMOVAL_PLAN_DART
mkdir -p lib/src/domain/models
cat > lib/src/domain/models/playback_session.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_PLAYBACK_SESSION_DART'
import 'dart:collection';

import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

typedef PlaybackSessionRefresher = Future<PlaybackSession> Function();

final class MediaTrack {
  MediaTrack({
    required this.id,
    required this.label,
    this.languageCode,
  })  : assert(id.trim().isNotEmpty, 'id must not be empty.'),
        assert(label.trim().isNotEmpty, 'label must not be empty.');

  final String id;
  final String label;
  final String? languageCode;
}

final class PlaybackSession {
  PlaybackSession({
    required this.sessionId,
    required this.episode,
    required this.mediaUri,
    required this.pageUri,
    required this.adRemovalPlan,
    Map<String, String> headers = const {},
    Map<String, String> cookies = const {},
    this.referer,
    this.origin,
    this.userAgent,
    this.expiresAt,
    Iterable<MediaTrack> subtitles = const [],
    Iterable<MediaTrack> audioTracks = const [],
    this.refresh,
  })  : assert(sessionId.trim().isNotEmpty, 'sessionId must not be empty.'),
        assert(
          adRemovalPlan.key.episode == episode,
          'AdRemovalPlan must describe the same source, line, subject, and episode.',
        ),
        headers = UnmodifiableMapView(headers),
        cookies = UnmodifiableMapView(cookies),
        subtitles = UnmodifiableListView(subtitles),
        audioTracks = UnmodifiableListView(audioTracks);

  final String sessionId;
  final SourceEpisodeIdentity episode;
  final Uri mediaUri;
  final Uri pageUri;
  final UnmodifiableMapView<String, String> headers;
  final UnmodifiableMapView<String, String> cookies;
  final Uri? referer;
  final Uri? origin;
  final String? userAgent;
  final DateTime? expiresAt;
  final UnmodifiableListView<MediaTrack> subtitles;
  final UnmodifiableListView<MediaTrack> audioTracks;
  final AdRemovalPlan adRemovalPlan;
  final PlaybackSessionRefresher? refresh;

  bool get isExpired =>
      expiresAt != null && !expiresAt!.isAfter(DateTime.now().toUtc());
}
WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_PLAYBACK_SESSION_DART
mkdir -p lib/src/domain/models
cat > lib/src/domain/models/download_artifact_manifest.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_DOWNLOAD_ARTIFACT_MANIFEST_DART'
import 'dart:collection';

enum DownloadArtifactKind {
  finalVideo,
  temporarySegment,
  manifestSnapshot,
  subtitle,
  audioTrack,
  cover,
  adRemovalPlan,
  timelineMap,
  remuxTemporaryFile,
  diagnosticLog,
  recoveryState,
}

final class DownloadArtifact {
  DownloadArtifact({
    required this.artifactId,
    required this.kind,
    required this.fileUri,
  })  : assert(artifactId.trim().isNotEmpty, 'artifactId must not be empty.'),
        assert(fileUri.scheme == 'file', 'fileUri must use the file scheme.');

  final String artifactId;
  final DownloadArtifactKind kind;
  final Uri fileUri;
}

final class DownloadArtifactManifest {
  DownloadArtifactManifest({
    required this.manifestId,
    required this.downloadId,
    required this.createdAt,
    required Iterable<DownloadArtifact> artifacts,
  })  : assert(manifestId.trim().isNotEmpty, 'manifestId must not be empty.'),
        assert(downloadId.trim().isNotEmpty, 'downloadId must not be empty.'),
        artifacts = UnmodifiableListView(_validateArtifacts(artifacts));

  final String manifestId;
  final String downloadId;
  final DateTime createdAt;
  final UnmodifiableListView<DownloadArtifact> artifacts;

  static List<DownloadArtifact> _validateArtifacts(
    Iterable<DownloadArtifact> artifacts,
  ) {
    final result = List<DownloadArtifact>.unmodifiable(artifacts);
    final artifactIds = <String>{};
    final fileUris = <Uri>{};

    for (final artifact in result) {
      if (!artifactIds.add(artifact.artifactId)) {
        throw ArgumentError.value(
          artifact.artifactId,
          'artifacts',
          'Artifact IDs must be unique.',
        );
      }
      if (!fileUris.add(artifact.fileUri)) {
        throw ArgumentError.value(
          artifact.fileUri,
          'artifacts',
          'Every physical artifact path must be registered only once.',
        );
      }
    }

    return result;
  }
}
WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_DOWNLOAD_ARTIFACT_MANIFEST_DART
mkdir -p lib/src/domain/models
cat > lib/src/domain/models/delete_job.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_DELETE_JOB_DART'
enum DeleteJobStatus { pending, running, failed, completed }

final class DeleteJob {
  DeleteJob({
    required this.jobId,
    required this.artifactManifestId,
    required this.status,
    this.attempts = 0,
    this.failureCode,
  })  : assert(jobId.trim().isNotEmpty, 'jobId must not be empty.'),
        assert(
          artifactManifestId.trim().isNotEmpty,
          'artifactManifestId must not be empty.',
        ),
        assert(attempts >= 0, 'attempts must not be negative.'),
        assert(
          status == DeleteJobStatus.failed || failureCode == null,
          'failureCode is only valid for failed jobs.',
        );

  final String jobId;
  final String artifactManifestId;
  final DeleteJobStatus status;
  final int attempts;
  final String? failureCode;
}
WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_DELETE_JOB_DART
mkdir -p lib/src/domain/models
cat > lib/src/domain/models/source_models.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_SOURCE_MODELS_DART'
import 'package:wynime/src/domain/models/source_identity.dart';

final class SourceSearchResult {
  SourceSearchResult({
    required this.sourceId,
    required this.subjectId,
    required this.title,
  })  : assert(sourceId.trim().isNotEmpty, 'sourceId must not be empty.'),
        assert(subjectId.trim().isNotEmpty, 'subjectId must not be empty.'),
        assert(title.trim().isNotEmpty, 'title must not be empty.');

  final String sourceId;
  final String subjectId;
  final String title;
}

final class SourceEpisode {
  SourceEpisode({required this.identity, required this.title})
      : assert(title.trim().isNotEmpty, 'title must not be empty.');

  final SourceEpisodeIdentity identity;
  final String title;
}
WYNIME_EOF_LIB_SRC_DOMAIN_MODELS_SOURCE_MODELS_DART
mkdir -p lib/src/domain/services
cat > lib/src/domain/services/source_adapter.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_SERVICES_SOURCE_ADAPTER_DART'
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_models.dart';

final class SourceCapabilities {
  const SourceCapabilities({
    required this.supportsSearch,
    required this.requiresDynamicPageResolution,
  });

  final bool supportsSearch;
  final bool requiresDynamicPageResolution;
}

abstract interface class SourceAdapter {
  String get sourceId;

  SourceCapabilities get capabilities;

  Future<List<SourceSearchResult>> search(String query);

  Future<PlaybackSession> resolve(SourceEpisode episode);
}
WYNIME_EOF_LIB_SRC_DOMAIN_SERVICES_SOURCE_ADAPTER_DART
mkdir -p lib/src/domain/services
cat > lib/src/domain/services/player_backend.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_SERVICES_PLAYER_BACKEND_DART'
import 'package:wynime/src/domain/models/playback_session.dart';

enum PlayerBackendKind { media3, mpv, webView }

abstract interface class PlayerBackend {
  String get backendId;

  PlayerBackendKind get kind;

  Future<void> open(PlaybackSession session);

  Future<void> pause();

  Future<void> seek(Duration position);

  Future<void> close();
}
WYNIME_EOF_LIB_SRC_DOMAIN_SERVICES_PLAYER_BACKEND_DART
mkdir -p lib/src/domain/repositories
cat > lib/src/domain/repositories/download_repository.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_REPOSITORIES_DOWNLOAD_REPOSITORY_DART'
import 'package:wynime/src/domain/models/delete_job.dart';
import 'package:wynime/src/domain/models/download_artifact_manifest.dart';
import 'package:wynime/src/domain/models/playback_session.dart';

abstract interface class DownloadRepository {
  Future<String> enqueue(PlaybackSession session);

  Future<DownloadArtifactManifest?> findArtifactManifest(String downloadId);

  Future<DeleteJob> requestDeletion(String artifactManifestId);
}
WYNIME_EOF_LIB_SRC_DOMAIN_REPOSITORIES_DOWNLOAD_REPOSITORY_DART
mkdir -p lib/src/domain/repositories
cat > lib/src/domain/repositories/bangumi_repository.dart <<'WYNIME_EOF_LIB_SRC_DOMAIN_REPOSITORIES_BANGUMI_REPOSITORY_DART'
enum BangumiCollectionStatus { wish, watching, completed, onHold, dropped }

final class BangumiEpisodeProgress {
  const BangumiEpisodeProgress({
    required this.subjectId,
    required this.watchedEpisodeIds,
  });

  final String subjectId;
  final Set<String> watchedEpisodeIds;
}

abstract interface class BangumiRepository {
  Future<BangumiEpisodeProgress?> loadEpisodeProgress(String subjectId);

  Future<void> saveCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  );

  Future<void> markEpisodeWatched(String subjectId, String episodeId);
}
WYNIME_EOF_LIB_SRC_DOMAIN_REPOSITORIES_BANGUMI_REPOSITORY_DART
mkdir -p lib/l10n
cat > lib/l10n/app_en.arb <<'WYNIME_EOF_LIB_L10N_APP_EN_ARB'
{
  "@@locale": "en",
  "appTitle": "Wynime",
  "@appTitle": {"description": "Application title."},
  "phaseZeroLabel": "Phase 0 scaffold",
  "@phaseZeroLabel": {"description": "Label indicating that a screen is a Phase 0 placeholder."},
  "navigationHome": "Home",
  "@navigationHome": {"description": "Home navigation destination."},
  "navigationSearch": "Search",
  "@navigationSearch": {"description": "Search navigation destination."},
  "navigationLibrary": "Library",
  "@navigationLibrary": {"description": "Library navigation destination."},
  "navigationDownloads": "Downloads",
  "@navigationDownloads": {"description": "Downloads navigation destination."},
  "navigationSources": "Sources",
  "@navigationSources": {"description": "Sources navigation destination."},
  "navigationSettings": "Settings",
  "@navigationSettings": {"description": "Settings navigation destination."},
  "homePlaceholder": "The future home dashboard will surface schedules, continue-watching items, and configurable modules.",
  "@homePlaceholder": {"description": "Phase 0 home page placeholder description."},
  "searchPlaceholder": "Search UI is scaffolded only. No website or source is queried in Phase 0.",
  "@searchPlaceholder": {"description": "Phase 0 search page placeholder description."},
  "libraryPlaceholder": "Collection and watch-history presentation will be connected in a later phase.",
  "@libraryPlaceholder": {"description": "Phase 0 library page placeholder description."},
  "downloadsPlaceholder": "Download presentation is present, but no media download implementation exists yet.",
  "@downloadsPlaceholder": {"description": "Phase 0 downloads page placeholder description."},
  "sourcesPlaceholder": "Source management is a placeholder. Source packages and live parsing are disabled.",
  "@sourcesPlaceholder": {"description": "Phase 0 sources page placeholder description."},
  "settingsPlaceholder": "Settings categories will be added behind typed policies in later phases.",
  "@settingsPlaceholder": {"description": "Phase 0 settings page placeholder description."}
}
WYNIME_EOF_LIB_L10N_APP_EN_ARB
mkdir -p lib/l10n
cat > lib/l10n/app_zh_Hant.arb <<'WYNIME_EOF_LIB_L10N_APP_ZH_HANT_ARB'
{
  "@@locale": "zh_Hant",
  "appTitle": "Wynime",
  "phaseZeroLabel": "Phase 0 專案骨架",
  "navigationHome": "首頁",
  "navigationSearch": "搜尋",
  "navigationLibrary": "收藏庫",
  "navigationDownloads": "下載",
  "navigationSources": "來源",
  "navigationSettings": "設定",
  "homePlaceholder": "未來首頁會整合放送資訊、繼續觀看與可自訂模組。",
  "searchPlaceholder": "目前僅建立搜尋介面骨架；Phase 0 不會查詢任何網站或來源。",
  "libraryPlaceholder": "收藏與觀看紀錄會在後續階段接入。",
  "downloadsPlaceholder": "目前只有下載介面，尚未加入任何真實媒體下載實作。",
  "sourcesPlaceholder": "來源管理目前為預留頁面；來源套件與即時解析均未啟用。",
  "settingsPlaceholder": "後續階段會透過型別化政策逐步加入設定分類。"
}
WYNIME_EOF_LIB_L10N_APP_ZH_HANT_ARB
mkdir -p lib/l10n
cat > lib/l10n/app_zh_Hans.arb <<'WYNIME_EOF_LIB_L10N_APP_ZH_HANS_ARB'
{
  "@@locale": "zh_Hans",
  "appTitle": "Wynime",
  "phaseZeroLabel": "Phase 0 项目骨架",
  "navigationHome": "首页",
  "navigationSearch": "搜索",
  "navigationLibrary": "收藏库",
  "navigationDownloads": "下载",
  "navigationSources": "来源",
  "navigationSettings": "设置",
  "homePlaceholder": "未来首页会整合放送信息、继续观看与可自定义模块。",
  "searchPlaceholder": "目前仅建立搜索界面骨架；Phase 0 不会查询任何网站或来源。",
  "libraryPlaceholder": "收藏与观看记录会在后续阶段接入。",
  "downloadsPlaceholder": "目前只有下载界面，尚未加入任何真实媒体下载实现。",
  "sourcesPlaceholder": "来源管理目前为预留页面；来源包与实时解析均未启用。",
  "settingsPlaceholder": "后续阶段会通过类型化策略逐步加入设置分类。"
}
WYNIME_EOF_LIB_L10N_APP_ZH_HANS_ARB
mkdir -p lib/l10n
cat > lib/l10n/app_ja.arb <<'WYNIME_EOF_LIB_L10N_APP_JA_ARB'
{
  "@@locale": "ja",
  "appTitle": "Wynime",
  "phaseZeroLabel": "Phase 0 スキャフォールド",
  "navigationHome": "ホーム",
  "navigationSearch": "検索",
  "navigationLibrary": "ライブラリ",
  "navigationDownloads": "ダウンロード",
  "navigationSources": "ソース",
  "navigationSettings": "設定",
  "homePlaceholder": "今後、放送情報、視聴再開項目、カスタマイズ可能なモジュールを表示します。",
  "searchPlaceholder": "現在は検索 UI の骨格のみです。Phase 0 ではサイトやソースへ接続しません。",
  "libraryPlaceholder": "コレクションと視聴履歴は後続フェーズで接続します。",
  "downloadsPlaceholder": "ダウンロード画面のみを用意し、実際のメディア取得はまだ実装していません。",
  "sourcesPlaceholder": "ソース管理はプレースホルダーです。ソースパッケージと実サイト解析は無効です。",
  "settingsPlaceholder": "設定項目は後続フェーズで型付きポリシーの背後に追加します。"
}
WYNIME_EOF_LIB_L10N_APP_JA_ARB
mkdir -p test/design_system
cat > test/design_system/breakpoints_test.dart <<'WYNIME_EOF_TEST_DESIGN_SYSTEM_BREAKPOINTS_TEST_DART'
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/design_system/tokens/breakpoints.dart';

void main() {
  group('WynimeBreakpoints', () {
    test('classifies exact boundaries authoritatively', () {
      expect(
        WynimeBreakpoints.classify(599.99),
        WynimeWindowClass.compact,
      );
      expect(WynimeBreakpoints.classify(600), WynimeWindowClass.medium);
      expect(
        WynimeBreakpoints.classify(1023.99),
        WynimeWindowClass.medium,
      );
      expect(WynimeBreakpoints.classify(1024), WynimeWindowClass.expanded);
    });
  });
}
WYNIME_EOF_TEST_DESIGN_SYSTEM_BREAKPOINTS_TEST_DART
mkdir -p test/domain
cat > test/domain/ad_removal_plan_test.dart <<'WYNIME_EOF_TEST_DOMAIN_AD_REMOVAL_PLAN_TEST_DART'
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

void main() {
  test('ad plan identity includes source, line, subject, episode, and manifest', () {
    final episode = SourceEpisodeIdentity(
      sourceId: 'source-a',
      lineId: 'line-1',
      subjectId: 'subject-9',
      episodeId: 'episode-3',
    );

    final first = AdRemovalPlanKey(
      episode: episode,
      manifestFingerprint: ManifestFingerprint(
        algorithm: 'sha256',
        value: 'manifest-a',
      ),
    );
    final second = AdRemovalPlanKey(
      episode: episode,
      manifestFingerprint: ManifestFingerprint(
        algorithm: 'sha256',
        value: 'manifest-b',
      ),
    );

    expect(first, isNot(second));
    expect(first.episode.sourceId, 'source-a');
    expect(first.episode.lineId, 'line-1');
    expect(first.episode.subjectId, 'subject-9');
    expect(first.episode.episodeId, 'episode-3');
  });
}
WYNIME_EOF_TEST_DOMAIN_AD_REMOVAL_PLAN_TEST_DART
mkdir -p test/domain
cat > test/domain/download_artifact_manifest_test.dart <<'WYNIME_EOF_TEST_DOMAIN_DOWNLOAD_ARTIFACT_MANIFEST_TEST_DART'
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/delete_job.dart';
import 'package:wynime/src/domain/models/download_artifact_manifest.dart';

void main() {
  test('artifact manifest keeps the exact registered file inventory', () {
    final manifest = DownloadArtifactManifest(
      manifestId: 'manifest-1',
      downloadId: 'download-1',
      createdAt: DateTime.utc(2026),
      artifacts: [
        DownloadArtifact(
          artifactId: 'video',
          kind: DownloadArtifactKind.finalVideo,
          fileUri: Uri.file('/downloads/video.mp4'),
        ),
        DownloadArtifact(
          artifactId: 'subtitle',
          kind: DownloadArtifactKind.subtitle,
          fileUri: Uri.file('/downloads/video.zh-Hant.ass'),
        ),
      ],
    );

    expect(
      manifest.artifacts.map((artifact) => artifact.fileUri.path),
      ['/downloads/video.mp4', '/downloads/video.zh-Hant.ass'],
    );
    expect(
      () => manifest.artifacts.add(
        DownloadArtifact(
          artifactId: 'unexpected',
          kind: DownloadArtifactKind.diagnosticLog,
          fileUri: Uri.file('/downloads/unexpected.log'),
        ),
      ),
      throwsUnsupportedError,
    );
  });

  test('duplicate physical paths are rejected', () {
    expect(
      () => DownloadArtifactManifest(
        manifestId: 'manifest-1',
        downloadId: 'download-1',
        createdAt: DateTime.utc(2026),
        artifacts: [
          DownloadArtifact(
            artifactId: 'first',
            kind: DownloadArtifactKind.finalVideo,
            fileUri: Uri.file('/downloads/video.mp4'),
          ),
          DownloadArtifact(
            artifactId: 'second',
            kind: DownloadArtifactKind.remuxTemporaryFile,
            fileUri: Uri.file('/downloads/video.mp4'),
          ),
        ],
      ),
      throwsArgumentError,
    );
  });

  test('delete job references the authoritative artifact manifest', () {
    final job = DeleteJob(
      jobId: 'delete-1',
      artifactManifestId: 'manifest-1',
      status: DeleteJobStatus.pending,
    );

    expect(job.artifactManifestId, 'manifest-1');
  });
}
WYNIME_EOF_TEST_DOMAIN_DOWNLOAD_ARTIFACT_MANIFEST_TEST_DART
mkdir -p test/domain
cat > test/domain/playback_session_test.dart <<'WYNIME_EOF_TEST_DOMAIN_PLAYBACK_SESSION_TEST_DART'
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

void main() {
  test('playback session and ad plan must describe the same episode identity', () {
    final episode = SourceEpisodeIdentity(
      sourceId: 'source',
      lineId: 'line',
      subjectId: 'subject',
      episodeId: 'episode',
    );
    final plan = AdRemovalPlan(
      key: AdRemovalPlanKey(
        episode: episode,
        manifestFingerprint: ManifestFingerprint(
          algorithm: 'sha256',
          value: 'fingerprint',
        ),
      ),
      mode: AdRemovalMode.safe,
    );

    final session = PlaybackSession(
      sessionId: 'session',
      episode: episode,
      mediaUri: Uri.parse('https://invalid.example/media.m3u8'),
      pageUri: Uri.parse('https://invalid.example/episode'),
      adRemovalPlan: plan,
      headers: const {'User-Agent': 'redacted-for-test'},
    );

    expect(session.adRemovalPlan, same(plan));
    expect(session.headers['User-Agent'], 'redacted-for-test');
    expect(
      () => session.headers['Authorization'] = 'secret',
      throwsUnsupportedError,
    );
  });
}
WYNIME_EOF_TEST_DOMAIN_PLAYBACK_SESSION_TEST_DART
mkdir -p test/architecture
cat > test/architecture/domain_dependency_test.dart <<'WYNIME_EOF_TEST_ARCHITECTURE_DOMAIN_DEPENDENCY_TEST_DART'
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('domain layer does not import Flutter or outer implementation packages', () {
    final domainFiles = Directory('lib/src/domain')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    const forbiddenImports = [
      "package:flutter/",
      "package:flutter_localizations/",
      "dart:io",
      "dart:ffi",
    ];

    for (final file in domainFiles) {
      final content = file.readAsStringSync();
      for (final forbiddenImport in forbiddenImports) {
        expect(
          content,
          isNot(contains(forbiddenImport)),
          reason: '${file.path} must remain a pure Domain file.',
        );
      }
    }
  });
}
WYNIME_EOF_TEST_ARCHITECTURE_DOMAIN_DEPENDENCY_TEST_DART
mkdir -p test/presentation
cat > test/presentation/app_shell_test.dart <<'WYNIME_EOF_TEST_PRESENTATION_APP_SHELL_TEST_DART'
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  Future<void> pumpAtSize(
    WidgetTester tester,
    Size size,
  ) async {
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

  testWidgets('navigation changes the selected placeholder', (tester) async {
    await pumpAtSize(tester, const Size(360, 800));

    await tester.tap(find.byIcon(Icons.search_outlined));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Search UI is scaffolded only. No website or source is queried in Phase 0.',
      ),
      findsOneWidget,
    );
  });
}
WYNIME_EOF_TEST_PRESENTATION_APP_SHELL_TEST_DART
mkdir -p test/presentation
cat > test/presentation/localization_test.dart <<'WYNIME_EOF_TEST_PRESENTATION_LOCALIZATION_TEST_DART'
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';

void main() {
  testWidgets('supports Traditional Chinese, Simplified Chinese, Japanese, and English',
      (tester) async {
    const expectations = <Locale, String>{
      Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hant'): '收藏庫',
      Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hans'): '收藏库',
      Locale('ja'): 'ライブラリ',
      Locale('en'): 'Library',
    };

    await tester.binding.setSurfaceSize(const Size(1024, 768));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    for (final entry in expectations.entries) {
      await tester.pumpWidget(WynimeApp(locale: entry.key));
      await tester.pumpAndSettle();
      expect(find.text(entry.value), findsOneWidget);
    }
  });
}
WYNIME_EOF_TEST_PRESENTATION_LOCALIZATION_TEST_DART
mkdir -p test/golden
cat > test/golden/app_shell_golden_test.dart <<'WYNIME_EOF_TEST_GOLDEN_APP_SHELL_GOLDEN_TEST_DART'
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
    await tester.pumpWidget(const WynimeApp(locale: Locale('en')));
    await tester.pumpAndSettle();
    await expectLater(
      find.byType(WynimeApp),
      matchesGoldenFile('goldens/$fileName.png'),
    );
  }

  tearDown(() async {
    TestWidgetsFlutterBinding.ensureInitialized().setSurfaceSize(null);
  });

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
WYNIME_EOF_TEST_GOLDEN_APP_SHELL_GOLDEN_TEST_DART
mkdir -p .
cat > dart_test.yaml <<'WYNIME_EOF_DART_TEST_YAML'
tags:
  golden:
    timeout: 2x
WYNIME_EOF_DART_TEST_YAML
mkdir -p docs
cat > docs/PHASE0_STATUS.md <<'WYNIME_EOF_DOCS_PHASE0_STATUS_MD'
# Phase 0 Implementation Status

This file records the implementation baseline for Phase 0. The authoritative
scope remains `AGENTS.md`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md`, and
`docs/DECISIONS.md`.

## Locked toolchain

- Flutter: `3.44.8` stable
- Bundled Dart: `3.12.2`
- Android package / namespace: `io.github.william12233.wynime`
- Java for CI Android builds: Temurin 17
- Target platforms: Android and Windows only

Flutter is pinned to an exact stable hotfix release. The generated Android and
Windows runner projects come from that exact SDK, so Gradle, Android Gradle
Plugin, Kotlin, CMake, and Windows runner templates stay mutually compatible
with Flutter instead of being selected independently.

## Implemented Phase 0 surface

- Android and Windows Flutter runners
- Compact, Medium, and Expanded app shells
- Home, Search, Library, Downloads, Sources, and Settings placeholders
- `gen_l10n` ARB resources for Traditional Chinese, Simplified Chinese,
  Japanese, and English
- Shared design tokens with no bundled font
- Pure Dart Domain contracts, including the authoritative
  `PlaybackSession`, `AdRemovalPlan`, `DownloadArtifactManifest`, and
  `DeleteJob`
- Unit, widget, architecture-boundary, and fixed-size Golden smoke tests
- CI gates for format, analyze, tests, Android build, and Windows build

## Explicitly absent

No live source parsing, WebView interception, media playback, downloads,
FFmpeg, mpv, Bangumi production authentication, or source package execution is
implemented in Phase 0.
WYNIME_EOF_DOCS_PHASE0_STATUS_MD

mkdir -p test/golden/goldens

run_check "Flutter dependency resolution" flutter pub get
run_check "Localization generation" flutter gen-l10n
run_check "Dart formatting" dart format .
run_check "Flutter analyzer" flutter analyze --fatal-infos
run_check "Golden baseline generation" flutter test --update-goldens test/golden/app_shell_golden_test.dart
run_check "Unit, widget, architecture, and golden tests" flutter test
run_check "Android debug build" flutter build apk --debug

python3 - "${OVERALL_STATUS}" "${RESULTS_FILE}" <<'PY'
from pathlib import Path
import sys

overall = sys.argv[1]
results_path = Path(sys.argv[2])
status_path = Path("docs/PHASE0_STATUS.md")
text = status_path.read_text(encoding="utf-8")
rows = []
for raw in results_path.read_text(encoding="utf-8").splitlines():
    if not raw.strip():
        continue
    name, result = raw.split("|", 1)
    rows.append(f"- {name}: **{result}**")

text += (
    "\n## Bootstrap validation\n\n"
    f"Overall Linux bootstrap validation: **{overall}**\n\n"
    + "\n".join(rows)
    + "\n\n"
    "- Windows build: **PENDING** — validated by the Windows GitHub Actions job "
    "after the generated runner is committed.\n"
)
status_path.write_text(text, encoding="utf-8")
PY

record ""
record "Bootstrap overall status: ${OVERALL_STATUS}"
exit 0

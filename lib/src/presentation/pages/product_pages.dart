import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/app/app_destination.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/design_system/tokens/spacing.dart';
import 'package:wynime/src/domain/models/app_settings.dart';

Widget buildWynimePage(
  AppDestination destination,
  AppLocalizations localizations, {
  required AppSettings settings,
  required ValueChanged<AppSettings> onSettingsChanged,
  required ValueChanged<AppDestination> onNavigate,
  required bool showPageHeader,
}) {
  return switch (destination) {
    AppDestination.home => HomePage(
      showPageHeader: showPageHeader,
      onNavigate: onNavigate,
    ),
    AppDestination.search => SearchPage(showPageHeader: showPageHeader),
    AppDestination.library => LibraryPage(showPageHeader: showPageHeader),
    AppDestination.downloads => DownloadsPage(showPageHeader: showPageHeader),
    AppDestination.sources => SourcesPage(showPageHeader: showPageHeader),
    AppDestination.settings => SettingsPage(
      showPageHeader: showPageHeader,
      settings: settings,
      onSettingsChanged: onSettingsChanged,
    ),
  };
}

class WynimePageFrame extends StatelessWidget {
  const WynimePageFrame({
    required this.icon,
    required this.title,
    required this.description,
    required this.showPageHeader,
    required this.children,
    super.key,
  });

  final IconData icon;
  final String title;
  final String description;
  final bool showPageHeader;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(
          WynimeSpacing.lg,
          WynimeSpacing.md,
          WynimeSpacing.lg,
          WynimeSpacing.xxl,
        ),
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: WynimeDimensions.contentMaxWidth,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (showPageHeader) ...[
                  _PageHeader(
                    icon: icon,
                    title: title,
                    description: description,
                  ),
                  const SizedBox(height: WynimeSpacing.lg),
                ],
                ...children,
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({
    required this.showPageHeader,
    required this.onNavigate,
    super.key,
  });

  final bool showPageHeader;
  final ValueChanged<AppDestination> onNavigate;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return WynimePageFrame(
      icon: Icons.home_rounded,
      title: localizations.navigationHome,
      description: localizations.homeTagline,
      showPageHeader: showPageHeader,
      children: [
        _ConnectionCard(
          title: localizations.syncDisconnectedTitle,
          description: localizations.syncDisconnectedDescription,
          label: localizations.statusDisconnectedLabel,
        ),
        const SizedBox(height: WynimeSpacing.xl),
        _SectionTitle(label: localizations.continueWatchingTitle),
        const SizedBox(height: WynimeSpacing.sm),
        _EmptyStateCard(
          icon: Icons.play_circle_outline_rounded,
          title: localizations.emptyContinueWatchingTitle,
          description: localizations.emptyContinueWatchingDescription,
        ),
        const SizedBox(height: WynimeSpacing.xl),
        _SectionTitle(label: localizations.scheduleTitle),
        const SizedBox(height: WynimeSpacing.sm),
        _EmptyStateCard(
          icon: Icons.calendar_month_outlined,
          title: localizations.emptyScheduleTitle,
          description: localizations.emptyScheduleDescription,
        ),
        const SizedBox(height: WynimeSpacing.xl),
        _SectionTitle(label: localizations.quickActionsTitle),
        const SizedBox(height: WynimeSpacing.sm),
        Wrap(
          spacing: WynimeSpacing.sm,
          runSpacing: WynimeSpacing.sm,
          children: [
            OutlinedButton.icon(
              onPressed: () => onNavigate(AppDestination.search),
              icon: const Icon(Icons.search),
              label: Text(localizations.searchAction),
            ),
            OutlinedButton.icon(
              onPressed: () => onNavigate(AppDestination.library),
              icon: const Icon(Icons.video_library_outlined),
              label: Text(localizations.viewLibraryAction),
            ),
            OutlinedButton.icon(
              onPressed: () => onNavigate(AppDestination.downloads),
              icon: const Icon(Icons.download_outlined),
              label: Text(localizations.openDownloadsAction),
            ),
          ],
        ),
      ],
    );
  }
}

class SearchPage extends StatefulWidget {
  const SearchPage({required this.showPageHeader, super.key});

  final bool showPageHeader;

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  late final TextEditingController _controller;
  String _submittedQuery = '';

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit(String value) {
    final query = value.trim();
    setState(() => _submittedQuery = query);
  }

  void _clear() {
    _controller.clear();
    setState(() => _submittedQuery = '');
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final hasText = _controller.text.trim().isNotEmpty;
    final hasSubmittedQuery = _submittedQuery.isNotEmpty;

    return WynimePageFrame(
      icon: Icons.search_rounded,
      title: localizations.navigationSearch,
      description: localizations.searchPageDescription,
      showPageHeader: widget.showPageHeader,
      children: [
        SearchBar(
          controller: _controller,
          hintText: localizations.searchHint,
          leading: const Icon(Icons.search),
          onChanged: (value) {
            if (value.trim() != _submittedQuery) {
              setState(() => _submittedQuery = '');
            }
          },
          onSubmitted: _submit,
          trailing: [
            if (hasText)
              IconButton(
                tooltip: localizations.clearAction,
                onPressed: _clear,
                icon: const Icon(Icons.clear),
              ),
          ],
        ),
        const SizedBox(height: WynimeSpacing.lg),
        if (!hasSubmittedQuery)
          _InfoCard(
            icon: Icons.manage_search_rounded,
            title: localizations.searchReadyTitle,
            description: localizations.searchReadyDescription,
          )
        else
          _InfoCard(
            icon: Icons.hub_outlined,
            title: localizations.searchNoSourcesTitle,
            description: localizations.searchNoSourcesDescription,
          ),
        const SizedBox(height: WynimeSpacing.lg),
        _ConnectionCard(
          title: localizations.noActiveSourcesTitle,
          description: localizations.noActiveSourcesDescription,
          label: localizations.statusUnavailableLabel,
        ),
      ],
    );
  }
}

enum _LibraryFilter { all, watching, completed }

class LibraryPage extends StatefulWidget {
  const LibraryPage({required this.showPageHeader, super.key});

  final bool showPageHeader;

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  _LibraryFilter _filter = _LibraryFilter.all;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return WynimePageFrame(
      icon: Icons.video_library_rounded,
      title: localizations.navigationLibrary,
      description: localizations.libraryPageDescription,
      showPageHeader: widget.showPageHeader,
      children: [
        SegmentedButton<_LibraryFilter>(
          segments: [
            ButtonSegment(
              value: _LibraryFilter.all,
              label: Text(localizations.libraryFilterAll),
              icon: const Icon(Icons.grid_view_rounded),
            ),
            ButtonSegment(
              value: _LibraryFilter.watching,
              label: Text(localizations.libraryFilterWatching),
              icon: const Icon(Icons.play_arrow_rounded),
            ),
            ButtonSegment(
              value: _LibraryFilter.completed,
              label: Text(localizations.libraryFilterCompleted),
              icon: const Icon(Icons.check_circle_outline_rounded),
            ),
          ],
          selected: {_filter},
          onSelectionChanged: (selection) {
            setState(() => _filter = selection.first);
          },
        ),
        const SizedBox(height: WynimeSpacing.lg),
        _EmptyStateCard(
          icon: switch (_filter) {
            _LibraryFilter.all => Icons.video_library_outlined,
            _LibraryFilter.watching => Icons.play_circle_outline_rounded,
            _LibraryFilter.completed => Icons.check_circle_outline_rounded,
          },
          title: localizations.libraryEmptyTitle,
          description: localizations.libraryEmptyDescription,
        ),
        const SizedBox(height: WynimeSpacing.lg),
        _InfoCard(
          icon: Icons.sync_disabled_rounded,
          title: localizations.librarySyncHintTitle,
          description: localizations.librarySyncHintDescription,
        ),
      ],
    );
  }
}

class DownloadsPage extends StatelessWidget {
  const DownloadsPage({required this.showPageHeader, super.key});

  final bool showPageHeader;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return WynimePageFrame(
      icon: Icons.download_rounded,
      title: localizations.navigationDownloads,
      description: localizations.downloadsPageDescription,
      showPageHeader: showPageHeader,
      children: [
        _SectionTitle(label: localizations.downloadsActiveTitle),
        const SizedBox(height: WynimeSpacing.sm),
        _EmptyStateCard(
          icon: Icons.download_done_outlined,
          title: localizations.downloadsEmptyTitle,
          description: localizations.downloadsEmptyDescription,
        ),
        const SizedBox(height: WynimeSpacing.xl),
        _InfoCard(
          icon: Icons.verified_user_outlined,
          title: localizations.artifactSafetyTitle,
          description: localizations.artifactSafetyDescription,
        ),
      ],
    );
  }
}

class SourcesPage extends StatelessWidget {
  const SourcesPage({required this.showPageHeader, super.key});

  final bool showPageHeader;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return WynimePageFrame(
      icon: Icons.hub_rounded,
      title: localizations.navigationSources,
      description: localizations.sourcesPageDescription,
      showPageHeader: showPageHeader,
      children: [
        _SectionTitle(label: localizations.sourcesInstalledTitle),
        const SizedBox(height: WynimeSpacing.sm),
        _EmptyStateCard(
          icon: Icons.extension_off_outlined,
          title: localizations.sourcesEmptyTitle,
          description: localizations.sourcesEmptyDescription,
        ),
        const SizedBox(height: WynimeSpacing.lg),
        _InfoCard(
          icon: Icons.shield_outlined,
          title: localizations.sourcesSecurityTitle,
          description: localizations.sourcesSecurityDescription,
        ),
        const SizedBox(height: WynimeSpacing.lg),
        OutlinedButton.icon(
          onPressed: null,
          icon: const Icon(Icons.rate_review_outlined),
          label: Text(localizations.reviewProposalLabel),
        ),
      ],
    );
  }
}

class SettingsPage extends StatelessWidget {
  const SettingsPage({
    required this.showPageHeader,
    required this.settings,
    required this.onSettingsChanged,
    super.key,
  });

  final bool showPageHeader;
  final AppSettings settings;
  final ValueChanged<AppSettings> onSettingsChanged;

  void _save(AppSettings next) {
    onSettingsChanged(next.copyWith(updatedAt: DateTime.now()));
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return WynimePageFrame(
      icon: Icons.settings_rounded,
      title: localizations.navigationSettings,
      description: localizations.settingsPageDescription,
      showPageHeader: showPageHeader,
      children: [
        _SettingsCard(
          title: localizations.settingsAppearanceTitle,
          icon: Icons.palette_outlined,
          children: [
            DropdownButtonFormField<ThemePreference>(
              key: const ValueKey('theme-preference'),
              initialValue: settings.theme,
              decoration: InputDecoration(
                labelText: localizations.themeLabel,
                border: const OutlineInputBorder(),
              ),
              items: [
                DropdownMenuItem(
                  value: ThemePreference.system,
                  child: Text(localizations.themeSystem),
                ),
                DropdownMenuItem(
                  value: ThemePreference.light,
                  child: Text(localizations.themeLight),
                ),
                DropdownMenuItem(
                  value: ThemePreference.dark,
                  child: Text(localizations.themeDark),
                ),
              ],
              onChanged: (value) {
                if (value != null) {
                  _save(settings.copyWith(theme: value));
                }
              },
            ),
          ],
        ),
        const SizedBox(height: WynimeSpacing.lg),
        _SettingsCard(
          title: localizations.settingsLanguageTitle,
          icon: Icons.translate_rounded,
          children: [
            DropdownButtonFormField<InterfaceLanguagePreference>(
              key: const ValueKey('language-preference'),
              initialValue: settings.interfaceLanguage,
              decoration: InputDecoration(
                labelText: localizations.interfaceLanguageLabel,
                border: const OutlineInputBorder(),
              ),
              items: [
                DropdownMenuItem(
                  value: InterfaceLanguagePreference.system,
                  child: Text(localizations.languageSystem),
                ),
                DropdownMenuItem(
                  value: InterfaceLanguagePreference.zhHant,
                  child: Text(localizations.languageZhHant),
                ),
                DropdownMenuItem(
                  value: InterfaceLanguagePreference.zhHans,
                  child: Text(localizations.languageZhHans),
                ),
                DropdownMenuItem(
                  value: InterfaceLanguagePreference.ja,
                  child: Text(localizations.languageJa),
                ),
                DropdownMenuItem(
                  value: InterfaceLanguagePreference.en,
                  child: Text(localizations.languageEn),
                ),
              ],
              onChanged: (value) {
                if (value != null) {
                  _save(settings.copyWith(interfaceLanguage: value));
                }
              },
            ),
          ],
        ),
        const SizedBox(height: WynimeSpacing.lg),
        _SettingsCard(
          title: localizations.settingsPrivacyTitle,
          icon: Icons.lock_outline_rounded,
          children: [
            SwitchListTile.adaptive(
              contentPadding: EdgeInsets.zero,
              title: Text(localizations.telemetryLabel),
              subtitle: Text(localizations.telemetryDescription),
              value: settings.telemetryEnabled,
              onChanged: (value) {
                _save(settings.copyWith(telemetryEnabled: value));
              },
            ),
          ],
        ),
        const SizedBox(height: WynimeSpacing.lg),
        _SettingsCard(
          title: localizations.settingsPlaybackTitle,
          icon: Icons.play_circle_outline_rounded,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(localizations.androidEngineOrderLabel),
              subtitle: Text(localizations.androidEngineOrder),
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(localizations.windowsEngineOrderLabel),
              subtitle: Text(localizations.windowsEngineOrder),
            ),
          ],
        ),
        const SizedBox(height: WynimeSpacing.lg),
        _SettingsCard(
          title: localizations.settingsStorageTitle,
          icon: Icons.folder_outlined,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(localizations.storageDescription),
              leading: const Icon(Icons.verified_outlined),
            ),
          ],
        ),
      ],
    );
  }
}

class _PageHeader extends StatelessWidget {
  const _PageHeader({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        DecoratedBox(
          decoration: BoxDecoration(
            color: colors.primaryContainer,
            borderRadius: BorderRadius.circular(18),
          ),
          child: Padding(
            padding: const EdgeInsets.all(WynimeSpacing.md),
            child: Icon(icon, color: colors.onPrimaryContainer, size: 32),
          ),
        ),
        const SizedBox(width: WynimeSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: WynimeSpacing.xs),
              Text(
                description,
                style: Theme.of(
                  context,
                ).textTheme.bodyLarge?.copyWith(color: colors.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(label, style: Theme.of(context).textTheme.titleLarge);
  }
}

class _ConnectionCard extends StatelessWidget {
  const _ConnectionCard({
    required this.title,
    required this.description,
    required this.label,
  });

  final String title;
  final String description;
  final String label;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Card(
      color: colors.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.cloud_off_outlined, color: colors.onSecondaryContainer),
            const SizedBox(width: WynimeSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          title,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ),
                      DecoratedBox(
                        decoration: BoxDecoration(
                          color: colors.surface,
                          borderRadius: BorderRadius.circular(999),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: WynimeSpacing.sm,
                            vertical: WynimeSpacing.xxs,
                          ),
                          child: Text(
                            label,
                            style: Theme.of(context).textTheme.labelMedium,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: WynimeSpacing.xs),
                  Text(
                    description,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colors.onSecondaryContainer,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyStateCard extends StatelessWidget {
  const _EmptyStateCard({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.xl),
        child: Column(
          children: [
            Icon(icon, size: 48, color: colors.primary),
            const SizedBox(height: WynimeSpacing.md),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: WynimeSpacing.xs),
            Text(
              description,
              textAlign: TextAlign.center,
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: colors.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: colors.primary),
            const SizedBox(width: WynimeSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: WynimeSpacing.xs),
                  Text(
                    description,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({
    required this.title,
    required this.icon,
    required this.children,
  });

  final String title;
  final IconData icon;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(icon),
                const SizedBox(width: WynimeSpacing.sm),
                Text(title, style: Theme.of(context).textTheme.titleLarge),
              ],
            ),
            const SizedBox(height: WynimeSpacing.lg),
            ...children,
          ],
        ),
      ),
    );
  }
}

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/app/app_destination.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/application/source_installed_live_search_pipeline.dart';
import 'package:wynime/src/application/source_package_startup_controller.dart';
import 'package:wynime/src/application/source_registry_controller.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/design_system/tokens/radii.dart';
import 'package:wynime/src/design_system/tokens/spacing.dart';
import 'package:wynime/src/domain/models/app_settings.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/watch_progress.dart';
import 'package:wynime/src/domain/repositories/watch_history_repository.dart';
import 'package:wynime/src/domain/services/watch_progress_policy.dart';
import 'package:wynime/src/domain/models/software_update_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_artifact_catalog.dart';
import '../source_search_presentation_controller.dart';
import 'subject_detail_page.dart';

Widget buildWynimePage(
  AppDestination destination,
  AppLocalizations localizations, {
  required AppSettings settings,
  required ValueChanged<AppSettings> onSettingsChanged,
  required ValueChanged<AppDestination> onNavigate,
  required bool showPageHeader,
  BangumiSessionController? bangumi,
  SourcePackageStartupController? sourcePackages,
  SourceInstalledLiveSearchPipeline? sourceSearchPipeline,
  SourceRegistryController? sourceRegistry,
  SoftwareUpdateController? softwareUpdates,
  WatchHistoryRepository? watchHistory,
}) {
  return switch (destination) {
    AppDestination.home => HomePage(
      showPageHeader: showPageHeader,
      onNavigate: onNavigate,
      bangumi: bangumi,
      watchHistory: watchHistory,
    ),
    AppDestination.search => SearchPage(
      showPageHeader: showPageHeader,
      sourceSearchPipeline: sourceSearchPipeline,
      installedPackagesProvider: () =>
          sourcePackages?.installedPackages ?? const [],
    ),
    AppDestination.library => LibraryPage(
      showPageHeader: showPageHeader,
      bangumi: bangumi,
      onNavigate: onNavigate,
    ),
    AppDestination.downloads => DownloadsPage(showPageHeader: showPageHeader),
    AppDestination.sources => SourcesPage(
      showPageHeader: showPageHeader,
      sourcePackages: sourcePackages,
      sourceRegistry: sourceRegistry,
    ),
    AppDestination.settings => SettingsPage(
      showPageHeader: showPageHeader,
      settings: settings,
      onSettingsChanged: onSettingsChanged,
      bangumi: bangumi,
      softwareUpdates: softwareUpdates,
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
    this.bangumi,
    this.watchHistory,
    super.key,
  });

  final bool showPageHeader;
  final ValueChanged<AppDestination> onNavigate;
  final BangumiSessionController? bangumi;
  final WatchHistoryRepository? watchHistory;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return WynimePageFrame(
      icon: Icons.home_rounded,
      title: localizations.navigationHome,
      description: localizations.homeTagline,
      showPageHeader: showPageHeader,
      children: [
        if (bangumi == null)
          _ConnectionCard(
            title: localizations.syncDisconnectedTitle,
            description: localizations.syncDisconnectedDescription,
            label: localizations.statusDisconnectedLabel,
          )
        else
          _BangumiConnectionCard(controller: bangumi!),
        const SizedBox(height: WynimeSpacing.xl),
        _SectionTitle(label: localizations.continueWatchingTitle),
        const SizedBox(height: WynimeSpacing.sm),
        _ContinueWatchingSection(repository: watchHistory),
        const SizedBox(height: WynimeSpacing.xl),
        _SectionTitle(label: localizations.scheduleTitle),
        const SizedBox(height: WynimeSpacing.sm),
        if (bangumi != null && bangumi!.schedule.isNotEmpty) ...[
          _ScheduleList(entries: bangumi!.schedule),
          if (bangumi!.scheduleUpdatedAt != null)
            Align(
              alignment: Alignment.centerLeft,
              child: Padding(
                padding: const EdgeInsets.only(top: WynimeSpacing.xs),
                child: Text(
                  bangumi!.scheduleIsFresh
                      ? localizations.bangumiScheduleUpdatedLabel
                      : localizations.bangumiScheduleCachedLabel,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
            ),
        ] else
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
  const SearchPage({
    required this.showPageHeader,
    this.sourceSearchPipeline,
    this.installedPackagesProvider,
    this.searchOperation,
    super.key,
  });

  final bool showPageHeader;
  final SourceInstalledLiveSearchPipeline? sourceSearchPipeline;
  final InstalledSourcePackagesProvider? installedPackagesProvider;
  final SourceInstalledLiveSearchOperation? searchOperation;

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  late final TextEditingController _controller;
  late final SourceSearchPresentationController _search;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    final pipeline = widget.sourceSearchPipeline;
    _search = SourceSearchPresentationController(
      installedPackages: widget.installedPackagesProvider ?? () => const [],
      searchOperation: widget.searchOperation ?? pipeline?.search,
    )..addListener(_onSearchStateChanged);
  }

  @override
  void dispose() {
    _search
      ..removeListener(_onSearchStateChanged)
      ..dispose();
    _controller.dispose();
    super.dispose();
  }

  void _onSearchStateChanged() {
    if (mounted) setState(() {});
  }

  void _submit(String value) {
    unawaited(_search.search(value));
  }

  void _clear() {
    _controller.clear();
    _search.reset();
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final hasText = _controller.text.trim().isNotEmpty;
    final state = _search.state;

    return WynimePageFrame(
      icon: Icons.search_rounded,
      title: localizations.navigationSearch,
      description: localizations.searchPageDescription,
      showPageHeader: widget.showPageHeader,
      children: [
        SearchBar(
          key: const ValueKey('source-search-field'),
          controller: _controller,
          hintText: localizations.searchHint,
          leading: const Icon(Icons.search),
          onChanged: (value) {
            if (state.status != SourceSearchPresentationStatus.idle &&
                value.trim() != state.query) {
              _search.reset();
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
        _SearchStateCard(
          state: state,
          localizations: localizations,
          onRetry:
              state.status == SourceSearchPresentationStatus.idle ||
                  state.status == SourceSearchPresentationStatus.loading ||
                  state.status == SourceSearchPresentationStatus.available ||
                  state.status == SourceSearchPresentationStatus.invalidQuery
              ? null
              : _search.retry,
        ),
        if (state.results.isNotEmpty) ...[
          const SizedBox(height: WynimeSpacing.lg),
          _SearchResultsList(results: state.results),
        ],
      ],
    );
  }
}

final class _SearchStateCard extends StatelessWidget {
  const _SearchStateCard({
    required this.state,
    required this.localizations,
    required this.onRetry,
  });

  final SourceSearchPresentationState state;
  final AppLocalizations localizations;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final copy = _searchStateCopy(localizations, state.status);
    final colors = Theme.of(context).colorScheme;
    return Card(
      key: ValueKey('source-search-state-${state.status.name}'),
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (state.status == SourceSearchPresentationStatus.loading)
              SizedBox(
                key: const ValueKey('source-search-loading-indicator'),
                width: 24,
                height: 24,
                child: CircularProgressIndicator(
                  strokeWidth: 3,
                  color: colors.primary,
                ),
              )
            else
              Icon(copy.icon, color: colors.primary),
            const SizedBox(width: WynimeSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    copy.title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: WynimeSpacing.xs),
                  Text(
                    copy.description,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colors.onSurfaceVariant,
                    ),
                  ),
                  if (onRetry != null) ...[
                    const SizedBox(height: WynimeSpacing.sm),
                    OutlinedButton.icon(
                      key: const ValueKey('source-search-retry'),
                      onPressed: onRetry,
                      icon: const Icon(Icons.refresh_rounded),
                      label: Text(localizations.searchRetryAction),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

final class _SearchResultsList extends StatelessWidget {
  const _SearchResultsList({required this.results});

  final Iterable<SourceSearchPresentationItem> results;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final values = results.toList(growable: false);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SectionTitle(label: localizations.searchResultsTitle),
        const SizedBox(height: WynimeSpacing.sm),
        Card(
          key: const ValueKey('source-search-results'),
          child: Column(
            children: [
              for (var index = 0; index < values.length; index++)
                ListTile(
                  key: ValueKey(
                    'source-search-result-$index-${values[index].result.sourceId}-${values[index].result.subjectId}',
                  ),
                  title: Text(
                    values[index].result.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text(
                    localizations.searchResultSource(
                      values[index].sourceDisplayName,
                      values[index].result.sourceId,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

({IconData icon, String title, String description}) _searchStateCopy(
  AppLocalizations localizations,
  SourceSearchPresentationStatus status,
) {
  return switch (status) {
    SourceSearchPresentationStatus.idle => (
      icon: Icons.manage_search_rounded,
      title: localizations.searchReadyTitle,
      description: localizations.searchReadyDescription,
    ),
    SourceSearchPresentationStatus.loading => (
      icon: Icons.sync_rounded,
      title: localizations.searchLoadingTitle,
      description: localizations.searchLoadingDescription,
    ),
    SourceSearchPresentationStatus.available => (
      icon: Icons.check_circle_outline_rounded,
      title: localizations.searchAvailableTitle,
      description: localizations.searchAvailableDescription,
    ),
    SourceSearchPresentationStatus.partial => (
      icon: Icons.warning_amber_rounded,
      title: localizations.searchPartialTitle,
      description: localizations.searchPartialDescription,
    ),
    SourceSearchPresentationStatus.notFound => (
      icon: Icons.search_off_rounded,
      title: localizations.searchNotFoundTitle,
      description: localizations.searchNotFoundDescription,
    ),
    SourceSearchPresentationStatus.noSources => (
      icon: Icons.hub_outlined,
      title: localizations.searchNoSourcesTitle,
      description: localizations.searchNoSourcesDescription,
    ),
    SourceSearchPresentationStatus.noUsableSources => (
      icon: Icons.extension_off_outlined,
      title: localizations.searchNoUsableSourcesTitle,
      description: localizations.searchNoUsableSourcesDescription,
    ),
    SourceSearchPresentationStatus.invalidQuery => (
      icon: Icons.edit_note_rounded,
      title: localizations.searchInvalidQueryTitle,
      description: localizations.searchInvalidQueryDescription,
    ),
    SourceSearchPresentationStatus.failed => (
      icon: Icons.error_outline_rounded,
      title: localizations.searchFailedTitle,
      description: localizations.searchFailedDescription,
    ),
  };
}

enum _LibraryFilter { wish, watching, onHold, completed, dropped }

const _libraryFilterOrder = <_LibraryFilter>[
  _LibraryFilter.dropped,
  _LibraryFilter.wish,
  _LibraryFilter.watching,
  _LibraryFilter.onHold,
  _LibraryFilter.completed,
];

class LibraryPage extends StatefulWidget {
  const LibraryPage({
    required this.showPageHeader,
    required this.onNavigate,
    this.bangumi,
    super.key,
  });

  final bool showPageHeader;
  final ValueChanged<AppDestination> onNavigate;
  final BangumiSessionController? bangumi;

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  _LibraryFilter _filter = _LibraryFilter.watching;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return WynimePageFrame(
      icon: Icons.video_library_rounded,
      title: localizations.navigationLibrary,
      description: localizations.libraryPageDescription,
      showPageHeader: widget.showPageHeader,
      children: [
        _LibraryStatusTabs(
          selected: _filter,
          countFor: (filter) => _collectionCount(filter, widget.bangumi),
          onSelected: (filter) => setState(() => _filter = filter),
        ),
        const SizedBox(height: WynimeSpacing.lg),
        if (widget.bangumi != null && widget.bangumi!.collections.isNotEmpty)
          _CollectionList(
            controller: widget.bangumi!,
            filter: _filter,
            onOpenSubject: (subjectId) => _openSubject(context, subjectId),
          )
        else
          _EmptyStateCard(
            icon: _libraryFilterIcon(_filter),
            title: localizations.libraryEmptyTitle,
            description: localizations.libraryEmptyDescription,
          ),
        const SizedBox(height: WynimeSpacing.lg),
        _InfoCard(
          icon: widget.bangumi?.isAuthenticated == true
              ? Icons.sync_rounded
              : Icons.sync_disabled_rounded,
          title: widget.bangumi?.isAuthenticated == true
              ? localizations.bangumiPendingLabel(widget.bangumi!.pendingCount)
              : localizations.librarySyncHintTitle,
          description: widget.bangumi?.isAuthenticated == true
              ? localizations.bangumiSyncDescription
              : localizations.librarySyncHintDescription,
        ),
      ],
    );
  }

  int _collectionCount(
    _LibraryFilter filter,
    BangumiSessionController? controller,
  ) {
    if (controller == null) return 0;
    return controller.collections
        .where((entry) => entry.status.name == filter.name)
        .length;
  }

  void _openSubject(BuildContext context, String subjectId) {
    final controller = widget.bangumi;
    if (controller == null) return;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        settings: RouteSettings(name: '/bangumi/subjects/$subjectId'),
        builder: (_) => BangumiSubjectDetailPage(
          controller: controller,
          subjectId: subjectId,
          onHome: () {
            Navigator.of(context).pop();
            widget.onNavigate(AppDestination.home);
          },
        ),
      ),
    );
  }
}

final class _LibraryStatusTabs extends StatelessWidget {
  const _LibraryStatusTabs({
    required this.selected,
    required this.countFor,
    required this.onSelected,
  });

  final _LibraryFilter selected;
  final int Function(_LibraryFilter filter) countFor;
  final ValueChanged<_LibraryFilter> onSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: WynimeSpacing.xs),
      child: Row(
        children: [
          for (final filter in _libraryFilterOrder)
            Padding(
              padding: const EdgeInsets.only(right: WynimeSpacing.lg),
              child: _LibraryStatusTab(
                key: ValueKey('library-status-tab-${filter.name}'),
                label: _libraryFilterLabel(filter, l10n),
                count: countFor(filter),
                selected: selected == filter,
                onTap: () => onSelected(filter),
              ),
            ),
        ],
      ),
    );
  }
}

final class _LibraryStatusTab extends StatelessWidget {
  const _LibraryStatusTab({
    required this.label,
    required this.count,
    required this.selected,
    required this.onTap,
    super.key,
  });

  final String label;
  final int count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = selected
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurfaceVariant;
    return Semantics(
      button: true,
      selected: selected,
      label: '$label $count',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(WynimeRadii.small),
        child: Padding(
          padding: const EdgeInsets.only(
            top: WynimeSpacing.xs,
            left: WynimeSpacing.xs,
            right: WynimeSpacing.xs,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    label,
                    style: theme.textTheme.labelLarge?.copyWith(
                      color: color,
                      fontWeight: selected ? FontWeight.w700 : null,
                    ),
                  ),
                  const SizedBox(width: WynimeSpacing.xxs),
                  Text(
                    '$count',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: selected
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: WynimeSpacing.xs),
              AnimatedContainer(
                duration: const Duration(milliseconds: 160),
                curve: Curves.easeOut,
                height: 3,
                width: selected ? 28 : 0,
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary,
                  borderRadius: BorderRadius.circular(WynimeRadii.small),
                ),
              ),
            ],
          ),
        ),
      ),
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
  const SourcesPage({
    required this.showPageHeader,
    this.sourcePackages,
    this.sourceRegistry,
    super.key,
  });

  final bool showPageHeader;
  final SourcePackageStartupController? sourcePackages;
  final SourceRegistryController? sourceRegistry;

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
        ..._sourcePackageContent(localizations),
        if (sourceRegistry != null) ...[
          const SizedBox(height: WynimeSpacing.lg),
          ..._sourceRegistryContent(localizations),
        ],
        const SizedBox(height: WynimeSpacing.lg),
        _InfoCard(
          icon: Icons.shield_outlined,
          title: localizations.sourcesSecurityTitle,
          description: localizations.sourcesSecurityDescription,
        ),
        if (sourcePackages == null) ...[
          const SizedBox(height: WynimeSpacing.lg),
          OutlinedButton.icon(
            onPressed: null,
            icon: const Icon(Icons.rate_review_outlined),
            label: Text(localizations.reviewProposalLabel),
          ),
        ],
      ],
    );
  }

  List<Widget> _sourcePackageContent(AppLocalizations localizations) {
    final controller = sourcePackages;
    if (controller == null) {
      return [
        _EmptyStateCard(
          icon: Icons.extension_off_outlined,
          title: localizations.sourcesEmptyTitle,
          description: localizations.sourcesEmptyDescription,
        ),
      ];
    }
    if (controller.status == SourcePackageStartupStatus.idle ||
        controller.status == SourcePackageStartupStatus.loading) {
      return [
        _InfoCard(
          icon: Icons.sync_outlined,
          title: localizations.sourcesLoadingTitle,
          description: localizations.sourcesLoadingDescription,
        ),
      ];
    }
    if (controller.status == SourcePackageStartupStatus.failed) {
      return [
        _InfoCard(
          icon: Icons.error_outline_rounded,
          title: localizations.sourcesLoadFailedTitle,
          description: localizations.sourcesLoadFailedDescription,
        ),
      ];
    }
    final installed = controller.installedPackages;
    if (installed.isEmpty) {
      return [
        _EmptyStateCard(
          icon: Icons.extension_off_outlined,
          title: localizations.sourcesEmptyTitle,
          description: localizations.sourcesEmptyDescription,
        ),
      ];
    }
    return [
      for (final installedPackage in installed)
        _InstalledSourcePackageCard(
          package: installedPackage,
          controller: controller,
        ),
    ];
  }

  List<Widget> _sourceRegistryContent(AppLocalizations localizations) {
    final registry = sourceRegistry!;
    final status = registry.status;
    if (status == SourceRegistryStatus.idle ||
        status == SourceRegistryStatus.loading) {
      return [
        _SourceRegistryHeader(
          localizations: localizations,
          loading: true,
          onRefresh: null,
        ),
        _InfoCard(
          icon: Icons.cloud_download_outlined,
          title: localizations.sourcesRegistryLoadingTitle,
          description: localizations.sourcesRegistryLoadingDescription,
        ),
      ];
    }
    if (status == SourceRegistryStatus.failed) {
      return [
        _SourceRegistryHeader(
          localizations: localizations,
          loading: false,
          onRefresh: registry.refresh,
        ),
        _InfoCard(
          icon: Icons.cloud_off_outlined,
          title: localizations.sourcesRegistryFailedTitle,
          description: localizations.sourcesRegistryFailedDescription,
        ),
      ];
    }
    final catalog = registry.catalog;
    if (catalog == null || catalog.packages.isEmpty) {
      return [
        _SourceRegistryHeader(
          localizations: localizations,
          loading: false,
          onRefresh: registry.refresh,
        ),
        _EmptyStateCard(
          icon: Icons.inventory_2_outlined,
          title: localizations.sourcesRegistryEmptyTitle,
          description: localizations.sourcesRegistryEmptyDescription,
        ),
      ];
    }
    return [
      _SourceRegistryHeader(
        localizations: localizations,
        loading: false,
        revision: catalog.index.revision,
        onRefresh: registry.refresh,
      ),
      ..._registryPackageCards(catalog),
    ];
  }

  List<Widget> _registryPackageCards(SourceRegistryCatalog catalog) {
    final localState = sourcePackages;
    final installedPackages =
        localState?.status == SourcePackageStartupStatus.ready
        ? localState!.installedPackages
        : null;
    return [
      for (final item in catalog.packages)
        _RegistrySourcePackageCard(
          item: item,
          sourcePackages: localState,
          installedPackages: installedPackages,
        ),
    ];
  }
}

class _SourceRegistryHeader extends StatelessWidget {
  const _SourceRegistryHeader({
    required this.localizations,
    required this.loading,
    required this.onRefresh,
    this.revision,
  });

  final AppLocalizations localizations;
  final bool loading;
  final String? revision;
  final Future<void> Function()? onRefresh;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              child: _SectionTitle(label: localizations.sourcesRegistryTitle),
            ),
            OutlinedButton.icon(
              onPressed: loading || onRefresh == null
                  ? null
                  : () => unawaited(onRefresh!()),
              icon: loading
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.refresh_rounded),
              label: Text(localizations.sourcesRegistryRefreshAction),
            ),
          ],
        ),
        const SizedBox(height: WynimeSpacing.xs),
        Text(localizations.sourcesRegistryDescription),
        if (revision != null) ...[
          const SizedBox(height: WynimeSpacing.xs),
          Text(
            localizations.sourcesRegistryRevisionLabel(revision!),
            style: Theme.of(context).textTheme.labelMedium,
          ),
        ],
        const SizedBox(height: WynimeSpacing.sm),
      ],
    );
  }
}

class _RegistrySourcePackageCard extends StatelessWidget {
  const _RegistrySourcePackageCard({
    required this.item,
    required this.sourcePackages,
    required this.installedPackages,
  });

  final SourceRegistryCatalogPackage item;
  final SourcePackageStartupController? sourcePackages;
  final List<InstalledSourcePackage>? installedPackages;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final localPackages = installedPackages;
    if (localPackages == null) {
      return _buildCard(
        context,
        localizations,
        localizations.sourcesRegistryInstalledStateUnavailableLabel,
        action: null,
      );
    }
    InstalledSourcePackage? installed;
    for (final candidate in localPackages) {
      if (candidate.package.packageId == item.package.packageId) {
        installed = candidate;
        break;
      }
    }
    final label = installed == null
        ? localizations.sourcesRegistryNotInstalledLabel
        : installed.package.version < item.package.version
        ? localizations.sourcesRegistryUpdateAvailableLabel
        : localizations.sourcesRegistryInstalledLabel;
    final isNewPackage = installed == null;
    final hasUpdate =
        installed != null && installed.package.version < item.package.version;
    final actionLabel = isNewPackage
        ? localizations.sourcesPackageInstallAction
        : hasUpdate
        ? localizations.sourcesPackageUpdateAction
        : null;
    return _buildCard(
      context,
      localizations,
      label,
      action: actionLabel == null
          ? null
          : _SourcePackageAction(
              label: actionLabel,
              icon: isNewPackage
                  ? Icons.download_outlined
                  : Icons.system_update_alt_outlined,
              onPressed: sourcePackages!.isMutating
                  ? null
                  : () => _runSourcePackageOperation(
                      context,
                      localizations,
                      operation: () =>
                          sourcePackages!.installOrUpdate(item.package),
                      successMessage: isNewPackage
                          ? localizations.sourcesPackageInstalledMessage(
                              item.package.displayName,
                            )
                          : localizations.sourcesPackageUpdatedMessage(
                              item.package.displayName,
                            ),
                    ),
            ),
    );
  }

  Widget _buildCard(
    BuildContext context,
    AppLocalizations localizations,
    String label, {
    required _SourcePackageAction? action,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.inventory_2_outlined),
              title: Text(
                item.package.displayName,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                '${item.package.packageId} · v${item.package.version} · ${localizations.sourcesRegistryIntegrityLabel}',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: SizedBox(
                width: 128,
                child: Align(
                  alignment: Alignment.centerRight,
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerRight,
                    child: Chip(label: Text(label, maxLines: 1)),
                  ),
                ),
              ),
            ),
            if (action != null)
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton.icon(
                  onPressed: action.onPressed,
                  icon: Icon(action.icon),
                  label: Text(action.label),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _InstalledSourcePackageCard extends StatelessWidget {
  const _InstalledSourcePackageCard({
    required this.package,
    required this.controller,
  });

  final InstalledSourcePackage package;
  final SourcePackageStartupController controller;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final status = _statusLabel(localizations, package);
    final isEnabled = package.status == SourcePackageStatus.enabled;
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.extension_outlined),
              title: Text(
                package.package.displayName,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                '${package.package.packageId} · v${package.package.version}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: SizedBox(
                width: 112,
                child: Align(
                  alignment: Alignment.centerRight,
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerRight,
                    child: Chip(label: Text(status, maxLines: 1)),
                  ),
                ),
              ),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton.icon(
                onPressed: controller.isMutating
                    ? null
                    : isEnabled
                    ? () => _runSourcePackageOperation(
                        context,
                        localizations,
                        operation: () => controller.disable(
                          packageId: package.package.packageId,
                          version: package.package.version,
                        ),
                        successMessage: localizations
                            .sourcesPackageDisabledMessage(
                              package.package.displayName,
                            ),
                      )
                    : () => _reviewAndEnableSourcePackage(
                        context,
                        localizations,
                        controller,
                        package,
                      ),
                icon: Icon(
                  isEnabled
                      ? Icons.pause_circle_outline
                      : Icons.check_circle_outline,
                ),
                label: Text(
                  isEnabled
                      ? localizations.sourcesPackageDisableAction
                      : localizations.sourcesPackageEnableAction,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

final class _SourcePackageAction {
  const _SourcePackageAction({
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
}

Future<void> _runSourcePackageOperation(
  BuildContext context,
  AppLocalizations localizations, {
  required Future<InstalledSourcePackage> Function() operation,
  required String successMessage,
}) async {
  try {
    await operation();
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(successMessage)));
  } on SourcePackageLifecycleException {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(localizations.sourcesPackageOperationFailed)),
      );
  }
}

Future<void> _reviewAndEnableSourcePackage(
  BuildContext context,
  AppLocalizations localizations,
  SourcePackageStartupController controller,
  InstalledSourcePackage package,
) async {
  final approved = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => _SourcePackageReviewDialog(
      localizations: localizations,
      package: package,
    ),
  );
  if (approved != true || !context.mounted) return;
  await _runSourcePackageOperation(
    context,
    localizations,
    operation: () => controller.enable(
      packageId: package.package.packageId,
      version: package.package.version,
      userApproved: true,
      reconsentGranted: true,
    ),
    successMessage: localizations.sourcesPackageEnabledMessage(
      package.package.displayName,
    ),
  );
}

final class _SourcePackageReviewDialog extends StatelessWidget {
  const _SourcePackageReviewDialog({
    required this.localizations,
    required this.package,
  });

  final AppLocalizations localizations;
  final InstalledSourcePackage package;

  @override
  Widget build(BuildContext context) {
    final manifest = package.package;
    final policy = manifest.securityPolicy;
    final domains = policy.allowedDomains
        .map(
          (rule) =>
              '${rule.host} (${rule.schemes.toList(growable: false).join(', ')})',
        )
        .join('\n');
    final permissions = policy.permissions
        .map((permission) => permission.name)
        .join(', ');
    final budget = policy.budget;
    return AlertDialog(
      title: Text(localizations.sourcesPackageReviewTitle),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              package.requiresReconsent
                  ? localizations.sourcesPackageReconsentDescription(
                      manifest.displayName,
                    )
                  : localizations.sourcesPackageReviewDescription(
                      manifest.displayName,
                    ),
            ),
            const SizedBox(height: 12),
            Text(
              '${manifest.packageId} · v${manifest.version}',
              style: Theme.of(context).textTheme.labelMedium,
            ),
            const SizedBox(height: 12),
            Text(
              localizations.sourcesPackageDomainsLabel,
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Text(domains),
            const SizedBox(height: 8),
            Text(
              localizations.sourcesPackagePermissionsLabel,
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Text(permissions),
            const SizedBox(height: 8),
            Text(
              localizations.sourcesPackageBudgetLabel,
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Text(
              localizations.sourcesPackageBudgetSummary(
                budget.maxDocumentBytes,
                budget.maxRecords,
                budget.maxSelectorMatches,
                budget.maxEvaluationSteps,
                budget.maxRegexPatternChars,
                budget.maxRegexInputChars,
                budget.maxRedirects,
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text(localizations.sourcesPackageCancelAction),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: Text(localizations.sourcesPackageConfirmEnableAction),
        ),
      ],
    );
  }
}

String _statusLabel(
  AppLocalizations localizations,
  InstalledSourcePackage package,
) {
  if (package.status == SourcePackageStatus.enabled) {
    return localizations.sourcesStatusEnabled;
  }
  if (package.requiresReconsent) {
    return localizations.sourcesStatusReconsentRequired;
  }
  if (package.requiresConsent) {
    return localizations.sourcesStatusReviewRequired;
  }
  return localizations.sourcesStatusDisabled;
}

String _libraryFilterLabel(_LibraryFilter filter, AppLocalizations l10n) {
  return switch (filter) {
    _LibraryFilter.wish => l10n.libraryFilterWish,
    _LibraryFilter.watching => l10n.libraryFilterWatching,
    _LibraryFilter.completed => l10n.libraryFilterCompleted,
    _LibraryFilter.onHold => l10n.libraryFilterOnHold,
    _LibraryFilter.dropped => l10n.libraryFilterDropped,
  };
}

IconData _libraryFilterIcon(_LibraryFilter filter) {
  return switch (filter) {
    _LibraryFilter.wish => Icons.bookmark_border_rounded,
    _LibraryFilter.watching => Icons.play_circle_outline_rounded,
    _LibraryFilter.completed => Icons.check_circle_outline_rounded,
    _LibraryFilter.onHold => Icons.pause_circle_outline_rounded,
    _LibraryFilter.dropped => Icons.remove_circle_outline_rounded,
  };
}

String _bangumiSyncErrorText(AppLocalizations l10n, String code) {
  if (code == 'http_415') return l10n.bangumiSyncErrorUnsupportedMedia;
  if (code == 'auth_required' ||
      code == 'reauth_required' ||
      code == 'account_mismatch') {
    return l10n.bangumiSyncErrorReauth;
  }
  if (code == 'remote_revision_conflict') {
    return l10n.bangumiSyncErrorConflict;
  }
  if (code == 'rate_limited' ||
      code == 'network_error' ||
      code == 'network_timeout' ||
      code == 'network_unavailable' ||
      code == 'remote_server_error' ||
      code == 'collection_refresh_failed' ||
      code == 'http_408' ||
      code == 'http_429' ||
      code == 'http_500') {
    return l10n.bangumiSyncErrorRetry;
  }
  return l10n.bangumiSyncErrorGeneric;
}

class _BangumiConnectionCard extends StatelessWidget {
  const _BangumiConnectionCard({required this.controller});

  final BangumiSessionController controller;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final unavailable = !controller.isAvailable;
    final connected = controller.isAuthenticated;
    final reauth = controller.status == BangumiConnectionStatus.reauthRequired;
    return Card(
      color: Theme.of(context).colorScheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              connected ? Icons.cloud_done_outlined : Icons.cloud_off_outlined,
              color: Theme.of(context).colorScheme.onSecondaryContainer,
            ),
            const SizedBox(width: WynimeSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    unavailable
                        ? l10n.bangumiUnavailableTitle
                        : connected
                        ? l10n.bangumiConnectedTitle
                        : reauth
                        ? l10n.bangumiReauthTitle
                        : l10n.syncDisconnectedTitle,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: WynimeSpacing.xs),
                  Text(
                    unavailable
                        ? l10n.bangumiUnavailableDescription
                        : connected
                        ? l10n.bangumiAccountLabel(
                            controller.account?.username ?? '',
                          )
                        : l10n.syncDisconnectedDescription,
                  ),
                  if (!unavailable) ...[
                    const SizedBox(height: WynimeSpacing.sm),
                    Wrap(
                      spacing: WynimeSpacing.sm,
                      runSpacing: WynimeSpacing.sm,
                      children: [
                        if (!connected)
                          FilledButton.icon(
                            key: const ValueKey('bangumi-login'),
                            onPressed:
                                controller.status ==
                                    BangumiConnectionStatus.authorizing
                                ? null
                                : controller.signIn,
                            icon: const Icon(Icons.login_rounded),
                            label: Text(l10n.bangumiLoginAction),
                          )
                        else ...[
                          OutlinedButton.icon(
                            key: const ValueKey('bangumi-sync'),
                            onPressed: controller.isSyncing
                                ? null
                                : controller.syncNow,
                            icon: const Icon(Icons.sync_rounded),
                            label: Text(l10n.bangumiSyncAction),
                          ),
                          OutlinedButton.icon(
                            key: const ValueKey('bangumi-sign-out'),
                            onPressed: controller.signOut,
                            icon: const Icon(Icons.logout_rounded),
                            label: Text(l10n.bangumiSignOutAction),
                          ),
                        ],
                      ],
                    ),
                  ],
                  if (!unavailable && controller.errorCode != null) ...[
                    const SizedBox(height: WynimeSpacing.xs),
                    Text(
                      _bangumiSyncErrorText(l10n, controller.errorCode!),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: WynimeSpacing.sm),
            Text(
              unavailable
                  ? l10n.statusUnavailableLabel
                  : connected
                  ? l10n.statusConnectedLabel
                  : reauth
                  ? l10n.bangumiReauthLabel
                  : l10n.statusDisconnectedLabel,
            ),
          ],
        ),
      ),
    );
  }
}

class _ScheduleList extends StatelessWidget {
  const _ScheduleList({required this.entries});

  final List<BangumiScheduleEntry> entries;

  @override
  Widget build(BuildContext context) {
    final visible = entries.take(12).toList(growable: false);
    return Card(
      child: Column(
        children: [
          for (var index = 0; index < visible.length; index++)
            ListTile(
              leading: _BangumiArtwork(
                key: ValueKey('bangumi-schedule-artwork-${visible[index].id}'),
                imageUrl: visible[index].imageUrl,
                semanticLabel: visible[index].subjectName,
                width: 42,
                height: 60,
              ),
              title: Text(visible[index].subjectName),
              subtitle: Text(
                visible[index].episodeNumber == null
                    ? 'Bangumi #${visible[index].subjectId}'
                    : 'EP ${visible[index].episodeNumber}',
              ),
              dense: true,
            ),
        ],
      ),
    );
  }
}

class _CollectionList extends StatelessWidget {
  const _CollectionList({
    required this.controller,
    required this.filter,
    required this.onOpenSubject,
  });

  final BangumiSessionController controller;
  final _LibraryFilter filter;
  final ValueChanged<String> onOpenSubject;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final entries = controller.collections
        .where((entry) => entry.status.name == filter.name)
        .toList(growable: false);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (entries.isEmpty)
          _EmptyStateCard(
            icon: _libraryFilterIcon(filter),
            title: l10n.libraryEmptyTitle,
            description: l10n.libraryEmptyDescription,
          )
        else
          LayoutBuilder(
            builder: (context, constraints) {
              final columnCount = constraints.maxWidth >= 1024
                  ? 3
                  : constraints.maxWidth >= 600
                  ? 2
                  : 1;
              const gap = WynimeSpacing.sm;
              final cardWidth =
                  (constraints.maxWidth - gap * (columnCount - 1)) /
                  columnCount;
              return Wrap(
                spacing: gap,
                runSpacing: WynimeSpacing.md,
                children: [
                  for (final entry in entries)
                    _CollectionCard(
                      key: ValueKey('bangumi-collection-${entry.subjectId}'),
                      entry: entry,
                      width: cardWidth,
                      onOpenSubject: onOpenSubject,
                    ),
                ],
              );
            },
          ),
      ],
    );
  }
}

class _CollectionCard extends StatelessWidget {
  const _CollectionCard({
    required this.entry,
    required this.width,
    required this.onOpenSubject,
    super.key,
  });

  final BangumiCollectionEntry entry;
  final double width;
  final ValueChanged<String> onOpenSubject;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final title = entry.nameCn?.isNotEmpty == true
        ? entry.nameCn!
        : entry.name?.isNotEmpty == true
        ? entry.name!
        : entry.subjectId;
    final posterWidth = width < 600
        ? 92.0
        : (width * 0.24).clamp(104.0, 132.0).toDouble();
    final posterHeight = posterWidth * 1.5;
    return SizedBox(
      width: width,
      child: Card(
        color: Theme.of(context).colorScheme.surfaceContainerLow,
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: () => onOpenSubject(entry.subjectId),
          child: Padding(
            padding: const EdgeInsets.all(WynimeSpacing.sm),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _BangumiArtwork(
                  key: ValueKey(
                    'bangumi-collection-artwork-${entry.subjectId}',
                  ),
                  imageUrl: entry.imageUrl,
                  semanticLabel: title,
                  width: posterWidth,
                  height: posterHeight,
                ),
                const SizedBox(width: WynimeSpacing.sm),
                Expanded(
                  child: _CollectionCardInfo(
                    title: title,
                    entry: entry,
                    onOpenSubject: () => onOpenSubject(entry.subjectId),
                    moreTooltip: l10n.libraryCardMoreAction,
                    openLabel: l10n.libraryOpenSubjectAction,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CollectionCardInfo extends StatelessWidget {
  const _CollectionCardInfo({
    required this.title,
    required this.entry,
    required this.onOpenSubject,
    required this.moreTooltip,
    required this.openLabel,
  });

  final String title;
  final BangumiCollectionEntry entry;
  final VoidCallback onOpenSubject;
  final String moreTooltip;
  final String openLabel;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final total = entry.totalEpisodes;
    final watched = entry.epStatus;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ),
            PopupMenuButton<String>(
              key: ValueKey('bangumi-collection-menu-${entry.subjectId}'),
              tooltip: moreTooltip,
              padding: EdgeInsets.zero,
              iconSize: 20,
              onSelected: (_) => onOpenSubject(),
              itemBuilder: (context) => [
                PopupMenuItem<String>(value: 'open', child: Text(openLabel)),
              ],
            ),
          ],
        ),
        const SizedBox(height: WynimeSpacing.xs),
        _CollectionProgressText(watched: watched, total: total),
        const SizedBox(height: WynimeSpacing.sm),
        Row(
          children: [
            Icon(
              Icons.list_alt_outlined,
              size: 17,
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
            const SizedBox(width: WynimeSpacing.xxs),
            Expanded(
              child: Text(
                l10n.libraryEpisodesAction,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            if (watched != null && total != null && total > 0)
              SizedBox(
                width: 64,
                child: LinearProgressIndicator(
                  value: (watched / total).clamp(0, 1),
                  minHeight: 3,
                ),
              ),
          ],
        ),
      ],
    );
  }
}

final class _CollectionProgressText extends StatelessWidget {
  const _CollectionProgressText({required this.watched, required this.total});

  final int? watched;
  final int? total;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final watchedValue = watched;
    final totalValue = total;
    final text = totalValue != null && totalValue > 0
        ? watchedValue == null
              ? l10n.libraryProgressTotal(totalValue)
              : l10n.libraryProgressKnown(watchedValue, totalValue)
        : watchedValue == null
        ? l10n.libraryProgressUnknown
        : l10n.libraryProgressWatchedOnly(watchedValue);
    return Text(
      text,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
        color: Theme.of(context).colorScheme.onSurface,
        fontWeight: FontWeight.w600,
      ),
    );
  }
}

class _BangumiArtwork extends StatelessWidget {
  const _BangumiArtwork({
    required this.imageUrl,
    required this.semanticLabel,
    required this.width,
    required this.height,
    super.key,
  });

  final Uri? imageUrl;
  final String semanticLabel;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    final placeholder = DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
      ),
      child: Center(
        child: Icon(
          Icons.movie_creation_outlined,
          color: Theme.of(context).colorScheme.onSurfaceVariant,
          size: 28,
        ),
      ),
    );
    final image = imageUrl == null
        ? placeholder
        : Image.network(
            imageUrl.toString(),
            fit: BoxFit.cover,
            semanticLabel: semanticLabel,
            errorBuilder: (context, error, stackTrace) => placeholder,
            loadingBuilder: (context, child, progress) =>
                progress == null ? child : placeholder,
          );
    return ClipRRect(
      borderRadius: BorderRadius.circular(WynimeRadii.medium),
      child: SizedBox(width: width, height: height, child: image),
    );
  }
}

class _BangumiSettingsContent extends StatelessWidget {
  const _BangumiSettingsContent({required this.controller});

  final BangumiSessionController controller;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final unavailable = !controller.isAvailable;
    final account = controller.account;
    return Column(
      children: [
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.account_circle_outlined),
          title: Text(
            unavailable
                ? l10n.bangumiUnavailableTitle
                : account == null
                ? l10n.bangumiNotSignedInLabel
                : l10n.bangumiAccountLabel(account.username),
          ),
          subtitle: Text(
            unavailable
                ? l10n.bangumiUnavailableDescription
                : account == null
                ? l10n.bangumiReauthDescription
                : l10n.bangumiCacheDescription,
          ),
        ),
        if (!unavailable && !controller.isAuthenticated)
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.icon(
              key: const ValueKey('bangumi-settings-login'),
              onPressed: controller.signIn,
              icon: const Icon(Icons.login_rounded),
              label: Text(l10n.bangumiLoginAction),
            ),
          )
        else if (!unavailable)
          Wrap(
            spacing: WynimeSpacing.sm,
            runSpacing: WynimeSpacing.sm,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              OutlinedButton.icon(
                onPressed: controller.isSyncing ? null : controller.syncNow,
                icon: const Icon(Icons.sync_rounded),
                label: Text(l10n.bangumiSyncAction),
              ),
              Text(
                l10n.bangumiQueueSummary(
                  controller.pendingCount,
                  controller.conflictCount,
                ),
              ),
              if (controller.blockedCount > 0)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.bangumiBlockedQueueSummary(controller.blockedCount),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                    Text(l10n.bangumiBlockedQueueDescription),
                  ],
                ),
              OutlinedButton.icon(
                key: const ValueKey('bangumi-settings-logout'),
                onPressed: controller.signOut,
                icon: const Icon(Icons.logout_rounded),
                label: Text(l10n.bangumiSignOutAction),
              ),
            ],
          ),
        if (controller.conflicts.isNotEmpty) ...[
          const SizedBox(height: WynimeSpacing.md),
          Align(
            alignment: Alignment.centerLeft,
            child: Text(
              l10n.bangumiConflictTitle,
              style: Theme.of(context).textTheme.titleSmall,
            ),
          ),
          const SizedBox(height: WynimeSpacing.xs),
          Align(
            alignment: Alignment.centerLeft,
            child: Text(l10n.bangumiConflictDescription),
          ),
          const SizedBox(height: WynimeSpacing.sm),
          for (final conflict in controller.conflicts)
            Card(
              margin: const EdgeInsets.only(bottom: WynimeSpacing.sm),
              child: Padding(
                padding: const EdgeInsets.all(WynimeSpacing.sm),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      l10n.bangumiConflictSubjectLabel(
                        conflict.operation.subjectId,
                      ),
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: WynimeSpacing.xs),
                    Text(
                      l10n.bangumiConflictRevisionLabel(
                        conflict.remoteState.remoteRevision,
                      ),
                    ),
                    const SizedBox(height: WynimeSpacing.sm),
                    Wrap(
                      spacing: WynimeSpacing.sm,
                      runSpacing: WynimeSpacing.sm,
                      children: [
                        OutlinedButton(
                          key: ValueKey(
                            'bangumi-conflict-adopt-${conflict.operation.operationId}',
                          ),
                          onPressed: () => controller.adoptConflict(conflict),
                          child: Text(l10n.bangumiAdoptRemoteAction),
                        ),
                        FilledButton(
                          key: ValueKey(
                            'bangumi-conflict-requeue-${conflict.operation.operationId}',
                          ),
                          onPressed: () => controller.requeueConflict(conflict),
                          child: Text(l10n.bangumiRequeueLocalAction),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
        ],
        if (controller.errorCode != null)
          Align(
            alignment: Alignment.centerLeft,
            child: Padding(
              padding: const EdgeInsets.only(top: WynimeSpacing.xs),
              child: Text(
                _bangumiSyncErrorText(l10n, controller.errorCode!),
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          ),
      ],
    );
  }
}

class _SoftwareUpdateSettingsContent extends StatelessWidget {
  const _SoftwareUpdateSettingsContent({required this.controller});

  final SoftwareUpdateController controller;

  Future<void> _installWithProgressDialog(BuildContext context) async {
    if (controller.isBusy ||
        controller.status != UpdateStatus.updateAvailable) {
      return;
    }
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _SoftwareUpdateProgressDialog(controller: controller),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final current = controller.currentVersion;
    final latest = controller.latestRelease;
    final status = switch (controller.status) {
      UpdateStatus.checking => l10n.softwareUpdateChecking,
      UpdateStatus.downloading => l10n.softwareUpdateDownloading,
      UpdateStatus.verifying => l10n.softwareUpdateVerifying,
      UpdateStatus.handingOff => l10n.softwareUpdatePreparingInstall,
      UpdateStatus.updateAvailable => l10n.softwareUpdateAvailable,
      UpdateStatus.upToDate => l10n.softwareUpdateUpToDate,
      UpdateStatus.manualUpdateRequired => l10n.softwareUpdateManualRequired,
      UpdateStatus.failed => l10n.softwareUpdateFailed,
      _ => l10n.softwareUpdateIdle,
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.softwareUpdateCurrentVersion),
          subtitle: Text(current?.displayVersion ?? l10n.softwareUpdateUnknown),
        ),
        if (latest != null)
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.softwareUpdateLatestVersion),
            subtitle: Text(latest.version.toString()),
          ),
        Text(status),
        const SizedBox(height: WynimeSpacing.sm),
        Wrap(
          spacing: WynimeSpacing.sm,
          runSpacing: WynimeSpacing.sm,
          children: [
            OutlinedButton.icon(
              key: const ValueKey('software-update-check'),
              onPressed: controller.isBusy ? null : controller.check,
              icon: const Icon(Icons.refresh_rounded),
              label: Text(l10n.softwareUpdateCheckAction),
            ),
            if (controller.status == UpdateStatus.updateAvailable)
              FilledButton.icon(
                key: const ValueKey('software-update-now'),
                onPressed: controller.isBusy
                    ? null
                    : () => _installWithProgressDialog(context),
                icon: const Icon(Icons.download_rounded),
                label: Text(l10n.softwareUpdateInstallAction),
              ),
          ],
        ),
        if (controller.error != null) ...[
          const SizedBox(height: WynimeSpacing.xs),
          Text(
            l10n.softwareUpdateError(controller.error!.code),
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
      ],
    );
  }
}

final class _SoftwareUpdateProgressDialog extends StatefulWidget {
  const _SoftwareUpdateProgressDialog({required this.controller});

  final SoftwareUpdateController controller;

  @override
  State<_SoftwareUpdateProgressDialog> createState() =>
      _SoftwareUpdateProgressDialogState();
}

final class _SoftwareUpdateProgressDialogState
    extends State<_SoftwareUpdateProgressDialog> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_install());
    });
  }

  Future<void> _install() async {
    await widget.controller.install();
    if (!mounted) return;
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return PopScope(
      canPop: false,
      child: ListenableBuilder(
        listenable: widget.controller,
        builder: (context, child) {
          final progress = widget.controller.status == UpdateStatus.downloading
              ? widget.controller.downloadProgress
              : null;
          final normalizedProgress = progress?.clamp(0.0, 1.0).toDouble();
          final message = switch (widget.controller.status) {
            UpdateStatus.verifying => l10n.softwareUpdateVerifying,
            UpdateStatus.handingOff => l10n.softwareUpdatePreparingInstall,
            _ => l10n.softwareUpdateDownloading,
          };
          return AlertDialog(
            key: const ValueKey('software-update-progress-dialog'),
            title: Text(l10n.softwareUpdateTitle),
            content: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(message),
                  const SizedBox(height: WynimeSpacing.md),
                  LinearProgressIndicator(
                    key: const ValueKey('software-update-progress'),
                    value: normalizedProgress,
                  ),
                  if (normalizedProgress != null) ...[
                    const SizedBox(height: WynimeSpacing.xs),
                    Text(
                      l10n.softwareUpdateDownloadProgress(
                        (normalizedProgress * 100).round(),
                      ),
                      textAlign: TextAlign.end,
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class SettingsPage extends StatelessWidget {
  const SettingsPage({
    required this.showPageHeader,
    required this.settings,
    required this.onSettingsChanged,
    this.bangumi,
    this.softwareUpdates,
    super.key,
  });

  final bool showPageHeader;
  final AppSettings settings;
  final ValueChanged<AppSettings> onSettingsChanged;
  final BangumiSessionController? bangumi;
  final SoftwareUpdateController? softwareUpdates;

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
        if (bangumi != null)
          _SettingsCard(
            title: localizations.bangumiSettingsTitle,
            icon: Icons.bookmark_outline_rounded,
            children: [_BangumiSettingsContent(controller: bangumi!)],
          ),
        if (bangumi != null) const SizedBox(height: WynimeSpacing.lg),
        if (softwareUpdates != null)
          _SettingsCard(
            title: localizations.softwareUpdateTitle,
            icon: Icons.system_update_alt_rounded,
            children: [
              _SoftwareUpdateSettingsContent(controller: softwareUpdates!),
            ],
          ),
        if (softwareUpdates != null) const SizedBox(height: WynimeSpacing.lg),
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

class _ContinueWatchingSection extends StatelessWidget {
  const _ContinueWatchingSection({required this.repository});

  final WatchHistoryRepository? repository;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final source = repository;
    if (source == null) {
      return _EmptyStateCard(
        icon: Icons.play_circle_outline_rounded,
        title: localizations.emptyContinueWatchingTitle,
        description: localizations.emptyContinueWatchingDescription,
      );
    }
    return StreamBuilder<List<WatchProgress>>(
      stream: source.watchRecent(),
      builder: (context, snapshot) {
        const policy = WatchProgressPolicy();
        final items = policy.selectContinueWatching(
          snapshot.data ?? const <WatchProgress>[],
        );
        if (items.isEmpty) {
          return _EmptyStateCard(
            icon: Icons.play_circle_outline_rounded,
            title: localizations.emptyContinueWatchingTitle,
            description: localizations.emptyContinueWatchingDescription,
          );
        }
        return Column(
          children: [
            for (final item in items)
              Padding(
                padding: const EdgeInsets.only(bottom: WynimeSpacing.sm),
                child: _ContinueWatchingCard(progress: item),
              ),
          ],
        );
      },
    );
  }
}

class _ContinueWatchingCard extends StatelessWidget {
  const _ContinueWatchingCard({required this.progress});

  final WatchProgress progress;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    const policy = WatchProgressPolicy();
    final percent = policy.progressPercent(progress);
    final position = _formatProgressDuration(progress.position);
    final progressLabel = percent == null
        ? localizations.continueWatchingProgressUnknownDuration(position)
        : localizations.continueWatchingProgress(percent, position);
    return Card(
      key: ValueKey('continue-watching-${progress.progressId}'),
      child: ListTile(
        leading: SizedBox(
          width: 44,
          height: 44,
          child: percent == null
              ? const Icon(Icons.play_circle_outline_rounded)
              : CircularProgressIndicator(value: percent / 100, strokeWidth: 4),
        ),
        title: Text(
          localizations.subjectDetailEpisodeNumber(progress.episodeId),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Text(progressLabel),
      ),
    );
  }
}

String _formatProgressDuration(Duration value) {
  final seconds = value.inSeconds.clamp(0, 1 << 31);
  final minutes = seconds ~/ 60;
  final remainder = seconds % 60;
  return '$minutes:${remainder.toString().padLeft(2, '0')}';
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

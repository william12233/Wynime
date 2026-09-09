import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/app/app_destination.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/design_system/tokens/spacing.dart';
import 'package:wynime/src/domain/models/app_settings.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/software_update_models.dart';

Widget buildWynimePage(
  AppDestination destination,
  AppLocalizations localizations, {
  required AppSettings settings,
  required ValueChanged<AppSettings> onSettingsChanged,
  required ValueChanged<AppDestination> onNavigate,
  required bool showPageHeader,
  BangumiSessionController? bangumi,
  SoftwareUpdateController? softwareUpdates,
}) {
  return switch (destination) {
    AppDestination.home => HomePage(
      showPageHeader: showPageHeader,
      onNavigate: onNavigate,
      bangumi: bangumi,
    ),
    AppDestination.search => SearchPage(showPageHeader: showPageHeader),
    AppDestination.library => LibraryPage(
      showPageHeader: showPageHeader,
      bangumi: bangumi,
    ),
    AppDestination.downloads => DownloadsPage(showPageHeader: showPageHeader),
    AppDestination.sources => SourcesPage(showPageHeader: showPageHeader),
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
    super.key,
  });

  final bool showPageHeader;
  final ValueChanged<AppDestination> onNavigate;
  final BangumiSessionController? bangumi;

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
        _EmptyStateCard(
          icon: Icons.play_circle_outline_rounded,
          title: localizations.emptyContinueWatchingTitle,
          description: localizations.emptyContinueWatchingDescription,
        ),
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

enum _LibraryFilter { all, wish, watching, completed, onHold, dropped }

class LibraryPage extends StatefulWidget {
  const LibraryPage({required this.showPageHeader, this.bangumi, super.key});

  final bool showPageHeader;
  final BangumiSessionController? bangumi;

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
            for (final filter
                in widget.bangumi == null
                    ? const <_LibraryFilter>[
                        _LibraryFilter.all,
                        _LibraryFilter.watching,
                        _LibraryFilter.completed,
                      ]
                    : _LibraryFilter.values)
              ButtonSegment(
                value: filter,
                label: Text(_libraryFilterLabel(filter, localizations)),
                icon: Icon(_libraryFilterIcon(filter)),
              ),
          ],
          selected: {_filter},
          onSelectionChanged: (selection) {
            setState(() => _filter = selection.first);
          },
        ),
        const SizedBox(height: WynimeSpacing.lg),
        if (widget.bangumi != null && widget.bangumi!.collections.isNotEmpty)
          _CollectionList(controller: widget.bangumi!, filter: _filter)
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

String _libraryFilterLabel(_LibraryFilter filter, AppLocalizations l10n) {
  return switch (filter) {
    _LibraryFilter.all => l10n.libraryFilterAll,
    _LibraryFilter.wish => l10n.libraryFilterWish,
    _LibraryFilter.watching => l10n.libraryFilterWatching,
    _LibraryFilter.completed => l10n.libraryFilterCompleted,
    _LibraryFilter.onHold => l10n.libraryFilterOnHold,
    _LibraryFilter.dropped => l10n.libraryFilterDropped,
  };
}

IconData _libraryFilterIcon(_LibraryFilter filter) {
  return switch (filter) {
    _LibraryFilter.all => Icons.video_library_outlined,
    _LibraryFilter.wish => Icons.bookmark_border_rounded,
    _LibraryFilter.watching => Icons.play_circle_outline_rounded,
    _LibraryFilter.completed => Icons.check_circle_outline_rounded,
    _LibraryFilter.onHold => Icons.pause_circle_outline_rounded,
    _LibraryFilter.dropped => Icons.remove_circle_outline_rounded,
  };
}

String _collectionStatusLabel(
  BangumiCollectionStatus? status,
  AppLocalizations l10n,
) {
  return switch (status) {
    BangumiCollectionStatus.wish => l10n.libraryFilterWish,
    BangumiCollectionStatus.watching => l10n.libraryFilterWatching,
    BangumiCollectionStatus.completed => l10n.libraryFilterCompleted,
    BangumiCollectionStatus.onHold => l10n.libraryFilterOnHold,
    BangumiCollectionStatus.dropped => l10n.libraryFilterDropped,
    null => l10n.bangumiNotCollectedLabel,
  };
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
                            onPressed: controller.syncNow,
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
                      l10n.bangumiErrorLabel(controller.errorCode!),
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
              leading: const Icon(Icons.live_tv_outlined),
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
  const _CollectionList({required this.controller, required this.filter});

  final BangumiSessionController controller;
  final _LibraryFilter filter;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final entries = controller.collections
        .where(
          (entry) =>
              filter == _LibraryFilter.all || entry.status.name == filter.name,
        )
        .toList(growable: false);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Column(
            children: [
              for (final entry in entries)
                ListTile(
                  leading: const Icon(Icons.bookmark_outline_rounded),
                  title: Text(entry.nameCn ?? entry.name ?? entry.subjectId),
                  subtitle: Text(_collectionStatusLabel(entry.status, l10n)),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => controller.openSubject(entry.subjectId),
                ),
            ],
          ),
        ),
        if (controller.selectedSubject != null) ...[
          const SizedBox(height: WynimeSpacing.lg),
          BangumiSubjectDetailPage(controller: controller),
        ],
      ],
    );
  }
}

class BangumiSubjectDetailPage extends StatelessWidget {
  const BangumiSubjectDetailPage({required this.controller, super.key});

  final BangumiSessionController controller;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final subject = controller.selectedSubject;
    final episodes =
        controller.selectedEpisodes?.episodes ?? const <BangumiEpisode>[];
    final remote = controller.selectedRemoteState;
    if (subject == null) return const SizedBox.shrink();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              subject.nameCn.isEmpty ? subject.name : subject.nameCn,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            if (subject.summary.isNotEmpty) ...[
              const SizedBox(height: WynimeSpacing.xs),
              Text(subject.summary),
            ],
            const SizedBox(height: WynimeSpacing.md),
            DropdownButtonFormField<BangumiCollectionStatus>(
              key: const ValueKey('bangumi-collection-status'),
              initialValue: remote?.status,
              decoration: InputDecoration(
                labelText: l10n.subjectCollectionLabel,
                border: const OutlineInputBorder(),
              ),
              items: [
                for (final status in BangumiCollectionStatus.values)
                  DropdownMenuItem(
                    value: status,
                    child: Text(_collectionStatusLabel(status, l10n)),
                  ),
              ],
              onChanged: (value) {
                if (value != null) {
                  controller.setCollectionStatus(subject.id, value);
                }
              },
            ),
            const SizedBox(height: WynimeSpacing.md),
            Text(
              l10n.subjectEpisodesLabel,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            for (final episode in episodes)
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: remote?.watchedEpisodeIds.contains(episode.id) ?? false,
                title: Text(
                  episode.nameCn.isEmpty ? episode.name : episode.nameCn,
                ),
                subtitle: Text('EP ${episode.sort}'),
                onChanged: (value) => controller.setEpisodeWatched(
                  subject.id,
                  episode.id,
                  value ?? false,
                ),
              ),
            if (controller.pendingCount > 0 || controller.conflictCount > 0)
              Text(
                l10n.bangumiQueueSummary(
                  controller.pendingCount,
                  controller.conflictCount,
                ),
                style: TextStyle(color: Theme.of(context).colorScheme.primary),
              ),
          ],
        ),
      ),
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
                onPressed: controller.syncNow,
                icon: const Icon(Icons.sync_rounded),
                label: Text(l10n.bangumiSyncAction),
              ),
              Text(
                l10n.bangumiQueueSummary(
                  controller.pendingCount,
                  controller.conflictCount,
                ),
              ),
              if (controller.failedCount > 0)
                Text(
                  l10n.bangumiFailedQueueSummary(controller.failedCount),
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
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
                l10n.bangumiErrorLabel(controller.errorCode!),
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

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final current = controller.currentVersion;
    final latest = controller.latestRelease;
    final status = switch (controller.status) {
      UpdateStatus.checking => l10n.softwareUpdateChecking,
      UpdateStatus.downloading => l10n.softwareUpdateDownloading,
      UpdateStatus.verifying => l10n.softwareUpdateVerifying,
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
        if (controller.downloadProgress != null) ...[
          const SizedBox(height: WynimeSpacing.sm),
          LinearProgressIndicator(value: controller.downloadProgress),
        ],
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
                onPressed: controller.isBusy ? null : controller.install,
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

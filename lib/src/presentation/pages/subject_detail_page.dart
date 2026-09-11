import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/design_system/tokens/dimensions.dart';
import 'package:wynime/src/design_system/tokens/radii.dart';
import 'package:wynime/src/design_system/tokens/spacing.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';

final class BangumiSubjectDetailPage extends StatefulWidget {
  const BangumiSubjectDetailPage({
    required this.controller,
    required this.subjectId,
    this.onHome,
    super.key,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final VoidCallback? onHome;

  @override
  State<BangumiSubjectDetailPage> createState() =>
      _BangumiSubjectDetailPageState();
}

final class _BangumiSubjectDetailPageState
    extends State<BangumiSubjectDetailPage> {
  String? _selectedEpisodeId;

  @override
  void initState() {
    super.initState();
    unawaited(widget.controller.loadSubjectDetail(widget.subjectId));
  }

  @override
  void didUpdateWidget(covariant BangumiSubjectDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.subjectId != widget.subjectId) {
      _selectedEpisodeId = null;
      unawaited(widget.controller.loadSubjectDetail(widget.subjectId));
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final l10n = AppLocalizations.of(context);
        final state = widget.controller.subjectDetailState(widget.subjectId);
        final snapshot = state?.snapshot;
        final title = snapshot == null
            ? l10n.subjectDetailLoading
            : _subjectTitle(snapshot.subject);
        return Scaffold(
          appBar: AppBar(
            title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
            actions: [
              if (widget.onHome != null)
                IconButton(
                  tooltip: l10n.subjectDetailHomeAction,
                  onPressed: widget.onHome,
                  icon: const Icon(Icons.home_outlined),
                ),
            ],
          ),
          body: SafeArea(
            child: _DetailBody(
              controller: widget.controller,
              subjectId: widget.subjectId,
              state: state,
              selectedEpisodeId: _selectedEpisodeId,
              onEpisodeSelected: (episodeId) {
                setState(() => _selectedEpisodeId = episodeId);
              },
            ),
          ),
        );
      },
    );
  }
}

final class _DetailBody extends StatelessWidget {
  const _DetailBody({
    required this.controller,
    required this.subjectId,
    required this.state,
    required this.selectedEpisodeId,
    required this.onEpisodeSelected,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final BangumiSubjectDetailState? state;
  final String? selectedEpisodeId;
  final ValueChanged<String> onEpisodeSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final snapshot = state?.snapshot;
    if (snapshot == null) {
      if (state?.phase == BangumiDetailPhase.fatal) {
        return _FatalDetail(
          description: l10n.subjectDetailFatalDescription,
          onRetry: () => controller.retryDetail(subjectId),
        );
      }
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: WynimeSpacing.md),
            Text(l10n.subjectDetailLoading),
          ],
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(
        WynimeSpacing.md,
        WynimeSpacing.md,
        WynimeSpacing.md,
        WynimeSpacing.xxl,
      ),
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: WynimeDimensions.contentMaxWidth,
          ),
          child: _SubjectDetailContent(
            controller: controller,
            subjectId: subjectId,
            state: state!,
            snapshot: snapshot,
            selectedEpisodeId: selectedEpisodeId,
            onEpisodeSelected: onEpisodeSelected,
          ),
        ),
      ),
    );
  }
}

final class _SubjectDetailContent extends StatelessWidget {
  const _SubjectDetailContent({
    required this.controller,
    required this.subjectId,
    required this.state,
    required this.snapshot,
    required this.selectedEpisodeId,
    required this.onEpisodeSelected,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final BangumiSubjectDetailState state;
  final BangumiSubjectDetailSnapshot snapshot;
  final String? selectedEpisodeId;
  final ValueChanged<String> onEpisodeSelected;

  @override
  Widget build(BuildContext context) {
    final identity = <Widget>[
      _SubjectHero(controller: controller, snapshot: snapshot),
      _MetadataSection(subject: snapshot.subject),
      _TagsSection(tags: snapshot.subject.tags),
    ];
    final main = <Widget>[
      _EpisodeSection(
        controller: controller,
        subjectId: subjectId,
        state: state,
        snapshot: snapshot,
        selectedEpisodeId: selectedEpisodeId,
        onEpisodeSelected: onEpisodeSelected,
      ),
      _SummarySection(summary: snapshot.subject.summary),
      _CharactersSection(
        controller: controller,
        subjectId: subjectId,
        state: state,
        characters: snapshot.characters,
      ),
      _PersonsSection(
        controller: controller,
        subjectId: subjectId,
        state: state,
        persons: snapshot.persons,
      ),
      _RelationsSection(
        controller: controller,
        subjectId: subjectId,
        state: state,
        relations: snapshot.relations,
      ),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 1024;
        if (!wide) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (state.phase == BangumiDetailPhase.loading)
                const Padding(
                  padding: EdgeInsets.only(bottom: WynimeSpacing.sm),
                  child: LinearProgressIndicator(minHeight: 3),
                ),
              if (state.phase == BangumiDetailPhase.partial)
                _PartialBanner(controller: controller, subjectId: subjectId),
              identity.first,
              const SizedBox(height: WynimeSpacing.md),
              main[0],
              main[1],
              identity[1],
              identity[2],
              ...main.skip(2),
            ],
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (state.phase == BangumiDetailPhase.loading)
              const Padding(
                padding: EdgeInsets.only(bottom: WynimeSpacing.sm),
                child: LinearProgressIndicator(minHeight: 3),
              ),
            if (state.phase == BangumiDetailPhase.partial)
              _PartialBanner(controller: controller, subjectId: subjectId),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 352,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: identity,
                  ),
                ),
                const SizedBox(width: WynimeSpacing.lg),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: main,
                  ),
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}

final class _SubjectHero extends StatelessWidget {
  const _SubjectHero({required this.controller, required this.snapshot});

  final BangumiSessionController controller;
  final BangumiSubjectDetailSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final subject = snapshot.subject;
    final title = _subjectTitle(subject);
    return LayoutBuilder(
      builder: (context, constraints) {
        final posterWidth = constraints.maxWidth < 380 ? 96.0 : 112.0;
        final posterHeight = posterWidth * 1.5;
        return ClipRRect(
          borderRadius: BorderRadius.circular(WynimeRadii.large),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 270),
            child: Stack(
              children: [
                Positioned.fill(child: _HeroBackground(subject: subject)),
                Positioned.fill(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.black.withValues(alpha: 0.36),
                          Colors.black.withValues(alpha: 0.82),
                        ],
                      ),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(WynimeSpacing.md),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _SubjectArtwork(
                        imageUrl: subject.imageUrl,
                        semanticLabel: title,
                        width: posterWidth,
                        height: posterHeight,
                      ),
                      const SizedBox(width: WynimeSpacing.md),
                      Expanded(
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(minHeight: 238),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                title,
                                maxLines: 3,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.headlineSmall?.copyWith(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              if (subject.name.isNotEmpty &&
                                  subject.name != title)
                                Padding(
                                  padding: const EdgeInsets.only(
                                    top: WynimeSpacing.xxs,
                                  ),
                                  child: Text(
                                    subject.name,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      color: Colors.white70,
                                    ),
                                  ),
                                ),
                              const SizedBox(height: WynimeSpacing.xs),
                              Text(
                                _heroMetadata(subject),
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: Colors.white70,
                                ),
                              ),
                              const SizedBox(height: WynimeSpacing.sm),
                              Text(
                                _progressLabel(
                                  watched: snapshot.epStatus,
                                  total: subject.totalEpisodes ?? subject.eps,
                                  l10n: l10n,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              const SizedBox(height: WynimeSpacing.xs),
                              _RatingLine(subject: subject),
                              const SizedBox(height: WynimeSpacing.sm),
                              _CollectionStatusPill(
                                controller: controller,
                                subjectId: subject.id,
                                status: snapshot.collectionStatus,
                              ),
                              if (subject.collectionStats != null) ...[
                                const SizedBox(height: WynimeSpacing.sm),
                                _PublicStats(
                                  stats: subject.collectionStats!,
                                  light: true,
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

final class _HeroBackground extends StatelessWidget {
  const _HeroBackground({required this.subject});

  final BangumiSubject subject;

  @override
  Widget build(BuildContext context) {
    final fallback = ColoredBox(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: const SizedBox.expand(),
    );
    final imageUrl = subject.imageUrl;
    if (imageUrl == null) return fallback;
    return ImageFiltered(
      imageFilter: ui.ImageFilter.blur(sigmaX: 18, sigmaY: 18),
      child: Image.network(
        imageUrl.toString(),
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) => fallback,
        loadingBuilder: (context, child, progress) =>
            progress == null ? child : fallback,
      ),
    );
  }
}

final class _RatingLine extends StatelessWidget {
  const _RatingLine({required this.subject});

  final BangumiSubject subject;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final rating = subject.rating;
    if (rating == null && subject.rank == null) {
      return const SizedBox.shrink();
    }
    final theme = Theme.of(context);
    return Wrap(
      spacing: WynimeSpacing.sm,
      runSpacing: WynimeSpacing.xxs,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        if (rating != null) ...[
          Icon(Icons.star, size: 18, color: Colors.amber.shade300),
          Text(
            rating.score.toStringAsFixed(1),
            style: theme.textTheme.titleSmall?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w700,
            ),
          ),
          Text(
            l10n.subjectDetailVotesLabel(rating.total),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall?.copyWith(color: Colors.white70),
          ),
        ],
        if (subject.rank != null)
          Text(
            l10n.subjectDetailRankLabel(subject.rank!),
            style: theme.textTheme.bodySmall?.copyWith(color: Colors.white70),
          ),
      ],
    );
  }
}

final class _CollectionStatusPill extends StatelessWidget {
  const _CollectionStatusPill({
    required this.controller,
    required this.subjectId,
    required this.status,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final BangumiCollectionStatus? status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final label = status == null
        ? l10n.subjectDetailCollectionAction
        : _statusLabel(status!, l10n);
    return PopupMenuButton<BangumiCollectionStatus>(
      key: const ValueKey('bangumi-collection-status'),
      tooltip: l10n.subjectDetailCollectionAction,
      onSelected: (value) {
        unawaited(controller.setCollectionStatus(subjectId, value));
      },
      itemBuilder: (context) => [
        for (final value in BangumiCollectionStatus.values)
          PopupMenuItem(value: value, child: Text(_statusLabel(value, l10n))),
      ],
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: WynimeSpacing.sm,
          vertical: WynimeSpacing.xs,
        ),
        decoration: BoxDecoration(
          color: theme.colorScheme.primaryContainer,
          borderRadius: BorderRadius.circular(WynimeRadii.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              status == BangumiCollectionStatus.watching
                  ? Icons.play_arrow_rounded
                  : Icons.check_rounded,
              size: 18,
              color: theme.colorScheme.onPrimaryContainer,
            ),
            const SizedBox(width: WynimeSpacing.xxs),
            Text(
              label,
              style: theme.textTheme.labelLarge?.copyWith(
                color: theme.colorScheme.onPrimaryContainer,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(width: WynimeSpacing.xxs),
            Icon(
              Icons.expand_more,
              size: 18,
              color: theme.colorScheme.onPrimaryContainer,
            ),
          ],
        ),
      ),
    );
  }
}

final class _PublicStats extends StatelessWidget {
  const _PublicStats({required this.stats, this.light = false});

  final BangumiPublicCollectionStats stats;
  final bool light;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final color = light
        ? Colors.white70
        : Theme.of(context).colorScheme.onSurfaceVariant;
    final values = <(String, int)>[
      (l10n.libraryFilterWish, stats.wish),
      (l10n.libraryFilterWatching, stats.watching),
      (l10n.libraryFilterCompleted, stats.completed),
      (l10n.libraryFilterOnHold, stats.onHold),
      (l10n.libraryFilterDropped, stats.dropped),
    ];
    return Wrap(
      spacing: WynimeSpacing.sm,
      runSpacing: WynimeSpacing.xxs,
      children: [
        for (final value in values)
          Text(
            '${value.$2} ${value.$1}',
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: color),
          ),
      ],
    );
  }
}

final class _EpisodeSection extends StatelessWidget {
  const _EpisodeSection({
    required this.controller,
    required this.subjectId,
    required this.state,
    required this.snapshot,
    required this.selectedEpisodeId,
    required this.onEpisodeSelected,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final BangumiSubjectDetailState state;
  final BangumiSubjectDetailSnapshot snapshot;
  final String? selectedEpisodeId;
  final ValueChanged<String> onEpisodeSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final episodes = snapshot.episodes.episodes;
    final total = snapshot.episodes.total;
    final watched = snapshot.watchedEpisodeIds.length;
    final content = episodes.isEmpty
        ? Text(l10n.subjectDetailNoEpisodes)
        : Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SizedBox(
                height: 152,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: episodes.length,
                  separatorBuilder: (_, index) =>
                      const SizedBox(width: WynimeSpacing.sm),
                  itemBuilder: (context, index) {
                    final episode = episodes[index];
                    final selected = selectedEpisodeId == episode.id;
                    final isWatched = snapshot.watchedEpisodeIds.contains(
                      episode.id,
                    );
                    return _EpisodeCard(
                      episode: episode,
                      selected: selected,
                      watched: isWatched,
                      onTap: () => onEpisodeSelected(episode.id),
                    );
                  },
                ),
              ),
              if (selectedEpisodeId != null)
                _SelectedEpisodeActions(
                  subjectId: subjectId,
                  episodes: episodes,
                  selectedEpisodeId: selectedEpisodeId!,
                  watchedEpisodeIds: snapshot.watchedEpisodeIds,
                  controller: controller,
                ),
            ],
          );
    return _SectionSurface(
      title: l10n.subjectEpisodesLabel,
      icon: Icons.list_alt_outlined,
      trailing: Text(
        l10n.subjectDetailEpisodesProgress(watched, total),
        style: Theme.of(context).textTheme.bodySmall,
      ),
      child: _withSectionFailure(
        context,
        state,
        BangumiDetailSection.episodes,
        content,
        () => controller.retryDetailSection(
          subjectId,
          BangumiDetailSection.episodes,
        ),
      ),
    );
  }
}

final class _EpisodeCard extends StatelessWidget {
  const _EpisodeCard({
    required this.episode,
    required this.selected,
    required this.watched,
    required this.onTap,
  });

  final BangumiEpisode episode;
  final bool selected;
  final bool watched;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final title = episode.nameCn.isEmpty ? episode.name : episode.nameCn;
    return SizedBox(
      width: 190,
      child: Material(
        color: selected
            ? theme.colorScheme.secondaryContainer
            : theme.colorScheme.surfaceContainerHighest,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(WynimeRadii.medium),
          side: BorderSide(
            color: selected
                ? theme.colorScheme.primary
                : theme.colorScheme.outlineVariant,
            width: selected ? 2 : 1,
          ),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(WynimeRadii.medium),
          child: Padding(
            padding: const EdgeInsets.all(WynimeSpacing.sm),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        l10n.subjectDetailEpisodeNumber(
                          _episodeNumber(episode.sort),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelLarge,
                      ),
                    ),
                    Icon(
                      watched
                          ? Icons.check_circle
                          : Icons.radio_button_unchecked,
                      color: watched
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurfaceVariant,
                      size: 18,
                    ),
                  ],
                ),
                const SizedBox(height: WynimeSpacing.xs),
                Text(title, maxLines: 3, overflow: TextOverflow.ellipsis),
                const Spacer(),
                Text(
                  watched
                      ? l10n.subjectDetailMarkUnwatchedAction
                      : l10n.subjectDetailMarkWatchedAction,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: selected
                        ? theme.colorScheme.primary
                        : theme.colorScheme.onSurfaceVariant,
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

final class _SelectedEpisodeActions extends StatelessWidget {
  const _SelectedEpisodeActions({
    required this.subjectId,
    required this.episodes,
    required this.selectedEpisodeId,
    required this.watchedEpisodeIds,
    required this.controller,
  });

  final String subjectId;
  final List<BangumiEpisode> episodes;
  final String selectedEpisodeId;
  final Set<String> watchedEpisodeIds;
  final BangumiSessionController controller;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final episode = episodes
        .where((item) => item.id == selectedEpisodeId)
        .first;
    final watched = watchedEpisodeIds.contains(episode.id);
    return Padding(
      padding: const EdgeInsets.only(top: WynimeSpacing.sm),
      child: Align(
        alignment: Alignment.centerLeft,
        child: OutlinedButton.icon(
          key: ValueKey('subject-episode-watched-${episode.id}'),
          onPressed: () =>
              controller.setEpisodeWatched(subjectId, episode.id, !watched),
          icon: Icon(watched ? Icons.remove_done : Icons.done),
          label: Text(
            watched
                ? l10n.subjectDetailMarkUnwatchedAction
                : l10n.subjectDetailMarkWatchedAction,
          ),
        ),
      ),
    );
  }
}

final class _SummarySection extends StatefulWidget {
  const _SummarySection({required this.summary});

  final String summary;

  @override
  State<_SummarySection> createState() => _SummarySectionState();
}

final class _SummarySectionState extends State<_SummarySection> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final summary = widget.summary.trim();
    final hasMore = summary.length > 240;
    final text = summary.isEmpty ? l10n.subjectDetailUnknownLabel : summary;
    return _SectionSurface(
      title: l10n.subjectDetailSummaryTitle,
      icon: Icons.notes_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AnimatedSize(
            duration: const Duration(milliseconds: 180),
            alignment: Alignment.topCenter,
            child: Text(
              text,
              maxLines: hasMore && !_expanded ? 6 : null,
              overflow: hasMore && !_expanded
                  ? TextOverflow.fade
                  : TextOverflow.visible,
            ),
          ),
          if (hasMore)
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                key: const ValueKey('subject-summary-toggle'),
                onPressed: () => setState(() => _expanded = !_expanded),
                child: Text(
                  _expanded
                      ? l10n.subjectDetailCollapseAction
                      : l10n.subjectDetailShowMoreAction,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

final class _MetadataSection extends StatefulWidget {
  const _MetadataSection({required this.subject});

  final BangumiSubject subject;

  @override
  State<_MetadataSection> createState() => _MetadataSectionState();
}

final class _MetadataSectionState extends State<_MetadataSection> {
  static const _previewLimit = 10;
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final values = _metadataValues(widget.subject, l10n);
    final visible = _expanded
        ? values
        : values.take(_previewLimit).toList(growable: false);
    final hasMore = values.length > _previewLimit;
    return _SectionSurface(
      title: l10n.subjectDetailMetadataTitle,
      icon: Icons.info_outline,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (visible.isEmpty) Text(l10n.subjectDetailUnknownLabel),
          for (final value in visible) _MetadataRow(value: value),
          if (hasMore)
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                key: const ValueKey('subject-metadata-toggle'),
                onPressed: () => setState(() => _expanded = !_expanded),
                child: Text(
                  _expanded
                      ? l10n.subjectDetailCollapseAction
                      : l10n.subjectDetailShowMoreAction,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

final class _MetadataRow extends StatelessWidget {
  const _MetadataRow({required this.value});

  final (String, String) value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: WynimeSpacing.xs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 112,
            child: Text(
              value.$1,
              style: Theme.of(context).textTheme.labelLarge,
            ),
          ),
          Expanded(child: Text(value.$2)),
        ],
      ),
    );
  }
}

final class _TagsSection extends StatefulWidget {
  const _TagsSection({required this.tags});

  final List<BangumiTag> tags;

  @override
  State<_TagsSection> createState() => _TagsSectionState();
}

final class _TagsSectionState extends State<_TagsSection> {
  static const _previewLimit = 10;
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tags = [...widget.tags]
      ..sort((left, right) => right.count.compareTo(left.count));
    final visible = _expanded
        ? tags
        : tags.take(_previewLimit).toList(growable: false);
    final hasMore = tags.length > _previewLimit;
    return _SectionSurface(
      title: l10n.subjectDetailTagsTitle,
      icon: Icons.sell_outlined,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (visible.isEmpty)
            Text(l10n.subjectDetailUnknownLabel)
          else
            Wrap(
              spacing: WynimeSpacing.xs,
              runSpacing: WynimeSpacing.xs,
              children: [
                for (final tag in visible)
                  Chip(label: Text('${tag.name} · ${tag.count}')),
              ],
            ),
          if (hasMore)
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                key: const ValueKey('subject-tags-toggle'),
                onPressed: () => setState(() => _expanded = !_expanded),
                child: Text(
                  _expanded
                      ? l10n.subjectDetailCollapseAction
                      : l10n.subjectDetailShowMoreAction,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

final class _CharactersSection extends StatefulWidget {
  const _CharactersSection({
    required this.controller,
    required this.subjectId,
    required this.state,
    required this.characters,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final BangumiSubjectDetailState state;
  final List<BangumiCharacter> characters;

  @override
  State<_CharactersSection> createState() => _CharactersSectionState();
}

final class _CharactersSectionState extends State<_CharactersSection> {
  static const _previewLimit = 6;
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final visible = _expanded
        ? widget.characters
        : widget.characters.take(_previewLimit).toList(growable: false);
    final hasMore = widget.characters.length > _previewLimit;
    final content = widget.characters.isEmpty
        ? Text(l10n.subjectDetailNoCharacters)
        : SizedBox(
            height: 160,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: visible.length,
              separatorBuilder: (_, index) =>
                  const SizedBox(width: WynimeSpacing.sm),
              itemBuilder: (context, index) {
                return _CharacterPreviewCard(character: visible[index]);
              },
            ),
          );
    return _SectionSurface(
      title: l10n.subjectDetailCharactersTitle,
      icon: Icons.people_alt_outlined,
      trailing: hasMore
          ? TextButton(
              key: const ValueKey('subject-characters-view-all'),
              onPressed: () => setState(() => _expanded = !_expanded),
              child: Text(
                _expanded
                    ? l10n.subjectDetailCollapseAction
                    : l10n.subjectDetailViewAllAction,
              ),
            )
          : null,
      child: _withSectionFailure(
        context,
        widget.state,
        BangumiDetailSection.characters,
        content,
        () => widget.controller.retryDetailSection(
          widget.subjectId,
          BangumiDetailSection.characters,
        ),
      ),
    );
  }
}

final class _CharacterPreviewCard extends StatelessWidget {
  const _CharacterPreviewCard({required this.character});

  final BangumiCharacter character;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final actor = character.actors.isEmpty
        ? character.relation
        : l10n.subjectDetailActorLabel(character.actors.first.name);
    return SizedBox(
      width: 116,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: _SubjectArtwork(
              imageUrl: character.imageUrl,
              semanticLabel: character.name,
              width: 72,
              height: 72,
              circular: true,
            ),
          ),
          const SizedBox(height: WynimeSpacing.xs),
          Text(
            character.name,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelLarge,
          ),
          if (actor != null)
            Text(
              actor,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
        ],
      ),
    );
  }
}

final class _PersonsSection extends StatefulWidget {
  const _PersonsSection({
    required this.controller,
    required this.subjectId,
    required this.state,
    required this.persons,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final BangumiSubjectDetailState state;
  final List<BangumiPersonCredit> persons;

  @override
  State<_PersonsSection> createState() => _PersonsSectionState();
}

final class _PersonsSectionState extends State<_PersonsSection> {
  static const _previewLimit = 6;
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final visible = _expanded
        ? widget.persons
        : widget.persons.take(_previewLimit).toList(growable: false);
    final hasMore = widget.persons.length > _previewLimit;
    final content = widget.persons.isEmpty
        ? Text(l10n.subjectDetailNoPersons)
        : LayoutBuilder(
            builder: (context, constraints) {
              final columns = constraints.maxWidth >= 620 ? 3 : 2;
              return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: visible.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: columns,
                  mainAxisExtent: 76,
                  crossAxisSpacing: WynimeSpacing.sm,
                  mainAxisSpacing: WynimeSpacing.xs,
                ),
                itemBuilder: (context, index) {
                  return _PersonPreviewTile(person: visible[index]);
                },
              );
            },
          );
    return _SectionSurface(
      title: l10n.subjectDetailPersonsTitle,
      icon: Icons.badge_outlined,
      trailing: hasMore
          ? TextButton(
              key: const ValueKey('subject-staff-view-all'),
              onPressed: () => setState(() => _expanded = !_expanded),
              child: Text(
                _expanded
                    ? l10n.subjectDetailCollapseAction
                    : l10n.subjectDetailViewAllAction,
              ),
            )
          : null,
      child: _withSectionFailure(
        context,
        widget.state,
        BangumiDetailSection.persons,
        content,
        () => widget.controller.retryDetailSection(
          widget.subjectId,
          BangumiDetailSection.persons,
        ),
      ),
    );
  }
}

final class _PersonPreviewTile extends StatelessWidget {
  const _PersonPreviewTile({required this.person});

  final BangumiPersonCredit person;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final role =
        person.relation ??
        (person.career.isEmpty
            ? l10n.subjectDetailUnknownLabel
            : person.career.join(' · '));
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SubjectArtwork(
          imageUrl: person.imageUrl,
          semanticLabel: person.name,
          width: 44,
          height: 58,
          circular: true,
        ),
        const SizedBox(width: WynimeSpacing.xs),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                person.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge,
              ),
              Text(
                role,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ],
    );
  }
}

final class _RelationsSection extends StatefulWidget {
  const _RelationsSection({
    required this.controller,
    required this.subjectId,
    required this.state,
    required this.relations,
  });

  final BangumiSessionController controller;
  final String subjectId;
  final BangumiSubjectDetailState state;
  final List<BangumiSubjectRelation> relations;

  @override
  State<_RelationsSection> createState() => _RelationsSectionState();
}

final class _RelationsSectionState extends State<_RelationsSection> {
  static const _previewLimit = 5;
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final visible = _expanded
        ? widget.relations
        : widget.relations.take(_previewLimit).toList(growable: false);
    final hasMore = widget.relations.length > _previewLimit;
    final content = widget.relations.isEmpty
        ? Text(l10n.subjectDetailNoRelations)
        : SizedBox(
            height: 126,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: visible.length,
              separatorBuilder: (_, index) =>
                  const SizedBox(width: WynimeSpacing.sm),
              itemBuilder: (context, index) {
                return _RelationPreviewCard(relation: visible[index]);
              },
            ),
          );
    return _SectionSurface(
      title: l10n.subjectDetailRelationsTitle,
      icon: Icons.account_tree_outlined,
      trailing: hasMore
          ? TextButton(
              key: const ValueKey('subject-relations-view-all'),
              onPressed: () => setState(() => _expanded = !_expanded),
              child: Text(
                _expanded
                    ? l10n.subjectDetailCollapseAction
                    : l10n.subjectDetailViewAllAction,
              ),
            )
          : null,
      child: _withSectionFailure(
        context,
        widget.state,
        BangumiDetailSection.relations,
        content,
        () => widget.controller.retryDetailSection(
          widget.subjectId,
          BangumiDetailSection.relations,
        ),
      ),
    );
  }
}

final class _RelationPreviewCard extends StatelessWidget {
  const _RelationPreviewCard({required this.relation});

  final BangumiSubjectRelation relation;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final title = relation.nameCn.isEmpty ? relation.name : relation.nameCn;
    return SizedBox(
      width: 152,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _SubjectArtwork(
            imageUrl: relation.imageUrl,
            semanticLabel: title,
            width: 52,
            height: 78,
          ),
          const SizedBox(width: WynimeSpacing.xs),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 4,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge,
                ),
                if (relation.relation != null)
                  Text(
                    relation.relation!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall,
                  )
                else
                  Text(
                    l10n.subjectDetailUnknownLabel,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

final class _SectionSurface extends StatelessWidget {
  const _SectionSurface({
    required this.title,
    required this.icon,
    required this.child,
    this.trailing,
  });

  final String title;
  final IconData icon;
  final Widget child;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(bottom: WynimeSpacing.sm),
      padding: const EdgeInsets.all(WynimeSpacing.md),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(WynimeRadii.large),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Icon(icon, size: 20),
              const SizedBox(width: WynimeSpacing.xs),
              Expanded(
                child: Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleMedium,
                ),
              ),
              if (trailing != null) ...[
                const SizedBox(width: WynimeSpacing.xs),
                Flexible(child: trailing!),
              ],
            ],
          ),
          const SizedBox(height: WynimeSpacing.sm),
          child,
        ],
      ),
    );
  }
}

final class _PartialBanner extends StatelessWidget {
  const _PartialBanner({required this.controller, required this.subjectId});

  final BangumiSessionController controller;
  final String subjectId;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Container(
      margin: const EdgeInsets.only(bottom: WynimeSpacing.sm),
      padding: const EdgeInsets.all(WynimeSpacing.sm),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(WynimeRadii.medium),
      ),
      child: Row(
        children: [
          Icon(
            Icons.warning_amber_outlined,
            color: Theme.of(context).colorScheme.onErrorContainer,
          ),
          const SizedBox(width: WynimeSpacing.sm),
          Expanded(child: Text(l10n.subjectDetailPartialLabel)),
          TextButton(
            onPressed: () => controller.retryDetail(subjectId),
            child: Text(l10n.subjectDetailRetryAction),
          ),
        ],
      ),
    );
  }
}

final class _FatalDetail extends StatelessWidget {
  const _FatalDetail({required this.description, required this.onRetry});

  final String description;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.lg),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.cloud_off_outlined,
                size: 48,
                color: Theme.of(context).colorScheme.error,
              ),
              const SizedBox(height: WynimeSpacing.md),
              Text(
                l10n.subjectDetailFatalTitle,
                style: Theme.of(context).textTheme.titleLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: WynimeSpacing.sm),
              Text(description, textAlign: TextAlign.center),
              const SizedBox(height: WynimeSpacing.md),
              FilledButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: Text(l10n.subjectDetailRetryAction),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

Widget _withSectionFailure(
  BuildContext context,
  BangumiSubjectDetailState state,
  BangumiDetailSection section,
  Widget content,
  VoidCallback onRetry,
) {
  final l10n = AppLocalizations.of(context);
  final error = state.errors[section];
  if (error == null) return content;
  return Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Container(
        padding: const EdgeInsets.all(WynimeSpacing.sm),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.errorContainer,
          borderRadius: BorderRadius.circular(WynimeRadii.small),
        ),
        child: Row(
          children: [
            Expanded(child: Text(l10n.subjectDetailSectionError(error))),
            TextButton(
              onPressed: onRetry,
              child: Text(l10n.subjectDetailRetrySectionAction),
            ),
          ],
        ),
      ),
      const SizedBox(height: WynimeSpacing.sm),
      content,
    ],
  );
}

final class _SubjectArtwork extends StatelessWidget {
  const _SubjectArtwork({
    required this.imageUrl,
    required this.semanticLabel,
    required this.width,
    required this.height,
    this.circular = false,
  });

  final Uri? imageUrl;
  final String semanticLabel;
  final double width;
  final double height;
  final bool circular;

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
      borderRadius: BorderRadius.circular(
        circular ? WynimeRadii.pill : WynimeRadii.medium,
      ),
      child: SizedBox(width: width, height: height, child: image),
    );
  }
}

List<(String, String)> _metadataValues(
  BangumiSubject subject,
  AppLocalizations l10n,
) {
  final values = <(String, String)>[];
  if (subject.airDate != null) {
    values.add((l10n.subjectDetailDateLabel, _dateLabel(subject.airDate!)));
  }
  if (subject.platform?.trim().isNotEmpty == true) {
    values.add((l10n.subjectDetailPlatformLabel, subject.platform!.trim()));
  }
  final total = subject.totalEpisodes ?? subject.eps;
  if (total != null && total > 0) {
    values.add((l10n.subjectEpisodesLabel, total.toString()));
  }
  if (subject.volumes != null && subject.volumes! > 0) {
    values.add((l10n.subjectDetailMetadataTitle, subject.volumes.toString()));
  }
  for (final item in subject.infobox) {
    final key = item.key.trim();
    final value = item.values
        .map((item) => item.text.trim())
        .where((item) => item.isNotEmpty)
        .join(' / ');
    if (key.isEmpty || value.isEmpty || !_isMeaningfulMetadataKey(key)) {
      continue;
    }
    values.add((key, value));
  }
  return values;
}

bool _isMeaningfulMetadataKey(String key) {
  final normalized = key.toLowerCase().replaceAll(RegExp(r'\s+'), '');
  return normalized != '作品資訊0' &&
      normalized != '作品信息0' &&
      normalized != 'information0' &&
      normalized != 'info0' &&
      normalized != '作品資訊' &&
      normalized != '作品信息' &&
      normalized != 'information' &&
      normalized != 'info';
}

String _progressLabel({
  required int? watched,
  required int? total,
  required AppLocalizations l10n,
}) {
  final validTotal = total != null && total > 0;
  if (watched != null && validTotal) {
    return l10n.libraryProgressKnown(watched, total);
  }
  if (validTotal) return l10n.libraryProgressTotal(total);
  if (watched != null) return l10n.libraryProgressWatchedOnly(watched);
  return l10n.libraryProgressUnknown;
}

String _heroMetadata(BangumiSubject subject) {
  final values = <String>[];
  if (subject.airDate != null) values.add(_dateLabel(subject.airDate!));
  if (subject.platform?.trim().isNotEmpty == true) {
    values.add(subject.platform!.trim());
  }
  final total = subject.totalEpisodes ?? subject.eps;
  if (total != null && total > 0) values.add('$total eps');
  return values.isEmpty ? '' : values.join(' · ');
}

String _subjectTitle(BangumiSubject subject) =>
    subject.nameCn.isEmpty ? subject.name : subject.nameCn;

String _episodeNumber(double value) => value == value.truncateToDouble()
    ? value.toInt().toString()
    : value.toString();

String _dateLabel(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';

String _statusLabel(BangumiCollectionStatus status, AppLocalizations l10n) =>
    switch (status) {
      BangumiCollectionStatus.wish => l10n.libraryFilterWish,
      BangumiCollectionStatus.watching => l10n.libraryFilterWatching,
      BangumiCollectionStatus.completed => l10n.libraryFilterCompleted,
      BangumiCollectionStatus.onHold => l10n.libraryFilterOnHold,
      BangumiCollectionStatus.dropped => l10n.libraryFilterDropped,
    };

import 'dart:async';

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

    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 900;
        final content = _SubjectDetailContent(
          controller: controller,
          subjectId: subjectId,
          state: state!,
          snapshot: snapshot,
          selectedEpisodeId: selectedEpisodeId,
          onEpisodeSelected: onEpisodeSelected,
        );
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
              child: wide ? content : content,
            ),
          ),
        );
      },
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
    final l10n = AppLocalizations.of(context);
    final summary = <Widget>[
      _SectionCard(
        title: l10n.subjectDetailSummaryTitle,
        icon: Icons.notes_outlined,
        child: Text(
          snapshot.subject.summary.isEmpty
              ? l10n.subjectDetailUnknownLabel
              : snapshot.subject.summary,
        ),
      ),
      _MetadataSection(subject: snapshot.subject),
      _TagsSection(tags: snapshot.subject.tags),
    ];
    final secondary = <Widget>[
      _EpisodeSection(
        controller: controller,
        subjectId: subjectId,
        state: state,
        snapshot: snapshot,
        selectedEpisodeId: selectedEpisodeId,
        onEpisodeSelected: onEpisodeSelected,
      ),
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
        _SubjectHero(controller: controller, snapshot: snapshot),
        const SizedBox(height: WynimeSpacing.md),
        LayoutBuilder(
          builder: (context, constraints) {
            if (constraints.maxWidth < 900) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ...summary,
                  const SizedBox(height: WynimeSpacing.sm),
                  ...secondary,
                ],
              );
            }
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(width: 320, child: Column(children: summary)),
                const SizedBox(width: WynimeSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: secondary,
                  ),
                ),
              ],
            );
          },
        ),
      ],
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
    final subject = snapshot.subject;
    final title = _subjectTitle(subject);
    final score = subject.rating;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.md),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final compact = constraints.maxWidth < 600;
            final artwork = _SubjectArtwork(
              imageUrl: subject.imageUrl,
              semanticLabel: title,
              width: compact ? 112 : 156,
              height: compact ? 164 : 228,
            );
            final details = Expanded(
              child: Padding(
                padding: const EdgeInsets.only(left: WynimeSpacing.md),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    if (subject.name.isNotEmpty && subject.name != title)
                      Padding(
                        padding: const EdgeInsets.only(top: WynimeSpacing.xxs),
                        child: Text(subject.name),
                      ),
                    const SizedBox(height: WynimeSpacing.sm),
                    Wrap(
                      spacing: WynimeSpacing.xs,
                      runSpacing: WynimeSpacing.xs,
                      children: [
                        if (score != null)
                          Chip(
                            avatar: const Icon(Icons.star, size: 16),
                            label: Text(
                              l10n.subjectDetailScoreLabel(score.score),
                            ),
                          ),
                        if (subject.rank != null)
                          Chip(
                            label: Text(
                              l10n.subjectDetailRankLabel(subject.rank!),
                            ),
                          ),
                        if (score != null)
                          Chip(
                            label: Text(
                              l10n.subjectDetailVotesLabel(score.total),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: WynimeSpacing.sm),
                    DropdownButtonFormField<BangumiCollectionStatus>(
                      key: const ValueKey('bangumi-collection-status'),
                      initialValue: snapshot.collectionStatus,
                      decoration: InputDecoration(
                        labelText: l10n.subjectCollectionLabel,
                        border: const OutlineInputBorder(),
                      ),
                      items: [
                        for (final status in BangumiCollectionStatus.values)
                          DropdownMenuItem(
                            value: status,
                            child: Text(_statusLabel(status, l10n)),
                          ),
                      ],
                      onChanged: (value) {
                        if (value != null) {
                          controller.setCollectionStatus(subject.id, value);
                        }
                      },
                    ),
                    if (subject.collectionStats != null) ...[
                      const SizedBox(height: WynimeSpacing.sm),
                      Text(
                        l10n.subjectDetailPublicStatsTitle,
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      const SizedBox(height: WynimeSpacing.xs),
                      _PublicStats(stats: subject.collectionStats!),
                    ],
                  ],
                ),
              ),
            );
            return compact
                ? Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [artwork, details],
                  )
                : Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [artwork, details],
                  );
          },
        ),
      ),
    );
  }
}

final class _PublicStats extends StatelessWidget {
  const _PublicStats({required this.stats});

  final BangumiPublicCollectionStats stats;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final values = <(String, int)>[
      (l10n.libraryFilterWish, stats.wish),
      (l10n.libraryFilterWatching, stats.watching),
      (l10n.libraryFilterCompleted, stats.completed),
      (l10n.libraryFilterOnHold, stats.onHold),
      (l10n.libraryFilterDropped, stats.dropped),
    ];
    return Wrap(
      spacing: WynimeSpacing.xs,
      runSpacing: WynimeSpacing.xs,
      children: [
        for (final value in values)
          Chip(label: Text(l10n.libraryStatusCount(value.$1, value.$2))),
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
              Text(
                l10n.subjectDetailEpisodesProgress(watched, total),
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: WynimeSpacing.sm),
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
    return _SectionCard(
      title: l10n.subjectEpisodesLabel,
      icon: Icons.list_alt_outlined,
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
                if (selected)
                  Text(
                    watched
                        ? l10n.subjectDetailMarkUnwatchedAction
                        : l10n.subjectDetailMarkWatchedAction,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.primary,
                    ),
                  )
                else
                  Text(
                    watched
                        ? l10n.subjectDetailMarkUnwatchedAction
                        : l10n.subjectDetailMarkWatchedAction,
                    style: theme.textTheme.labelSmall,
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

final class _MetadataSection extends StatelessWidget {
  const _MetadataSection({required this.subject});

  final BangumiSubject subject;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final values = <(String, String)>[];
    if (subject.airDate != null) {
      values.add((l10n.subjectDetailDateLabel, _dateLabel(subject.airDate!)));
    }
    if (subject.platform?.isNotEmpty == true) {
      values.add((l10n.subjectDetailPlatformLabel, subject.platform!));
    }
    final total = subject.totalEpisodes ?? subject.eps;
    if (total != null) {
      values.add((l10n.subjectEpisodesLabel, '$total'));
    }
    if (subject.volumes != null) {
      values.add((l10n.subjectDetailMetadataTitle, '${subject.volumes}'));
    }
    for (final item in subject.infobox) {
      values.add((
        item.key,
        item.values.map((value) => value.text).join(' / '),
      ));
    }
    return _SectionCard(
      title: l10n.subjectDetailMetadataTitle,
      icon: Icons.info_outline,
      child: values.isEmpty
          ? Text(l10n.subjectDetailUnknownLabel)
          : Column(
              children: [
                for (final value in values)
                  Padding(
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
                  ),
              ],
            ),
    );
  }
}

final class _TagsSection extends StatelessWidget {
  const _TagsSection({required this.tags});

  final List<BangumiTag> tags;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return _SectionCard(
      title: l10n.subjectDetailTagsTitle,
      icon: Icons.sell_outlined,
      child: tags.isEmpty
          ? Text(l10n.subjectDetailUnknownLabel)
          : Wrap(
              spacing: WynimeSpacing.xs,
              runSpacing: WynimeSpacing.xs,
              children: [
                for (final tag in tags)
                  Chip(label: Text('${tag.name} · ${tag.count}')),
              ],
            ),
    );
  }
}

final class _CharactersSection extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final content = characters.isEmpty
        ? Text(l10n.subjectDetailNoCharacters)
        : Wrap(
            spacing: WynimeSpacing.sm,
            runSpacing: WynimeSpacing.sm,
            children: [
              for (final character in characters)
                SizedBox(
                  width: 230,
                  child: ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: _SubjectArtwork(
                      imageUrl: character.imageUrl,
                      semanticLabel: character.name,
                      width: 48,
                      height: 64,
                    ),
                    title: Text(character.name),
                    subtitle: character.actors.isEmpty
                        ? (character.relation == null
                              ? null
                              : Text(character.relation!))
                        : Text(
                            l10n.subjectDetailActorLabel(
                              character.actors.first.name,
                            ),
                          ),
                  ),
                ),
            ],
          );
    return _SectionCard(
      title: l10n.subjectDetailCharactersTitle,
      icon: Icons.people_alt_outlined,
      child: _withSectionFailure(
        context,
        state,
        BangumiDetailSection.characters,
        content,
        () => controller.retryDetailSection(
          subjectId,
          BangumiDetailSection.characters,
        ),
      ),
    );
  }
}

final class _PersonsSection extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final content = persons.isEmpty
        ? Text(l10n.subjectDetailNoPersons)
        : Column(
            children: [
              for (final person in persons)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: _SubjectArtwork(
                    imageUrl: person.imageUrl,
                    semanticLabel: person.name,
                    width: 48,
                    height: 64,
                  ),
                  title: Text(person.name),
                  subtitle: Text(
                    person.relation ??
                        (person.career.isEmpty
                            ? l10n.subjectDetailUnknownLabel
                            : person.career.join(' · ')),
                  ),
                ),
            ],
          );
    return _SectionCard(
      title: l10n.subjectDetailPersonsTitle,
      icon: Icons.badge_outlined,
      child: _withSectionFailure(
        context,
        state,
        BangumiDetailSection.persons,
        content,
        () => controller.retryDetailSection(
          subjectId,
          BangumiDetailSection.persons,
        ),
      ),
    );
  }
}

final class _RelationsSection extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final content = relations.isEmpty
        ? Text(l10n.subjectDetailNoRelations)
        : Wrap(
            spacing: WynimeSpacing.sm,
            runSpacing: WynimeSpacing.xs,
            children: [
              for (final relation in relations)
                Chip(
                  avatar: _SubjectArtwork(
                    imageUrl: relation.imageUrl,
                    semanticLabel: relation.nameCn.isEmpty
                        ? relation.name
                        : relation.nameCn,
                    width: 32,
                    height: 40,
                  ),
                  label: Text(
                    relation.nameCn.isEmpty ? relation.name : relation.nameCn,
                  ),
                ),
            ],
          );
    return _SectionCard(
      title: l10n.subjectDetailRelationsTitle,
      icon: Icons.account_tree_outlined,
      child: _withSectionFailure(
        context,
        state,
        BangumiDetailSection.relations,
        content,
        () => controller.retryDetailSection(
          subjectId,
          BangumiDetailSection.relations,
        ),
      ),
    );
  }
}

final class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.title,
    required this.icon,
    required this.child,
  });

  final String title;
  final IconData icon;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: WynimeSpacing.sm),
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(icon, size: 20),
                const SizedBox(width: WynimeSpacing.xs),
                Expanded(
                  child: Text(
                    title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: WynimeSpacing.sm),
            child,
          ],
        ),
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
    return Card(
      color: Theme.of(context).colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(WynimeSpacing.sm),
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
        color: Theme.of(context).colorScheme.errorContainer,
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

String _subjectTitle(BangumiSubject subject) =>
    subject.nameCn.isEmpty ? subject.name : subject.nameCn;

String _episodeNumber(double value) =>
    value == value.truncateToDouble() ? value.toInt().toString() : '$value';

String _dateLabel(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

String _statusLabel(BangumiCollectionStatus status, AppLocalizations l10n) =>
    switch (status) {
      BangumiCollectionStatus.wish => l10n.libraryFilterWish,
      BangumiCollectionStatus.watching => l10n.libraryFilterWatching,
      BangumiCollectionStatus.completed => l10n.libraryFilterCompleted,
      BangumiCollectionStatus.onHold => l10n.libraryFilterOnHold,
      BangumiCollectionStatus.dropped => l10n.libraryFilterDropped,
    };

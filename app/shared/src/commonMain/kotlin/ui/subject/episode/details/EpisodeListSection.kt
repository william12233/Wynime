package com.wynime.app.ui.subject.episode.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.preferredDisplayName
import com.wynime.app.domain.media.cache.EpisodeCacheStatus
import com.wynime.app.ui.foundation.LocalEpisodeProgressSettings
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.LongClickProgressFill
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.icons.PlayingIcon
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.theme.WynimeTheme
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_episode_collapse
import com.wynime.app.ui.lang.subject_episode_episode_list
import com.wynime.app.ui.lang.subject_episode_expand
import com.wynime.app.ui.lang.subject_episode_view_more_episodes
import com.wynime.app.ui.subject.AiringLabel
import com.wynime.app.ui.subject.AiringLabelState
import com.wynime.app.ui.subject.createTestAiringLabelState
import com.wynime.app.ui.subject.episode.details.components.EpisodeGrid
import com.wynime.app.ui.subject.episode.list.EpisodeCellLabel
import com.wynime.app.ui.subject.episode.list.EpisodePlayProgressBar
import com.wynime.app.ui.subject.episode.list.EpisodeStillBackground
import com.wynime.app.ui.subject.episode.list.EpisodeWatchedBadge
import com.wynime.app.ui.subject.episode.list.EpisodeStillDefaults
import com.wynime.app.ui.subject.episode.details.components.PaginatedEpisodeList
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun EpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    expanded: Boolean,
    airingLabelState: AiringLabelState,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo1()
    val isWideLayout = windowAdaptiveInfo.isWidthAtLeastMedium

    if (isWideLayout) {

        WideEpisodeListSection(
            episodeCarouselState = episodeCarouselState,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            modifier = modifier,
        )
    } else {

        NarrowEpisodeListSection(
            episodeCarouselState = episodeCarouselState,
            airingLabelState = airingLabelState,
            modifier = modifier,
        )
    }
}

@Composable
private fun WideEpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
) {
    val episodeListText = stringResource(Lang.subject_episode_episode_list)
    val collapseText = stringResource(Lang.subject_episode_collapse)
    val expandText = stringResource(Lang.subject_episode_expand)
    Box(modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().offset(y = (-1).dp),
                ) {
                    Column(modifier = Modifier.padding(top = 64.dp)) {
                        val listState = rememberLazyListState()

                        LaunchedEffect(expanded) {
                            if (expanded) {
                                val playingIndex = episodeCarouselState.episodes.indexOfFirst {
                                    episodeCarouselState.isPlaying(it)
                                }
                                if (playingIndex >= 0) {
                                    listState.animateScrollToItem(playingIndex)
                                }
                            }
                        }

                        if (episodeCarouselState.episodes.size > 100) {
                            PaginatedEpisodeList(
                                groups = episodeCarouselState.groups,
                                episodeCarouselState = episodeCarouselState,
                                listState = listState,
                                modifier = Modifier.height(360.dp),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 360.dp),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                            ) {
                                items(
                                    items = episodeCarouselState.episodes,
                                    key = { it.episodeId },
                                ) { episode ->
                                    val isWatched = episode.collectionType.isDoneOrDropped()
                                    val isPlaying = episodeCarouselState.isPlaying(episode)

                                    EpisodeListSectionItem(
                                        episode = episode,
                                        isPlaying = isPlaying,
                                        isWatched = isWatched,
                                        onClick = { episodeCarouselState.onSelect(episode) },
                                        onLongClick = {
                                            val newType = if (episode.collectionType.isDoneOrDropped()) {
                                                UnifiedCollectionType.NOT_COLLECTED
                                            } else {
                                                UnifiedCollectionType.DONE
                                            }
                                            episodeCarouselState.setCollectionType(episode, newType)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        episodeListText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.List,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) collapseText else expandText,
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier.combinedClickable { onToggleExpanded() },
            )
        }
    }
}

@Composable
private fun NarrowEpisodeListSection(
    episodeCarouselState: EpisodeCarouselState,
    airingLabelState: AiringLabelState,
    modifier: Modifier = Modifier,
) {
    val episodeListText = stringResource(Lang.subject_episode_episode_list)
    val viewMoreEpisodesText = stringResource(Lang.subject_episode_view_more_episodes)
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val horizontalListState = rememberLazyListState()
    var hasInitialScrolled by remember { mutableStateOf(false) }
    val showImages = LocalEpisodeProgressSettings.current.showEpisodeImages

    Column(modifier.padding(horizontal = 16.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    episodeListText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onDoubleClick = {
                            val playingIndex = episodeCarouselState.episodes.indexOfFirst {
                                episodeCarouselState.isPlaying(it)
                            }
                            if (playingIndex >= 0) {
                                coroutineScope.launch {
                                    horizontalListState.animateScrollToItem(playingIndex)
                                }
                            }
                        },
                    ),
                )
            }
            Row(
                modifier = Modifier.combinedClickable { showBottomSheet = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiringLabel(
                    airingLabelState,
                    modifier = Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    ),
                    progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = viewMoreEpisodesText,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val density = LocalDensity.current

        LaunchedEffect(episodeCarouselState.episodes) {
            if (!hasInitialScrolled && episodeCarouselState.episodes.isNotEmpty()) {
                val playingIndex = episodeCarouselState.episodes.indexOfFirst {
                    episodeCarouselState.isPlaying(it)
                }
                if (playingIndex >= 0) {
                    horizontalListState.animateScrollToItem(
                        playingIndex,

                        with(density) {
                            -48.dp.roundToPx()
                        },
                    )
                    hasInitialScrolled = true
                }
            }
        }

        LazyRow(
            state = horizontalListState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            items(
                items = episodeCarouselState.episodes,
                key = { it.episodeId },
            ) { episode ->
                EpisodeCard(
                    episode = episode,
                    isPlaying = episodeCarouselState.isPlaying(episode),
                    onClick = { episodeCarouselState.onSelect(episode) },
                    onLongClick = {
                        val newType = if (episode.collectionType.isDoneOrDropped()) {
                            UnifiedCollectionType.NOT_COLLECTED
                        } else {
                            UnifiedCollectionType.DONE
                        }
                        episodeCarouselState.setCollectionType(episode, newType)
                    },
                    showImage = showImages,
                    playProgress = episodeCarouselState.playProgress(episode),
                )
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) },
            modifier = modifier,
        ) {
            Column {
                TopAppBar(
                    title = { Text(episodeListText) },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BottomSheetDefaults.ContainerColor,
                    ),
                )

                EpisodeGrid(
                    episodeCarouselState = episodeCarouselState,
                    onEpisodeClick = { episode ->
                        episodeCarouselState.onSelect(episode)
                        showBottomSheet = false
                    },
                    isVisible = true,
                    showImages = showImages,
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeCollectionInfo,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showImage: Boolean = true,
    playProgress: Float? = null,
) {
    val isWatched = episode.collectionType.isDoneOrDropped()
    val isDone = episode.collectionType == UnifiedCollectionType.DONE
    val interactionSource = remember { MutableInteractionSource() }
    val still = episode.episodeInfo.imageMedium?.takeIf { showImage }

    val stillBorder = if (still != null && isPlaying) 2.dp else 0.dp
    val sortColor = when {
        still != null -> EpisodeStillDefaults.contentColor
        isPlaying -> MaterialTheme.colorScheme.primary
        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> LocalContentColor.current
    }
    val nameColor = when {
        still != null -> EpisodeStillDefaults.secondaryContentColor
        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.secondaryContainer
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (still != null && isPlaying) BorderStroke(stillBorder, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .height(72.dp)
            .aspectRatio(16f / 9)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (still != null) {
                EpisodeStillBackground(
                    imageUrl = still,
                    highlighted = isPlaying,
                    modifier = Modifier.matchParentSize(),
                )
            }
            LongClickProgressFill(
                interactionSource = interactionSource,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.matchParentSize(),
            )
            EpisodeCellLabel(
                sort = episode.episodeInfo.sort.toString(),
                name = episode.episodeInfo.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                sortColor = sortColor,
                nameColor = nameColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                playingIndicator = if (isPlaying) {
                    { PlayingIcon(width = 20.dp, height = 12.dp, color = sortColor) }
                } else {
                    null
                },
            )
            if (isDone) {
                EpisodeWatchedBadge(
                    onStill = still != null,
                    onClick = onLongClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            } else if (playProgress != null) {
                EpisodePlayProgressBar(
                    progress = playProgress,
                    onStill = still != null,

                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = stillBorder, end = stillBorder, bottom = stillBorder),
                )
            }
        }
    }
}

@Composable
private fun EpisodeListSectionItem(
    episode: EpisodeCollectionInfo,
    isPlaying: Boolean,
    isWatched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val containerColor = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isWatched -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        LongClickProgressFill(
            interactionSource = interactionSource,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.matchParentSize(),
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = "${episode.episodeInfo.sort}  " +
                        episode.episodeInfo.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                    color = when {
                        isPlaying -> MaterialTheme.colorScheme.primary
                        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> LocalContentColor.current
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                if (isPlaying) {
                    PlayingIcon()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
internal fun PreviewEpisodeListSectionNarrow() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    WynimeTheme {
        Surface {
            EpisodeListSection(
                episodeCarouselState = remember {
                    EpisodeCarouselState(
                        episodes = mutableStateOf(PreviewEpisodeCollections),
                        playingEpisode = mutableStateOf(PreviewEpisodeCollections.getOrNull(2)),
                        cacheStatus = { EpisodeCacheStatus.NotCached },
                        onSelect = {},
                        onChangeCollectionType = { _, _ -> },
                        backgroundScope = scope,
                    )
                },
                expanded = false,
                airingLabelState = createTestAiringLabelState(),
                onToggleExpanded = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

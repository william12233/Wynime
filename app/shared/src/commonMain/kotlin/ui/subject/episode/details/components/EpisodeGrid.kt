package com.wynime.app.ui.subject.episode.details.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.preferredDisplayName
import com.wynime.app.domain.media.cache.EpisodeCacheStatus
import com.wynime.app.ui.foundation.LocalEpisodeProgressSettings
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.LongClickProgressFill
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.icons.PlayingIcon
import com.wynime.app.ui.foundation.layout.plus
import com.wynime.app.ui.subject.episode.details.EpisodeCarouselState
import com.wynime.app.ui.subject.episode.list.EpisodeCellLabel
import com.wynime.app.ui.subject.episode.list.EpisodeStillBackground
import com.wynime.app.ui.subject.episode.list.EpisodePlayProgressBar
import com.wynime.app.ui.subject.episode.list.EpisodeStillDefaults
import com.wynime.app.ui.subject.episode.list.EpisodeWatchedBadge
import com.wynime.app.ui.subject.episode.details.PreviewEpisodeCollections
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.utils.platform.annotations.TestOnly

@Composable
fun EpisodeGrid(
    episodeCarouselState: EpisodeCarouselState,
    onEpisodeClick: (EpisodeCollectionInfo) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    showImages: Boolean = LocalEpisodeProgressSettings.current.showEpisodeImages,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(isVisible) {
        if (isVisible) {
            val playingIndex = episodeCarouselState.episodes.indexOfFirst {
                episodeCarouselState.isPlaying(it)
            }
            if (playingIndex >= 0) {
                gridState.animateScrollToItem(playingIndex)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 144.dp),
        state = gridState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(all = 16.dp).plus(WindowInsets.navigationBars.asPaddingValues()),
        userScrollEnabled = true,
        modifier = modifier.heightIn(max = 500.dp)
    ) {
        items(
            items = episodeCarouselState.episodes,
            key = { it.episodeId }
        ) { episode ->
            EpisodeGridItem(
                episode = episode,
                isPlaying = episodeCarouselState.isPlaying(episode),
                onClick = { onEpisodeClick(episode) },
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

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewEpisodeGrid() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    Surface {
        EpisodeGrid(
            episodeCarouselState = remember {
                EpisodeCarouselState(
                    episodes = mutableStateOf(PreviewEpisodeCollections),
                    playingEpisode = mutableStateOf(PreviewEpisodeCollections[2]),
                    cacheStatus = { EpisodeCacheStatus.NotCached },
                    onSelect = {},
                    onChangeCollectionType = { _, _ -> },
                    backgroundScope = scope,
                )
            },
            onEpisodeClick = {},
        )
    }
}

@Composable
private fun EpisodeGridItem(
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
                MaterialTheme.colorScheme.primaryContainer
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        border = if (still != null && isPlaying) BorderStroke(stillBorder, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
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

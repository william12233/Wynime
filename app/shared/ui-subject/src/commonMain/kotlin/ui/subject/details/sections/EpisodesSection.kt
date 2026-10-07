package com.wynime.app.ui.subject.details.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GraphicEq
import com.wynime.app.ui.foundation.LocalEpisodeProgressSettings
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.LongClickProgressFill
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_details_next_page
import com.wynime.app.ui.lang.subject_details_prev_page
import com.wynime.app.ui.lang.subject_episode_mark_watched
import com.wynime.app.ui.lang.subject_episode_unwatch
import com.wynime.app.ui.subject.details.components.EpisodePaging
import com.wynime.app.ui.subject.episode.list.EpisodeCellLabel
import com.wynime.app.ui.subject.episode.list.EpisodeListItem
import com.wynime.app.ui.subject.episode.list.EpisodeStillBackground
import com.wynime.app.ui.subject.episode.list.EpisodePlayProgressBar
import com.wynime.app.ui.subject.episode.list.EpisodeStillDefaults
import com.wynime.app.ui.subject.episode.list.EpisodeWatchedBadge
import com.wynime.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

@Composable
fun EpisodeGridCell(
    item: EpisodeListItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    showImage: Boolean = true,
) {
    val isWatched = item.isDoneOrDropped
    val isDone = item.collectionType == UnifiedCollectionType.DONE
    val playProgress = item.playProgress
    val interactionSource = remember { MutableInteractionSource() }
    val still = item.imageMedium?.takeIf { showImage }

    val stillBorder = if (still != null && isPlaying) 2.dp else 0.dp
    val containerColor = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isWatched -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val sortColor = when {
        still != null -> EpisodeStillDefaults.contentColor
        isPlaying -> MaterialTheme.colorScheme.primary
        isWatched -> dimmed
        else -> LocalContentColor.current
    }
    val nameColor = when {
        still != null -> EpisodeStillDefaults.secondaryContentColor
        isWatched -> dimmed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val longClickLabel = stringResource(
        if (isWatched) Lang.subject_episode_unwatch else Lang.subject_episode_mark_watched,
    )

    Surface(
        modifier = modifier
            .height(height)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = if (still != null && isPlaying) BorderStroke(stillBorder, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box {
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
                sort = item.sort.toString(),
                name = item.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                sortColor = sortColor,
                nameColor = nameColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                playingIndicator = if (isPlaying) {
                    {
                        Icon(
                            rememberVectorPainter(Icons.Rounded.GraphicEq),
                            contentDescription = null,
                            Modifier.size(16.dp),
                            tint = sortColor,
                        )
                    }
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
fun PagedEpisodesGrid(
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    onEpisodeClick: (EpisodeListItem) -> Unit,
    onEpisodeLongClick: (EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    cellMinWidth: Dp = 128.dp,
    cellSpacing: Dp = 12.dp,
    rowsPerPage: Int = 2,
    header: @Composable (pager: (@Composable () -> Unit)?) -> Unit = { it?.invoke() },
    showImages: Boolean = LocalEpisodeProgressSettings.current.showEpisodeImages,
) {
    BoxWithConstraints(modifier) {
        val columns = remember(maxWidth, cellMinWidth, cellSpacing) {
            (((maxWidth + cellSpacing) / (cellMinWidth + cellSpacing)).toInt()).coerceAtLeast(1)
        }
        val capacity = (columns * rowsPerPage).coerceAtLeast(1)
        val paging = remember(episodes.size, capacity) { EpisodePaging(episodes.size, capacity) }
        val currentIndex = remember(episodes, currentEpisodeId) {
            if (currentEpisodeId == null) -1 else episodes.indexOfFirst { it.episodeId == currentEpisodeId }
        }
        var page by remember(paging, currentIndex) { mutableStateOf(paging.initialPage(currentIndex)) }
        val range = paging.itemRange(page)

        Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
            header(
                if (paging.isPaged) {
                    {
                        EpisodePager(
                            page = page,
                            pageCount = paging.pageCount,
                            range = range.first + 1..range.last + 1,
                            total = episodes.size,
                            onPrev = { if (page > 0) page-- },
                            onNext = { if (page < paging.pageCount - 1) page++ },
                        )
                    }
                } else {
                    null
                },
            )
            val pageItems = episodes.subList(range.first.coerceIn(0, episodes.size), (range.last + 1).coerceIn(0, episodes.size))

            for (row in pageItems.chunked(columns)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                ) {
                    for (item in row) {
                        EpisodeGridCell(
                            item,
                            isPlaying = item.episodeId == currentEpisodeId,
                            onClick = { onEpisodeClick(item) },
                            onLongClick = { onEpisodeLongClick(item) },
                            modifier = Modifier.weight(1f),
                            showImage = showImages,
                        )
                    }

                    repeat(columns - row.size) {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodePager(
    page: Int,
    pageCount: Int,
    range: IntRange,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onPrev, enabled = page > 0) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(Lang.subject_details_prev_page),
            )
        }
        Text(
            "${range.first} – ${range.last} / $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onNext, enabled = page < pageCount - 1) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(Lang.subject_details_next_page),
            )
        }
    }
}

@Composable
fun EpisodesRow(
    episodes: List<EpisodeListItem>,
    currentEpisodeId: Int?,
    onEpisodeClick: (EpisodeListItem) -> Unit,
    onEpisodeLongClick: (EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    cellWidth: Dp = 128.dp,
    cellHeight: Dp = 72.dp,
    cellSpacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    showImages: Boolean = LocalEpisodeProgressSettings.current.showEpisodeImages,
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(episodes, currentEpisodeId) {
        if (currentEpisodeId == null) -1 else episodes.indexOfFirst { it.episodeId == currentEpisodeId }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }
    LazyRow(
        modifier,
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        contentPadding = contentPadding,
    ) {
        items(episodes, key = { it.episodeId }) { item ->
            EpisodeGridCell(
                item,
                isPlaying = item.episodeId == currentEpisodeId,
                onClick = { onEpisodeClick(item) },
                onLongClick = { onEpisodeLongClick(item) },
                modifier = Modifier.width(cellWidth),
                height = cellHeight,
                showImage = showImages,
            )
        }
    }
}

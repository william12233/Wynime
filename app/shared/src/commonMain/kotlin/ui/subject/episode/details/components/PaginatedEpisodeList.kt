package com.wynime.app.ui.subject.episode.details.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.preferredDisplayName
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.LongClickProgressFill
import com.wynime.app.ui.foundation.icons.PlayingIcon
import com.wynime.app.ui.foundation.lists.PaginatedGroup
import com.wynime.app.ui.foundation.lists.PaginatedList
import com.wynime.app.ui.foundation.lists.rememberPaginatedListState
import com.wynime.app.ui.subject.episode.details.EpisodeCarouselState
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped

@Composable
fun PaginatedEpisodeList(
    groups: List<PaginatedGroup<EpisodeCollectionInfo>>,
    episodeCarouselState: EpisodeCarouselState,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {

    val allEpisodes = rememberSaveable(groups) {
        groups.flatMap { it.items }
    }

    val playingEpisodeIndex by remember(allEpisodes) {
        derivedStateOf { allEpisodes.indexOfFirst { episodeCarouselState.isPlaying(it) } }
    }

    val state = rememberPaginatedListState(groups, allEpisodes, listState)

    LaunchedEffect(playingEpisodeIndex) {
        if (playingEpisodeIndex >= 0) {
            state.bringIntoView(playingEpisodeIndex)
        }
    }

    PaginatedList(
        state = state,
        modifier = modifier,
        onItemClick = { episode ->
            episodeCarouselState.onSelect(episode)
        },
        headerContent = { group ->
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        },
        itemContent = { episode ->
            EpisodeDetailsListItem(
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
            )
        },
    )
}

@Composable
private fun EpisodeDetailsListItem(
    episode: EpisodeCollectionInfo,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWatched = episode.collectionType.isDoneOrDropped()
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val containerColor = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
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
                    "${episode.episodeInfo.sort}  " +
                        episode.episodeInfo.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else if (isWatched) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        LocalContentColor.current
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

package com.wynime.app.ui.subject.episode.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.domain.media.cache.EpisodeCacheStatus
import com.wynime.app.domain.media.cache.isCachedOrCaching
import com.wynime.app.tools.MonoTasker
import com.wynime.app.tools.toPercentageOrZero
import com.wynime.app.tools.toProgress
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.icons.PlayingIcon
import com.wynime.app.ui.foundation.lists.PaginatedGroup
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.episode_comments
import com.wynime.app.ui.lang.subject_episode_cached
import com.wynime.app.ui.lang.subject_episode_default_title
import com.wynime.app.ui.subject.episode.details.components.EpisodeWatchStatusButton
import com.wynime.app.ui.subject.episode.details.components.PlayingEpisodeItem
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.format1f
import org.jetbrains.compose.resources.stringResource

@Stable
class EpisodeCarouselState(
    episodes: State<List<EpisodeCollectionInfo>>,
    playingEpisode: State<EpisodeCollectionInfo?>,
    private val cacheStatus: (EpisodeCollectionInfo) -> EpisodeCacheStatus,
    val onSelect: (EpisodeCollectionInfo) -> Unit,
    val onChangeCollectionType: suspend (episode: EpisodeCollectionInfo, UnifiedCollectionType) -> Unit,
    internal val gridState: LazyGridState = LazyGridState(),
    backgroundScope: CoroutineScope,
    groupsState: State<List<PaginatedGroup<EpisodeCollectionInfo>>>? = null,

    playProgress: (EpisodeCollectionInfo) -> Float? = { null },
) {
    val episodes by episodes
    val playingEpisode by playingEpisode
    val groups by (groupsState ?: mutableStateOf(emptyList()))
    private val playProgressOf: (EpisodeCollectionInfo) -> Float? = playProgress

    val size get() = episodes.size

    suspend fun animateScrollToItem(index: Int) {
        gridState.animateScrollToItem(
            index,
            scrollOffset = -calculateItemSize(),
        )
    }

    private fun calculateItemSize(): Int {
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0
        return info.size.height.times(0.2f).toInt()
    }

    @Stable
    internal fun getEpisode(index: Int) = episodes.getOrNull(index)

    @Stable
    internal fun isPlaying(episode: EpisodeCollectionInfo): Boolean {
        return playingEpisode == episode
    }

    @Stable
    internal fun cacheStatus(episode: EpisodeCollectionInfo): EpisodeCacheStatus {
        return this.cacheStatus.invoke(episode)
    }

    @Stable
    internal fun playProgress(episode: EpisodeCollectionInfo): Float? = playProgressOf(episode)

    private val setCollectionTypeTasker = MonoTasker(backgroundScope)
    val isSettingCollectionType get() = setCollectionTypeTasker.isRunning
    fun setCollectionType(episode: EpisodeCollectionInfo, type: UnifiedCollectionType) {
        setCollectionTypeTasker.launch {
            onChangeCollectionType(episode, type)
        }
    }
}

@Composable
fun EpisodeCarousel(
    state: EpisodeCarouselState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val commentsText = stringResource(Lang.episode_comments)
    LaunchedEffect(state.playingEpisode) {
        val index = state.episodes.indexOf(state.playingEpisode)
        if (index == -1) return@LaunchedEffect
        state.animateScrollToItem(
            state.episodes.indexOf(state.playingEpisode),
        )
    }

    LazyVerticalGrid(
        GridCells.Adaptive(240.dp),
        modifier,
        state = state.gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.size) { index ->
            val collection = state.getEpisode(index)
            if (collection != null) {
                val collectionUpdated by rememberUpdatedState(collection)
                val play = { state.onSelect(collectionUpdated) }
                OutlinedCard(
                    onClick = play,
                ) {
                    val isPlaying = state.isPlaying(collection)
                    PlayingEpisodeItem(
                        episodeSort = {
                            Text(
                                collection.episodeInfo.sort.toString(),
                                color = if (isPlaying) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        },
                        title = {
                            Text(
                                collection.episodeInfo.nameCn.ifEmpty {
                                    stringResource(
                                        Lang.subject_episode_default_title,
                                        collection.episodeInfo.sort.toString(),
                                    )
                                },
                                color = if (isPlaying) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        },
                        watchStatus = {
                            EpisodeWatchStatusButton(
                                collection.collectionType.isDoneOrDropped(),
                                onUnmark = {
                                    state.setCollectionType(collection, UnifiedCollectionType.NOT_COLLECTED)
                                },
                                onMarkAsDone = {
                                    state.setCollectionType(collection, UnifiedCollectionType.DONE)
                                },
                                enabled = !state.isSettingCollectionType.collectAsStateWithLifecycle().value,
                            )
                        },
                        mediaSelected = true,
                        mediaLabels = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = commentsText)
                                Text(collection.episodeInfo.comment.toString(), softWrap = false)
                            }

                            EpisodeCacheStatusLabel(state, collectionUpdated)
                        },
                        filename = {},
                        videoLoadingSummary = {},
                        mediaSource = {},
                        playingIcon = {
                            if (isPlaying) {
                                PlayingIcon()
                            }
                        },
                    )
                }
            }
        }
    }

}

@Composable
private fun EpisodeCacheStatusLabel(
    state: EpisodeCarouselState,
    episode: EpisodeCollectionInfo,
) {
    val cachedText = stringResource(Lang.subject_episode_cached)
    val cacheStatusState by remember(state, episode) {
        derivedStateOf { state.cacheStatus(episode) }
    }
    val isCachedOrCaching by remember {
        derivedStateOf { cacheStatusState.isCachedOrCaching() }
    }

    if (isCachedOrCaching) {
        val cacheProgress by remember {
            derivedStateOf {
                val s = cacheStatusState
                if (s is EpisodeCacheStatus.Caching) {
                    s.progress
                } else {
                    0f.toProgress()
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (cacheStatusState) {
                is EpisodeCacheStatus.Cached -> {
                    Icon(Icons.Rounded.DownloadDone, contentDescription = null)
                    Text(cachedText, softWrap = false)
                }

                is EpisodeCacheStatus.Caching -> {
                    Icon(Icons.Rounded.Downloading, contentDescription = null)
                    val text by remember {
                        derivedStateOf {
                            String.format1f(cacheProgress.toPercentageOrZero()) + "%"
                        }
                    }
                    Text(text, softWrap = false)
                }

                EpisodeCacheStatus.NotCached -> {}
            }
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
fun PreviewEpisodeCarouselOnSurface() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    Surface {
        EpisodeCarousel(
            state = remember {
                EpisodeCarouselState(
                    episodes = mutableStateOf(PreviewEpisodeCollections),
                    playingEpisode = mutableStateOf(PreviewEpisodeCollections[2]),
                    cacheStatus = {
                        when ((it.episodeInfo.sort.number ?: 0).toInt().rem(3)) {
                            0 -> EpisodeCacheStatus.Cached(123.megaBytes)
                            1 -> EpisodeCacheStatus.Caching(0.3f.toProgress(), 123.megaBytes)
                            else -> EpisodeCacheStatus.NotCached
                        }
                    },
                    onSelect = {},
                    onChangeCollectionType = { _, _ -> },
                    backgroundScope = scope,
                )
            },
        )
    }
}

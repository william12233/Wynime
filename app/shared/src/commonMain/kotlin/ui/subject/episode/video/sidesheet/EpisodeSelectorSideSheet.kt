package com.wynime.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import com.wynime.app.ui.download.subject.contentColorForWatchStatus
import com.wynime.app.ui.foundation.BackgroundScope
import com.wynime.app.ui.foundation.HasBackgroundScope
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.icons.PlayingIcon
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_episode_cached
import com.wynime.app.ui.lang.subject_episode_close
import com.wynime.app.ui.lang.subject_episode_default_title
import com.wynime.app.ui.lang.subject_episode_mark_watched
import com.wynime.app.ui.lang.subject_episode_unwatch
import com.wynime.app.ui.lang.subject_episode_now_playing
import com.wynime.app.ui.lang.subject_episode_no_episodes
import com.wynime.app.ui.lang.video_player_select_episode
import com.wynime.app.ui.subject.episode.EpisodeCatalogStatus
import com.wynime.app.ui.subject.episode.EpisodeCatalogStatusRow
import com.wynime.app.ui.subject.episode.EpisodePresentation
import com.wynime.app.ui.subject.episode.TAG_EPISODE_SELECTOR_SHEET
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import com.wynime.app.ui.subject.episode.video.settings.SideSheetLayout
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private typealias Item = EpisodePresentation

@Stable
class EpisodeSelectorState(
    itemsFlow: Flow<List<Item>>,
    currentEpisodeId: Flow<Int>,
    private val onSelect: (Item) -> Unit,
    parentCoroutineContext: CoroutineContext,
    catalogStatus: State<EpisodeCatalogStatus> = stateOf(EpisodeCatalogStatus()),
    val onRetry: () -> Unit = {},
) : HasBackgroundScope by BackgroundScope(parentCoroutineContext) {

    val items: List<Item> by itemsFlow.produceState(emptyList())
    val catalogStatus by catalogStatus

    private val currentEpisodeId by currentEpisodeId.produceState(-1)

    val currentIndex by derivedStateOf {
        if (this.currentEpisodeId == -1) {
            -1
        } else {
            items.indexOfFirst { it.episodeId == this.currentEpisodeId }
        }
    }

    val current: Item? by derivedStateOf {
        items.find { it.episodeId == this.currentEpisodeId }
    }

    val hasNextEpisode by derivedStateOf { nextEpisodeAfter(this.currentEpisodeId) != null }

    fun select(item: Item) {
        onSelect(item)
    }

    fun selectEpisodeId(episodeId: Int): Boolean {
        val item = items.find { it.episodeId == episodeId }
        if (item != null) {
            onSelect(item)
            return true
        }
        return false
    }

    fun selectNext() {
        nextEpisodeAfter(currentEpisodeId)?.let(onSelect)
    }

    fun nextEpisodeAfter(episodeId: Int): Item? {
        val index = items.indexOfFirst { it.episodeId == episodeId }
        if (index < 0) return null
        return items.getOrNull(index + 1)?.takeIf { it.isKnownBroadcast }
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.EpisodeSelectorSheet(
    state: EpisodeSelectorState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatched: ((Int) -> Unit)? = null,
) {
    val selectEpisodeText = stringResource(Lang.video_player_select_episode)
    val closeText = stringResource(Lang.subject_episode_close)

    SideSheetLayout(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(TAG_EPISODE_SELECTOR_SHEET),
        title = { Text(text = selectEpisodeText) },
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = closeText)
            }
        },
    ) {
        val lazyListState = rememberLazyListState()

        LaunchedEffect(true) {
            val currentIndex = snapshotFlow { state.currentIndex }
                .filter { it != -1 }
                .first()
            if (currentIndex != -1) {
                lazyListState.scrollToItem(
                    currentIndex,

                    scrollOffset = -(lazyListState.layoutInfo.visibleItemsInfo.getOrNull(0)?.size?.div(2) ?: 0),
                )
            }
        }
        val status = state.catalogStatus
        EpisodeCatalogStatusRow(status, state.onRetry)
        LazyColumn(state = lazyListState) {
            if (!status.loading && !status.hasError && state.items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(Lang.subject_episode_no_episodes))
                    }
                }
            }
            itemsIndexed(state.items, key = { _, item -> item.episodeId }) { index, item ->
                EpisodeSelectorRow(
                    item = item,
                    selected = index == state.currentIndex,
                    onClick = {
                        state.select(item)
                        onDismissRequest()
                    },
                    onToggleWatched = onToggleWatched?.let { action -> { action(item.episodeId) } },
                )
                if (index != state.items.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
internal fun EpisodeSelectorRow(
    item: EpisodePresentation,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleWatched: (() -> Unit)?,
) {
    val watched = item.collectionType.isDoneOrDropped()
    val color = contentColorForWatchStatus(item.collectionType, item.isKnownBroadcast)
    ListItem(
        headlineContent = {
            Text(item.title.ifBlank { stringResource(Lang.subject_episode_default_title, item.ep) }, color = color)
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onToggleWatched,
            onLongClickLabel = stringResource(if (watched) Lang.subject_episode_unwatch else Lang.subject_episode_mark_watched),
        ),
        leadingContent = {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                Text(item.sort, fontFamily = FontFamily.Monospace, color = color)
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.isDownloaded) {
                    Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = stringResource(Lang.subject_episode_cached),
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (selected) {
                    PlayingIcon(contentDescription = stringResource(Lang.subject_episode_now_playing))
                }
            }
        },
        colors = if (selected) {
            ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.primary,
                leadingIconColor = MaterialTheme.colorScheme.primary,
                trailingIconColor = MaterialTheme.colorScheme.primary,
            )
        } else {
            ListItemDefaults.colors()
        },
    )
}

@Composable
@TestOnly
fun rememberTestEpisodeSelectorState() = remember {
    EpisodeSelectorState(
        MutableStateFlow(
            listOf(
                EpisodePresentation(
                    episodeId = -1,
                    title = "placeholder",
                    ep = "placeholder",
                    sort = "01",
                    collectionType = UnifiedCollectionType.WISH,
                    isKnownBroadcast = true,
                    isPlaceholder = true,
                ),
                EpisodePresentation(
                    episodeId = 1,
                    title = "placeholder",
                    ep = "placeholder",
                    sort = "02",
                    collectionType = UnifiedCollectionType.WISH,
                    isKnownBroadcast = true,
                    isPlaceholder = true,
                ),
                EpisodePresentation(
                    episodeId = 2,
                    title = "placeholder",
                    ep = "placeholder",
                    sort = "03",
                    collectionType = UnifiedCollectionType.WISH,
                    isKnownBroadcast = false,
                    isPlaceholder = true,
                ),
            ),
        ),
        MutableStateFlow(1),
        {},
        EmptyCoroutineContext,
    )
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
fun PreviewEpisodeSelectorSideSheet() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSideSheets.EpisodeSelectorSheet(
            state = rememberTestEpisodeSelectorState(),
            onDismissRequest = {},
        )
    }
}

@Preview
@Composable
fun PreviewPlayingIcon() {
    ProvideCompositionLocalsForPreview {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.border(1.dp, color = Color.Magenta)) {
                PlayingIcon(contentDescription = stringResource(Lang.subject_episode_now_playing))
            }
        }
    }
}

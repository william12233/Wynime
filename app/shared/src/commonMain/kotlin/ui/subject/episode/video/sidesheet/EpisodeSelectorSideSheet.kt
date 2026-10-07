package com.wynime.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_episode_close
import com.wynime.app.ui.lang.subject_episode_now_playing
import com.wynime.app.ui.lang.video_player_select_episode
import com.wynime.app.ui.subject.episode.EpisodePresentation
import com.wynime.app.ui.subject.episode.TAG_EPISODE_SELECTOR_SHEET
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import com.wynime.app.ui.subject.episode.video.settings.SideSheetLayout
import com.wynime.datasources.api.topic.UnifiedCollectionType
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
) : HasBackgroundScope by BackgroundScope(parentCoroutineContext) {

    val items: List<Item> by itemsFlow.produceState(emptyList())

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

    val hasNextEpisode by derivedStateOf {
        val currentIndex = currentIndex
        currentIndex != -1 && currentIndex < items.lastIndex
                && items[currentIndex + 1].isKnownBroadcast
    }

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
        val currentIndex = currentIndex
        if (currentIndex != -1 && currentIndex < items.lastIndex) {
            onSelect(items[currentIndex + 1])
        }
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.EpisodeSelectorSheet(
    state: EpisodeSelectorState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectEpisodeText = stringResource(Lang.video_player_select_episode)
    val closeText = stringResource(Lang.subject_episode_close)
    val nowPlayingText = stringResource(Lang.subject_episode_now_playing)

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
        LazyColumn(state = lazyListState) {
            itemsIndexed(state.items, key = { _, item -> item.episodeId }) { index, item ->
                val selected = index == state.currentIndex
                val color = contentColorForWatchStatus(item.collectionType, item.isKnownBroadcast)
                ListItem(
                    headlineContent = { Text(item.title, color = color) },
                    Modifier.clickable {
                        state.select(item)
                        onDismissRequest()
                    },
                    leadingContent = {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            Text(item.sort, fontFamily = FontFamily.Monospace, color = color)
                        }
                    },
                    trailingContent = {
                        if (selected) {
                            PlayingIcon(contentDescription = nowPlayingText)
                        }
                    },
                    colors =
                        if (selected) ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.primary,
                            leadingIconColor = MaterialTheme.colorScheme.primary,
                            trailingIconColor = MaterialTheme.colorScheme.primary,
                        )
                        else ListItemDefaults.colors(),
                )

                if (index != state.items.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
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
        val nowPlayingText = stringResource(Lang.subject_episode_now_playing)
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.border(1.dp, color = Color.Magenta)) {
                PlayingIcon(contentDescription = nowPlayingText)
            }
        }
    }
}

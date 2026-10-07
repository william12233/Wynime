package com.wynime.app.ui.download.subject

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.download.components.DownloadItem
import com.wynime.app.ui.download.components.DownloadRow
import com.wynime.app.ui.download.components.DownloadSelectionState
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.theme.stronglyWeaken
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_filter_collection_done
import com.wynime.app.ui.lang.cache_filter_collection_dropped
import com.wynime.app.ui.lang.cache_management_episode_label
import com.wynime.app.ui.lang.cache_subject_cache
import com.wynime.app.ui.lang.cache_subject_cancel
import com.wynime.app.ui.lang.downloads_empty
import com.wynime.app.ui.lang.downloads_load_failed
import com.wynime.app.ui.lang.settings_mediasource_retry
import com.wynime.app.ui.mediafetch.MediaSourceInfoProvider
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import org.jetbrains.compose.resources.stringResource

class SubjectDownloadActions(
    val download: (Int) -> Unit,
    val cancelRequest: () -> Unit,
    val pause: (Set<String>) -> Unit,
    val resume: (Set<String>) -> Unit,
    val delete: (Set<String>) -> Unit,
    val pauseAll: () -> Unit,
    val resumeAll: () -> Unit,
    val reload: () -> Unit,
) {
    companion object {

        val None = SubjectDownloadActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Composable
fun SubjectDownloadsContent(
    state: SubjectDownloadsUiState,
    selection: DownloadSelectionState,
    actions: SubjectDownloadActions,
    sourceInfoProvider: MediaSourceInfoProvider,
    onPlay: (DownloadItem) -> Unit,
    onViewDetail: ((DownloadItem) -> Unit)?,
    modifier: Modifier = Modifier,
    rowShape: Shape = RectangleShape,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: @Composable () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(480.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = contentPadding,
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        if (state.episodesFailed || state.downloadsFailed) {
            item(key = "load_error", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Lang.downloads_load_failed), Modifier.weight(1f))
                    TextButton(onClick = actions.reload) { Text(stringResource(Lang.settings_mediasource_retry)) }
                }
            }
        }
        if (state.episodesLoading || state.downloadsLoading) {
            item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp).testTag(SubjectDownloadsTestTags.LOADING))
                }
            }
        } else if (state.items.isEmpty() && !state.episodesFailed && !state.downloadsFailed) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(Lang.downloads_empty), Modifier.padding(16.dp))
            }
        }
        items(state.items, key = { it.key }, contentType = {
            when (it) {
                is SubjectDownloadListItem.Episode -> "episode"
                is SubjectDownloadListItem.Download -> "download"
            }
        }) { item ->
            when (item) {
                is SubjectDownloadListItem.Episode -> EpisodeDownloadRow(
                    episode = item.episode,

                    enabled = !selection.inSelection && !(state.request.busy && item.episode.episodeId !in state.request.episodeIds),
                    busy = state.request.busy && item.episode.episodeId in state.request.episodeIds,
                    canCancel = state.request.canCancel,
                    onDownload = { actions.download(item.episode.episodeId) },
                    onCancel = actions.cancelRequest,
                )
                is SubjectDownloadListItem.Download -> {
                    val download = item.download
                    DownloadRow(
                        episode = download,
                        mediaSourceInfoProvider = sourceInfoProvider,
                        selectionMode = selection.inSelection,
                        selected = download.id in selection.selectedIds,
                        onToggleSelected = { selection.toggleSelection(download.id) },
                        onEnterSelection = { selection.enterSelectionWith(selection.selectedIds + download.id) },
                        onPlay = { onPlay(download) },
                        onResume = { actions.resume(setOf(download.id)) },
                        onPause = { actions.pause(setOf(download.id)) },
                        onDelete = { actions.delete(setOf(download.id)) },
                        onViewDetail = onViewDetail?.let { { it(download) } },
                        shape = rowShape,
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeDownloadRow(
    episode: EpisodeDownloadItem,
    enabled: Boolean,
    busy: Boolean,
    canCancel: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
        val title = if (useOriginalTitle) episode.originalTitle else episode.title
        Text(
            text = stringResource(Lang.cache_management_episode_label, episode.sort, title),
            modifier = Modifier.weight(1f),
            color = contentColorForWatchStatus(episode.watchStatus, episode.hasPublished),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (episode.watchStatus) {
            UnifiedCollectionType.DONE -> WatchStatusLabel(stringResource(Lang.cache_filter_collection_done))
            UnifiedCollectionType.DROPPED -> WatchStatusLabel(stringResource(Lang.cache_filter_collection_dropped))
            else -> Unit
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (canCancel) IconButton(onClick = onCancel, enabled = enabled) { Icon(Icons.Rounded.Close, stringResource(Lang.cache_subject_cancel)) }
        } else {
            IconButton(onClick = onDownload, enabled = enabled) {
                Icon(Icons.Rounded.Download, stringResource(Lang.cache_subject_cache), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun contentColorForWatchStatus(collectionType: UnifiedCollectionType, isKnownBroadcast: Boolean) =
    if (collectionType.isDoneOrDropped() || !isKnownBroadcast) LocalContentColor.current.stronglyWeaken()
    else LocalContentColor.current

@Composable
private fun WatchStatusLabel(text: String) {
    Box(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

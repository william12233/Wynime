package com.wynime.app.ui.download.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.tools.getOrZero
import com.wynime.app.ui.download.DownloadActionDropdown
import com.wynime.app.ui.download.DeleteActionDialog
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_episode_download_failed
import com.wynime.app.ui.lang.cache_episode_pause_download
import com.wynime.app.ui.lang.cache_episode_resume_download
import com.wynime.app.ui.lang.cache_episode_status_paused
import com.wynime.app.ui.lang.cache_episode_watched_progress
import com.wynime.app.ui.lang.cache_filter_status_finished
import com.wynime.app.ui.lang.cache_management_episode_label
import com.wynime.app.ui.lang.cache_management_invalid_cache_info
import com.wynime.app.ui.lang.cache_management_more_actions
import com.wynime.app.ui.lang.cache_management_play
import com.wynime.app.ui.lang.cache_management_streaming_not_supported
import com.wynime.app.ui.mediafetch.MediaSourceInfoProvider
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadRow(
    episode: DownloadItem,
    mediaSourceInfoProvider: MediaSourceInfoProvider?,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onViewDetail: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showSubjectTitle: Boolean = false,

    shape: Shape = MaterialTheme.shapes.medium,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showConfirmDelete by rememberSaveable { mutableStateOf(false) }

    if (showConfirmDelete) {
        DeleteActionDialog(
            onDismiss = { showConfirmDelete = false },
            confirmEnabled = !episode.isBusy,
            onConfirm = {
                onDelete()
                showConfirmDelete = false
            },
        )
    }

    val containerColor by animateColorAsState(
        if (selectionMode && selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
    )
    Surface(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelected()
                    } else {
                        showMenu = true
                    }
                },
                onLongClick = onEnterSelection,
            ),
        shape = shape,
        color = containerColor,
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() },
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val title = if (showSubjectTitle) {
                        episode.subjectName
                    } else {
                        stringResource(Lang.cache_management_episode_label, episode.sort, episode.displayName)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        cacheEpisodeMetaText(episode, mediaSourceInfoProvider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!selectionMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DownloadPrimaryAction(episode, onPlay = onPlay, onResume = onResume, onPause = onPause)

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, stringResource(Lang.cache_management_more_actions))
                            }
                            DownloadActionDropdown(
                                show = showMenu,
                                onDismiss = { showMenu = false },
                                episode = episode,
                                onPlay = {
                                    onPlay()
                                    showMenu = false
                                },
                                onResume = {
                                    onResume()
                                    showMenu = false
                                },
                                onPause = {
                                    onPause()
                                    showMenu = false
                                },
                                onViewDetail = onViewDetail?.let {
                                    {
                                        it()
                                        showMenu = false
                                    }
                                },
                                onDelete = { showConfirmDelete = true },
                            )
                        }
                    }
                }
            }

            WynimeAnimatedVisibility(!episode.isFinished) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progress by animateFloatAsState(episode.progress.getOrZero())
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f),
                        strokeCap = StrokeCap.Round,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val statusText = when {
                            episode.isFailed -> stringResource(Lang.cache_episode_download_failed)
                            episode.isPaused -> stringResource(Lang.cache_episode_status_paused)
                            else -> episode.speedText
                        }
                        statusText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (episode.isFailed) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        }
                        episode.progressText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadPrimaryAction(
    episode: DownloadItem,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
) {
    val toaster = LocalToaster.current
    val invalidCacheInfoText = stringResource(Lang.cache_management_invalid_cache_info)
    val streamingNotSupportedText = stringResource(Lang.cache_management_streaming_not_supported)
    val primaryIconColors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    when {
        episode.isFinished -> {
            IconButton(
                onClick = {
                    when (episode.playability) {
                        DownloadItem.Playability.PLAYABLE -> onPlay()
                        DownloadItem.Playability.INVALID_SUBJECT_EPISODE_ID ->
                            toaster.toast(invalidCacheInfoText)

                        DownloadItem.Playability.STREAMING_NOT_SUPPORTED ->
                            toaster.toast(streamingNotSupportedText)
                    }
                },
                colors = primaryIconColors,
            ) {
                Icon(Icons.Rounded.PlayArrow, stringResource(Lang.cache_management_play))
            }
        }

        episode.isPaused -> {
            IconButton(onClick = onResume, enabled = !episode.isBusy, colors = primaryIconColors) {
                Icon(Icons.Rounded.PlayArrow, stringResource(Lang.cache_episode_resume_download))
            }
        }

        !episode.isFailed -> {
            IconButton(onClick = onPause, enabled = !episode.isBusy, colors = primaryIconColors) {
                Icon(Icons.Rounded.Pause, stringResource(Lang.cache_episode_pause_download))
            }
        }
    }
}

@Composable
private fun cacheEpisodeMetaText(
    episode: DownloadItem,
    mediaSourceInfoProvider: MediaSourceInfoProvider?,
): String {
    val sourceName = episode.mediaSourceId?.let { id ->
        mediaSourceInfoProvider?.rememberMediaSourceInfo(id)?.value?.displayName
    }
    val statusText = when {
        episode.isFinished -> stringResource(Lang.cache_filter_status_finished)
        else -> null
    }
    val watchedText = if (episode.isFinished) {
        episode.playbackProgressText?.let { stringResource(Lang.cache_episode_watched_progress, it) }
    } else {
        null
    }
    return listOfNotNull(episode.detailedSizeText, sourceName, statusText, watchedText)
        .joinToString(" · ")
}

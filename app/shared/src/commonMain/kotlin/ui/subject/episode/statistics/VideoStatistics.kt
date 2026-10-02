/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.player.data.filenameOrNull
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_select_media_source
import me.him188.ani.app.ui.lang.subject_episode_statistics_error_message
import me.him188.ani.app.ui.lang.subject_episode_statistics_now_playing
import me.him188.ani.app.ui.lang.subject_episode_statistics_show_less
import me.him188.ani.app.ui.lang.subject_episode_statistics_show_more
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.app.ui.media.renderProperties
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceInfo
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer

class VideoStatisticsCollector(
    mediaSelector: Flow<MediaSelector>,
    videoLoadingStateFlow: Flow<VideoLoadingState>,
    playerState: MediampPlayer,
    private val mediaSourceInfoProvider: MediaSourceInfoProvider,
    mediaSourceLoading: Flow<Boolean>,
    backgroundScope: CoroutineScope,
) {
    val videoStatisticsFlow: StateFlow<VideoStatistics> = kotlin.run {
        val selectedMediaFlow = mediaSelector.flatMapLatest { it.selected }
        combine(
            selectedMediaFlow,
            selectedMediaFlow.flatMapLatest {
                mediaSourceInfoProvider.getSourceInfoFlow(it?.mediaSourceId ?: return@flatMapLatest emptyFlow())
            },
            selectedMediaFlow
                .combine(playerState.mediaData.map { it?.filenameOrNull }) { selectedMedia, filename ->
                    filename ?: selectedMedia?.originalTitle
                },
            mediaSourceLoading,
            videoLoadingStateFlow,
            ::VideoStatistics,
        ).stateIn(
            backgroundScope,
            SharingStarted.WhileSubscribed(),
            VideoStatistics.Placeholder,
        )
    }
}


/**
 * 視頻統計資訊，用於取得目前播放器正在播放的來源 [Media] 與檔名。
 */
data class VideoStatistics(
    /**
     * 从 [MediaSelector] 选择后就有
     */
    val playingMedia: Media?,
    /**
     * [playingMedia] 所属的 [MediaSourceInfo]
     */
    val playingMediaSourceInfo: MediaSourceInfo?,
    /**
     * 要播放器获取到视频文件后才有
     */
    val playingFilename: String?,
    /**
     * [MediaFetcher] 的所有数据源是否都已经加载完成.
     */
    val mediaSourceLoading: Boolean,
    val videoLoadingState: VideoLoadingState,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        val Placeholder = VideoStatistics(
            null, null, null, false, VideoLoadingState.Initial,
            isPlaceholder = true,
        )
    }
}

@Composable
fun VideoStatistics(
    state: VideoStatistics,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            when (val loadingState = state.videoLoadingState) {
                is VideoLoadingState.Succeed -> {
                    val mediaPropertiesText by remember(state.playingMedia, mediaDetailsStrings) {
                        derivedStateOf {
                            state.playingMedia?.renderProperties(mediaDetailsStrings)
                        }
                    }
                    NowPlayingLabel(mediaPropertiesText, state.playingFilename)
                }

                is VideoLoadingState.UnknownError -> {
                    ErrorTextBox(
                        remember(loadingState) { loadingState.cause.toString() },
                        {
                            scope.launch {
                                clipboard.setClipEntryText(loadingState.cause.stackTraceToString())
                            }
                        },
                        Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                }

                else -> {}
            }
        }
    }
}

/**
 * 显示正在播放的那行字
 */
@Composable
private fun NowPlayingLabel(
    playingMedia: String?,
    filename: String?,
    modifier: Modifier = Modifier,
) {
    val nowPlayingText = stringResource(Lang.subject_episode_statistics_now_playing)
    val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
    Row(modifier) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            if (playingMedia != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(
                            nowPlayingText,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            playingMedia,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    if (filename != null) {
                        SelectionContainer {
                            Text(
                                filename,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            } else {
                Text(selectMediaSourceText)
            }
        }
    }
}

@Composable
private fun ErrorTextBox(
    text: String,
    onCopy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        text,
        onValueChange = {},
        modifier,
        label = { Text(stringResource(Lang.subject_episode_statistics_error_message)) },
        shape = MaterialTheme.shapes.medium,
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, null)
            }
        },
    )
}

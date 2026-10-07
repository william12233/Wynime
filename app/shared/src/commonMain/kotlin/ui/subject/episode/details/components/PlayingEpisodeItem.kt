package com.wynime.app.ui.subject.episode.details.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.TestMediaSourceInfo
import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.ui.episode.share.MediaShareData
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.icons.PlayingIcon
import com.wynime.app.ui.foundation.layout.paddingIfNotEmpty
import com.wynime.app.ui.foundation.text.ProvideContentColor
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_unknown
import com.wynime.app.ui.lang.episode_summary_share
import com.wynime.app.ui.lang.subject_episode_cache
import com.wynime.app.ui.lang.subject_episode_select_media_source
import com.wynime.app.ui.media.rememberMediaDetailsStrings
import com.wynime.app.ui.media.renderProperties
import com.wynime.app.ui.settings.rendering.MediaSourceIcons
import com.wynime.app.ui.subject.episode.statistics.VideoLoadingSummary
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.isAndroid
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayingEpisodeItem(
    episodeSort: @Composable () -> Unit,
    title: @Composable () -> Unit,
    watchStatus: @Composable () -> Unit,
    mediaSelected: Boolean,
    mediaLabels: @Composable FlowRowScope.() -> Unit,
    filename: @Composable () -> Unit,
    videoLoadingSummary: @Composable RowScope.() -> Unit,
    mediaSource: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    playingIcon: @Composable () -> Unit = {
        if (LocalPlatform.current.isAndroid()) {
            PlayingIcon()
        }
    },
    rowSpacing: Dp = 16.dp,
    horizontalPadding: Dp = 20.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {

        Row(
            Modifier.padding(
                horizontal = horizontalPadding,
                vertical = rowSpacing - 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            FlowRow(
                Modifier.padding(vertical = 12.dp).weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                ) {
                    ProvideContentColor(MaterialTheme.colorScheme.primary) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            playingIcon()
                            episodeSort()
                        }
                        SelectionContainer {
                            title()
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.Top).padding(start = 16.dp).height(48.dp)) {
                watchStatus()
            }
        }

        if (mediaSelected) {

            ProvideTextStyleContentColor(
                MaterialTheme.typography.labelLarge,
            ) {
                FlowRow(
                    Modifier
                        .padding(top = 4.dp)
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    mediaLabels()
                }
            }

            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Row(
                    Modifier
                        .paddingIfNotEmpty(top = rowSpacing - 8.dp, bottom = 8.dp)
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    filename()
                }
            }

            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {

                Row(
                    Modifier
                        .padding(
                            start = horizontalPadding - 8.dp,
                            end = horizontalPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    videoLoadingSummary()
                }
            }

            Spacer(Modifier.height(rowSpacing - 8.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                Modifier.padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.offset(x = (-8).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    mediaSource()
                }
            }
        }
    }
}

@Composable
fun TestEpisodeWatchStatusButton() {
    var isDone by rememberSaveable { mutableStateOf(false) }
    EpisodeWatchStatusButton(
        isDone = isDone,
        onUnmark = { isDone = false },
        onMarkAsDone = { isDone = true },
    )
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewEpisodeItemImpl(
    media: DefaultMedia? = TestMediaList[0],
    episodeTitle: String = "中文剧集名称",
    filename: String? = "filename-".repeat(3) + ".mkv",
    videoLoadingState: VideoLoadingState = VideoLoadingState.Succeed,
) {
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .animateContentSize(),
    ) {
        PlayingEpisodeItem(
            episodeSort = { Text("01") },
            title = { Text(episodeTitle) },
            watchStatus = { TestEpisodeWatchStatusButton() },
            mediaSelected = media != null,
            mediaLabels = {
                media?.let {
                    Text(media.renderProperties(mediaDetailsStrings))
                }
            },
            filename = {
                filename?.let {
                    Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            },
            videoLoadingSummary = {
                VideoLoadingSummary(
                    state = videoLoadingState,
                )
            },
            mediaSource = {
                var isLoading by remember { mutableStateOf(false) }
                PlayingEpisodeItemDefaults.MediaSource(
                    media = null,
                    mediaSourceInfo = TestMediaSourceInfo,
                    isLoading = isLoading,
                    onClick = { isLoading = !isLoading },
                    modifier = Modifier.clickable {
                        isLoading = !isLoading
                    },
                )
            },
            actions = {
                PlayingEpisodeItemDefaults.ActionShare(MediaShareData.from(media, null))
                PlayingEpisodeItemDefaults.ActionCache({ })
            },
        )
    }
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItem() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl()
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItemNoFilename() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(filename = null)
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItemLongTexts() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(
        episodeTitle = "超长名称".repeat(20),
        filename = "filename-".repeat(20) + ".mkv",
    )
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeNotSelected() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(
        media = null,
        filename = null,
    )
}

@Composable
@PreviewLightDark
fun PreviewPlayingEpisodeItemFailed() = ProvideCompositionLocalsForPreview {
    PreviewEpisodeItemImpl(
        videoLoadingState = VideoLoadingState.UnsupportedMedia,
    )
}

object PlayingEpisodeItemDefaults {
    @Composable
    fun ActionCache(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val cacheText = stringResource(Lang.subject_episode_cache)
        IconButton(onClick, modifier) {
            Icon(Icons.Rounded.Download, cacheText)
        }
    }

    @Composable
    fun ActionShare(
        data: MediaShareData,
        modifier: Modifier = Modifier,
    ) {
        var showShareDropdown by rememberSaveable { mutableStateOf(false) }
        val shareText = stringResource(Lang.episode_summary_share)
        Box {
            IconButton({ showShareDropdown = true }, modifier) {
                Icon(Icons.Rounded.Outbox, shareText)
            }
            ShareEpisodeDropdown(
                data, showShareDropdown,
                { showShareDropdown = false },
            )
        }
    }

    @Composable
    fun MediaSource(
        media: Media?,
        mediaSourceInfo: MediaSourceInfo?,
        isLoading: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val unknownText = stringResource(Lang.cache_unknown)
        val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (media != null) {
                OutlinedButton(
                    onClick = onClick,
                    Modifier,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current),
                ) {
                    Icon(MediaSourceIcons.location(media.location, media.kind), null)

                    Text(
                        mediaSourceInfo?.displayName ?: unknownText,
                        Modifier.padding(start = 12.dp).align(Alignment.CenterVertically),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                Button(
                    onClick = onClick,
                    Modifier,
                ) {
                    Icon(Icons.Rounded.DisplaySettings, null)

                    Text(
                        selectMediaSourceText,
                        Modifier.padding(start = 12.dp).align(Alignment.CenterVertically),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            WynimeAnimatedVisibility(
                isLoading,
                Modifier.align(Alignment.CenterVertically),
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
            ) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
    }
}

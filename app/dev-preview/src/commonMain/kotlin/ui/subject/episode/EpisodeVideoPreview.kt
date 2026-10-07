package com.wynime.app.ui.subject.episode

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.staticMediaCacheProgressState
import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.ui.episode.share.MediaShareData
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.mediafetch.rememberTestMediaSelectorState
import com.wynime.app.ui.mediafetch.request.TestMediaFetchRequest
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import com.wynime.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import com.wynime.app.ui.subject.episode.video.components.SideSheets
import com.wynime.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import com.wynime.app.ui.subject.episode.video.sidesheet.MediaSelectorSheet
import com.wynime.app.ui.subject.episode.video.sidesheet.rememberTestEpisodeSelectorState
import com.wynime.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import com.wynime.app.videoplayer.ui.ControllerVisibility
import com.wynime.app.videoplayer.ui.MutablePlayerFullscreenState
import com.wynime.app.videoplayer.ui.NoOpVideoAspectRatio
import com.wynime.app.videoplayer.ui.VideoAspectRatioControllerState
import com.wynime.app.videoplayer.ui.gesture.NoOpLevelController
import com.wynime.app.videoplayer.ui.progress.PlayerControllerDefaults
import com.wynime.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import com.wynime.app.videoplayer.ui.rememberVideoControllerState
import com.wynime.utils.platform.annotations.TestOnly
import org.openani.mediamp.test.TestMediampPlayer

@PreviewLightDark
@Preview(name = "Landscape Fullscreen", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PreviewVideoScaffoldFullscreen() {
    PreviewVideoScaffoldImpl(expanded = true)
}

@PreviewLightDark
@Preview(name = "Portrait", heightDp = 300)
@Composable
private fun PreviewVideoScaffold() {
    PreviewVideoScaffoldImpl(expanded = false)
}

@PreviewLightDark
@Preview(name = "Detached Slider Fullscreen", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PreviewDetachedSliderFullscreen() {
    PreviewVideoScaffoldImpl(expanded = true, controllerVisibility = ControllerVisibility.DetachedSliderOnly)
}

@PreviewLightDark
@Preview(name = "Detached Slider", heightDp = 300)
@Composable
private fun PreviewDetachedSlider() {
    PreviewVideoScaffoldImpl(expanded = false, controllerVisibility = ControllerVisibility.DetachedSliderOnly)
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewVideoScaffoldImpl(
    expanded: Boolean,
    controllerVisibility: ControllerVisibility = ControllerVisibility.Visible
) = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    val playerState = remember {
        TestMediampPlayer(scope.coroutineContext)
    }

    val controllerState = rememberVideoControllerState(initialVisibility = controllerVisibility)

    val progressSliderState = rememberMediaProgressSliderState(
        playerState,
        onPreview = {

        },
        onPreviewFinished = {
            playerState.seekTo(it)
        },
    )
    val videoScaffoldConfig = VideoScaffoldConfig.Default
    val fullscreenState = remember(expanded) { MutablePlayerFullscreenState(expanded) }
    val cacheProgressInfoFlow = staticMediaCacheProgressState(ChunkState.NONE).flow
    EpisodeVideoImpl(
        playerState = playerState,
        expanded = expanded,
        hasNextEpisode = true,
        onClickNextEpisode = {},
        playerControllerState = controllerState,
        onClickSkipOpEd = { playerState.skip(VideoScaffoldConfig.Default.opEdSkipDuration.inWholeMilliseconds) },
        title = {
            EpisodePlayerTitle(
                "28",
                "因为下次再见的时候就会很难为情",
                "葬送的芙莉莲",
            )
        },
        videoLoadingStateFlow = MutableStateFlow(VideoLoadingState.Succeed),
        fullscreenState = fullscreenState,
        onClickScreenshot = {},
        detachedProgressSlider = {
            PlayerControllerDefaults.MediaProgressSlider(
                progressSliderState,
                cacheProgressInfoFlow = cacheProgressInfoFlow,
                enabled = false,
            )
        },
        sidebarVisible = true,
        onToggleSidebar = {},
        progressSliderState = progressSliderState,
        cacheProgressInfoFlow = cacheProgressInfoFlow,
        audioController = NoOpLevelController,
        brightnessController = NoOpLevelController,
        playbackSpeedControllerState = null,
        videoAspectRatioControllerState = remember {
            VideoAspectRatioControllerState(NoOpVideoAspectRatio, scope)
        },
        leftBottomTips = {
            PlayerControllerDefaults.LeftBottomTips(
                onClick = {},
                modifier = Modifier.padding(if (expanded) 16.dp else 8.dp),
            )
        },
        fullscreenSwitchButton = {
            EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                videoScaffoldConfig.fullscreenSwitchMode,
                fullscreenState,
            )
        },
        sideSheets = { sheetsController ->
            EpisodeVideoDefaults.SideSheets(
                sheetsController,
                controllerState,
                mediaSelectorPage = {
                    EpisodeVideoSideSheets.MediaSelectorSheet(
                        mediaSelectorState = rememberTestMediaSelectorState(),
                        fetchRequest = TestMediaFetchRequest,
                        onFetchRequestChange = {},
                        onDismissRequest = { goBack() },
                        onRestartSource = {},
                    )
                },
                episodeSelectorPage = {
                    EpisodeVideoSideSheets.EpisodeSelectorSheet(
                        state = rememberTestEpisodeSelectorState(),
                        onDismissRequest = { goBack() },
                    )
                },
            )
        },
        shareData = MediaShareData.from(null, null),
        onClickCache = {},
    )
}

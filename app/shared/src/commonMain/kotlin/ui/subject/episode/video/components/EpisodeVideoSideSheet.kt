package com.wynime.app.ui.subject.episode.video.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import com.wynime.app.ui.subject.episode.EpisodeVideoDefaults
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.ui.VideoSideSheetScope
import com.wynime.app.videoplayer.ui.VideoSideSheets
import com.wynime.app.videoplayer.ui.VideoSideSheetsController
import com.wynime.app.videoplayer.ui.hasPageAsState
import com.wynime.app.videoplayer.ui.rememberAlwaysOnRequester
import com.wynime.app.videoplayer.ui.rememberVideoSideSheetsController

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoDefaults.SideSheets(
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage> = rememberVideoSideSheetsController(),
    playerControllerState: PlayerControllerState,
    mediaSelectorPage: @Composable VideoSideSheetScope.() -> Unit,
    episodeSelectorPage: @Composable VideoSideSheetScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoSideSheets(
        controller = sheetsController,
        modifier,
    ) { page ->
        when (page) {
            EpisodeVideoSideSheetPage.MEDIA_SELECTOR -> mediaSelectorPage()
            EpisodeVideoSideSheetPage.EPISODE_SELECTOR -> episodeSelectorPage()
        }
    }

    val alwaysOnRequester = rememberAlwaysOnRequester(playerControllerState, "sideSheets")
    val isPage by sheetsController.hasPageAsState()
    if (isPage) {
        DisposableEffect(true) {
            alwaysOnRequester.request()
            onDispose {
                alwaysOnRequester.cancelRequest()
            }
        }
    }
}

@Serializable
enum class EpisodeVideoSideSheetPage {
    MEDIA_SELECTOR,
    EPISODE_SELECTOR,
}

object EpisodeVideoSideSheets

package com.wynime.app.ui.subject.episode.video.components

import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import com.wynime.app.data.models.preference.FullscreenSwitchMode
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.subject.episode.EpisodeVideoDefaults
import com.wynime.app.videoplayer.ui.PlayerFullscreenState
import com.wynime.app.videoplayer.ui.progress.PlayerControllerDefaults
import kotlin.time.Duration.Companion.seconds

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
    mode: FullscreenSwitchMode,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (mode) {
            FullscreenSwitchMode.ONLY_IN_CONTROLLER -> {}

            FullscreenSwitchMode.ALWAYS_SHOW_FLOATING -> {
                PlayerControllerDefaults.FullscreenIcon(fullscreenState)
            }

            FullscreenSwitchMode.AUTO_HIDE_FLOATING -> {
                var visible by remember { mutableStateOf(true) }
                LaunchedEffect(true) {
                    delay(5.seconds)
                    visible = false
                }
                WynimeAnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(snap()),
                    exit = LocalWynimeMotionScheme.current.animatedVisibility.standardExit,
                ) {
                    PlayerControllerDefaults.FullscreenIcon(fullscreenState)
                }
            }
        }
    }
}

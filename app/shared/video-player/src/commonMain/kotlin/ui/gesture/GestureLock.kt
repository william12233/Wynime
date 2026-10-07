package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.input.LocalActiveInputSource
import com.wynime.app.ui.foundation.theme.slightlyWeaken
import com.wynime.app.videoplayer.ui.ControllerVisibility
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.ui.PlayerFullscreenState
import com.wynime.app.videoplayer.ui.progress.PlayerProgressSliderState
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.time.Duration.Companion.seconds

const val TAG_GESTURE_LOCK = "GestureLock"

@Composable
fun GestureLock(
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier.testTag(TAG_GESTURE_LOCK),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background.copy(0.05f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.slightlyWeaken()),
    ) {
        IconButton(onClick) {
            val color = if (isLocked) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White
            }
            CompositionLocalProvider(LocalContentColor provides color) {
                if (isLocked) {
                    Icon(Icons.Outlined.Lock, contentDescription = "UnLock screen")
                } else {
                    Icon(Icons.Outlined.LockOpen, contentDescription = "Lock screen")
                }
            }
        }
    }

}

@Composable
fun LockedScreenGestureHost(
    controllerVisibility: () -> ControllerVisibility,
    setFullVisible: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = { setFullVisible(true) },
            ).fillMaxSize(),
    )

    if (controllerVisibility() == ControllerVisibility.Visible) {
        LaunchedEffect(true) {
            delay(2.seconds)
            setFullVisible(false)
        }
    }
    return
}

@Composable
fun LockableVideoGestureHost(
    controllerState: PlayerControllerState,
    seekerState: SwipeSeekerState,
    progressSliderState: PlayerProgressSliderState,
    playerState: MediampPlayer,
    locked: Boolean,
    enableSwipeToSeek: Boolean,
    audioController: LevelController,
    brightnessController: LevelController,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
    onTogglePauseResume: () -> Unit = {},
    onTogglePlayerStats: () -> Unit = {},
    family: GestureFamily = gestureFamilyOf(
        LocalActiveInputSource.current.current,
        LocalPlatform.current.mouseFamily,
    ),
    gestureIndicatorState: GestureIndicatorState = rememberGestureIndicatorState(),
    fastForwardSpeed: Float = 3f,
    fastSkipState: FastSkipState? = playerState.features[PlaybackSpeed]?.let {
        rememberPlayerFastSkipState(
            playerState = it,
            gestureIndicatorState,
            fastForwardSpeed = fastForwardSpeed,
        )
    },
) {
    if (locked) {
        LockedScreenGestureHost(
            { controllerState.visibility },
            controllerState.setFullVisible,
            modifier.testTag("LockedScreenGestureHost"),
        )
    } else {
        PlayerGestureHost(
            controllerState,
            seekerState,
            progressSliderState,
            gestureIndicatorState,
            fastSkipState,
            playerState,
            enableSwipeToSeek,
            audioController,
            brightnessController,
            playbackSpeedControllerState,
            fullscreenState,
            modifier,
            onTogglePauseResume = onTogglePauseResume,
            onTogglePlayerStats = onTogglePlayerStats,
            family = family,
        )
    }
}

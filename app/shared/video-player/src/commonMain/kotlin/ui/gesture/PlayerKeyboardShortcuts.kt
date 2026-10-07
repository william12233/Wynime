package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.wynime.app.ui.foundation.effects.ComposeKey
import com.wynime.app.ui.foundation.effects.onKey
import com.wynime.app.videoplayer.ui.nextPlaybackSpeed

private val PLAYBACK_SPEED_SHORTCUTS = listOf(
    ComposeKey.One to 1f,
    ComposeKey.NumPad1 to 1f,
    ComposeKey.Two to 2f,
    ComposeKey.NumPad2 to 2f,
    ComposeKey.Three to 3f,
    ComposeKey.NumPad3 to 3f,
)

internal fun Modifier.playerKeyboardShortcuts(
    seekerState: SwipeSeekerState,
    fastSkipState: FastSkipState?,
    currentPlaybackSpeed: Float?,
    playbackSpeedRange: ClosedFloatingPointRange<Float>,
    onPlaybackSpeedChanged: (Float) -> Unit,
    volumeEnabled: Boolean,
    onVolumeUp: (fineAdjustment: Boolean) -> Unit,
    onVolumeDown: (fineAdjustment: Boolean) -> Unit,
    onTogglePauseResume: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onTogglePlayerStats: () -> Unit,
): Modifier {
    var result = keyboardSeekAndFastForward(
        onSeekBackward = { seekerState.onSeek(-5) },
        onSeekForward = { seekerState.onSeek(5) },
        fastSkipState = fastSkipState,
    )
    if (volumeEnabled) {
        result = result.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) return@onKeyEvent false
            when (event.key) {
                ComposeKey.DirectionUp -> {
                    onVolumeUp(event.isShiftPressed)
                    true
                }

                ComposeKey.DirectionDown -> {
                    onVolumeDown(event.isShiftPressed)
                    true
                }

                else -> false
            }
        }
    }
    result = result
        .onKey(ComposeKey.Spacebar, onTogglePauseResume)
        .onKey(ComposeKey.F, onToggleFullscreen)
    if (currentPlaybackSpeed != null) {
        result = result
            .onKey(ComposeKey.A) {
                onPlaybackSpeedChanged(nextPlaybackSpeed(currentPlaybackSpeed, playbackSpeedRange, -1))
            }
            .onKey(ComposeKey.D) {
                onPlaybackSpeedChanged(nextPlaybackSpeed(currentPlaybackSpeed, playbackSpeedRange, 1))
            }
            .onKey(ComposeKey.S) {
                onPlaybackSpeedChanged(1f.coerceIn(playbackSpeedRange))
            }
        for ((key, speed) in PLAYBACK_SPEED_SHORTCUTS) {
            result = result.onKey(key) {
                onPlaybackSpeedChanged(speed.coerceIn(playbackSpeedRange))
            }
        }
    }
    return result
        .onKey(ComposeKey.I, onTogglePlayerStats)

        .onPreviewKeyEvent { event ->
            event.key == ComposeKey.Enter || event.key == ComposeKey.NumPadEnter
        }
}

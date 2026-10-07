package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import com.wynime.app.tools.rememberUiMonoTasker
import com.wynime.app.ui.foundation.effects.ComposeKey
import com.wynime.app.ui.foundation.effects.onKey

@Stable
class KeyboardHorizontalDirectionState(
    val onBackward: () -> Unit,
    val onForward: () -> Unit,
)

fun Modifier.onKeyboardHorizontalDirection(
    state: KeyboardHorizontalDirectionState,
): Modifier = onKeyboardHorizontalDirection(
    onBackward = state.onBackward,
    onForward = state.onForward,
)

fun Modifier.onKeyboardHorizontalDirection(
    onBackward: () -> Unit,
    onForward: () -> Unit,
): Modifier = composed(
    inspectorInfo = {
        name = "keyboardSeek"
    },
) {
    val layoutDirection = LocalLayoutDirection.current
    val backwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionLeft
    } else {
        ComposeKey.DirectionRight
    }
    val forwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionRight
    } else {
        ComposeKey.DirectionLeft
    }

    val onBackwardState by rememberUpdatedState(onBackward)
    val onForwardState by rememberUpdatedState(onForward)
    onKey(backwardKey) {
        onBackwardState()
    }.onKey(forwardKey) {
        onForwardState()
    }
}

fun Modifier.keyboardSeekAndFastForward(
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    fastSkipState: FastSkipState?,
): Modifier = composed(
    inspectorInfo = {
        name = "keyboardSeekAndFastForward"
    },
) {
    val layoutDirection = LocalLayoutDirection.current
    val backwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionLeft
    } else {
        ComposeKey.DirectionRight
    }
    val forwardKey = if (layoutDirection == LayoutDirection.Ltr) {
        ComposeKey.DirectionRight
    } else {
        ComposeKey.DirectionLeft
    }

    val onBackwardState by rememberUpdatedState(onSeekBackward)
    val onForwardState by rememberUpdatedState(onSeekForward)

    val tasker = rememberUiMonoTasker()
    var ticket by remember { mutableStateOf<Int?>(null) }

    onPreviewKeyEvent { event ->
        if (event.key == backwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                return@onPreviewKeyEvent true
            } else if (event.type == KeyEventType.KeyUp) {
                onBackwardState()
                return@onPreviewKeyEvent true
            }
        }

        if (event.key == forwardKey) {
            if (event.type == KeyEventType.KeyDown) {
                if (!tasker.isRunning.value) {
                    tasker.launch {
                        try {
                            delay(200)
                            fastSkipState?.let {
                                ticket = it.startSkipping(SkipDirection.FORWARD)
                            }
                            awaitCancellation()
                        } finally {
                            ticket?.let {
                                fastSkipState?.stopSkipping(it)
                            }
                            ticket = null
                        }
                    }
                }
                return@onPreviewKeyEvent true
            } else if (event.type == KeyEventType.KeyUp) {
                val isSkipping = ticket != null
                tasker.cancel()
                if (!isSkipping) {
                    onForwardState()
                }
                return@onPreviewKeyEvent true
            }
        }
        false
    }
}


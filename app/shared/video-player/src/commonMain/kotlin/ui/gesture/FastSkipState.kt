package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.input.asGesturePointerType
import org.openani.mediamp.features.PlaybackSpeed

@Composable
fun rememberPlayerFastSkipState(
    playerState: PlaybackSpeed,
    gestureIndicatorState: GestureIndicatorState,
    fastForwardSpeed: Float = 3f,
): FastSkipState {
    return remember(playerState, fastForwardSpeed) {
        PlayerFastSkipState(playerState, gestureIndicatorState, fastForwardSpeed).fastSkipState
    }
}

class PlayerFastSkipState(
    private val playbackSpeed: PlaybackSpeed,
    private val gestureIndicatorState: GestureIndicatorState,
    private val fastForwardSpeed: Float = 3f,
) {
    private var originalSpeed = 0f
    private var gestureIndicatorTicket = 0
    val fastSkipState: FastSkipState = FastSkipState(
        onStart = { skipDirection ->
            originalSpeed = playbackSpeed.value
            playbackSpeed.set(
                when (skipDirection) {
                    SkipDirection.FORWARD -> fastForwardSpeed
                    SkipDirection.BACKWARD -> error("Backward skipping is not supported")
                },
            )
            gestureIndicatorTicket = gestureIndicatorState.startFastForward(fastForwardSpeed)
        },
        onStop = {
            playbackSpeed.set(originalSpeed)
            gestureIndicatorState.stopFastForward(gestureIndicatorTicket)
        },
    )
}

@Stable
class FastSkipState(
    private val onStart: (skipDirection: SkipDirection) -> Unit,
    private val onStop: () -> Unit,
) {
    private var skippingDirection: SkipDirection? by mutableStateOf(null)
    private var ticket: Int = 0

    fun startSkipping(direction: SkipDirection): Int {
        skippingDirection = direction
        onStart(direction)
        return ++ticket
    }

    fun stopSkipping(ticket: Int) {
        if (ticket == this.ticket) {
            skippingDirection = null
            onStop()
        }
    }
}

enum class SkipDirection {
    FORWARD, BACKWARD
}

fun Modifier.longPressFastSkip(
    state: FastSkipState,
    direction: SkipDirection,
    requiredPointerType: PointerType? = null,
): Modifier {
    var ticket = 0
    return detectLongPressGesture(
        onStart = {
            ticket = state.startSkipping(direction)
        },
        onEnd = {
            state.stopSkipping(ticket)
        },
        requiredPointerType = requiredPointerType,
    )
}

fun Modifier.detectLongPressGesture(
    onStart: () -> Unit,
    onEnd: () -> Unit,
    longPressTimeout: Long = 500L,
    requiredPointerType: PointerType? = null,
): Modifier = pointerInput(requiredPointerType) {
    coroutineScope {
        val touchSlop = viewConfiguration.touchSlop

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            if (
                requiredPointerType != null &&
                down.type.asGesturePointerType() != requiredPointerType.asGesturePointerType()
            ) {
                return@awaitEachGesture
            }
            val initialPosition = down.position
            var isLongPressDetected = false

            val longPressJob = launch {
                delay(longPressTimeout)
                onStart()
                isLongPressDetected = true
            }

            try {
                var change = awaitPointerEvent()
                while (change.changes.any { it.pressed }) {
                    val pointer = change.changes[0]
                    if (isLongPressDetected) {

                        change.changes.forEach { it.consume() }
                    }
                    if ((pointer.position - initialPosition).getDistance() > touchSlop) {

                        longPressJob.cancel()
                    }
                    change = awaitPointerEvent()
                }

                if (isLongPressDetected) {

                    change.changes.forEach { it.consume() }
                }
            } finally {

                longPressJob.cancel()
                if (isLongPressDetected) {
                    onEnd()
                }
            }
        }
    }
}

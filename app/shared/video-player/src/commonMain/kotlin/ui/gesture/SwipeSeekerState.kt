package com.wynime.app.videoplayer.ui.gesture

import androidx.annotation.UiThread
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import com.wynime.app.ui.foundation.effects.onPointerEventMultiplatform
import kotlin.math.roundToInt

@Composable
fun rememberSwipeSeekerState(
    screenWidthPx: Int,
    swipeSeekerConfig: SwipeSeekerConfig = SwipeSeekerConfig.Default,
    @UiThread onSeek: (offsetSeconds: Int) -> Unit,
): SwipeSeekerState {
    val onSeekState by rememberUpdatedState(onSeek)
    val density = LocalDensity.current
    return remember(swipeSeekerConfig, screenWidthPx, density) {
        SwipeSeekerState(
            screenWidthPx,
            swipeSeekerConfig,
            density,
        ) { onSeekState(it) }
    }
}

@Immutable
data class SwipeSeekerConfig(

    val maxDragDelta: Float = 0f,

    val maxDragSeconds: Int = 97,

    val cancelVerticalDragDistance: Dp = 144.dp,
) {
    companion object {
        val Default = SwipeSeekerConfig()
    }
}

internal fun isVerticalDragCancelled(
    dragStartY: Float,
    position: Offset,
    cancelVerticalDragDistancePx: Float,
): Boolean {
    return position.isSpecified &&
        dragStartY - position.y > cancelVerticalDragDistancePx
}

private fun Modifier.trackSwipeSeekCancellation(
    seekerState: SwipeSeekerState,
    onCancellationChanged: (Boolean) -> Unit,
): Modifier = this
    .onPointerEventMultiplatform(
        PointerEventType.Press,
        pass = PointerEventPass.Initial,
    ) { event ->
        event.changes.firstOrNull()?.let { seekerState.onPointerDown(it.position) }
    }
    .onPointerEventMultiplatform(
        PointerEventType.Move,
        pass = PointerEventPass.Initial,
    ) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEventMultiplatform
        if (seekerState.updateCancellation(change.position)) {
            onCancellationChanged(seekerState.isCancelled)
        }
    }

@Stable
class SwipeSeekerState internal constructor(

    private val screenWidthPx: Int,
    private val swipeSeekerConfig: SwipeSeekerConfig,
    density: Density,

    @UiThread val onSeek: (offsetSeconds: Int) -> Unit,
) {
    private val cancelVerticalDragDistancePx =
        with(density) { swipeSeekerConfig.cancelVerticalDragDistance.toPx() }

    private var seekDelta: Float by mutableFloatStateOf(Float.NaN)

    var isCancelled: Boolean by mutableStateOf(false)
        private set

    private var dragStartY: Float = Float.NaN

    @UiThread
    internal fun onPointerDown(position: Offset) {
        if (!isSeeking && position.isSpecified) {
            dragStartY = position.y
        }
    }

    @UiThread
    internal fun onSwipeStarted() {
        seekDelta = 0f
        isCancelled = false
    }

    @UiThread
    internal fun onSwipeStopped() {
        if (seekDelta.isNaN()) return
        if (!isCancelled) {
            onSeek(deltaSeconds)
        }
        seekDelta = Float.NaN
        isCancelled = false
        dragStartY = Float.NaN
    }

    @UiThread
    internal fun onSwipeOffset(offsetPx: Float) {
        seekDelta += offsetPx
    }

    @UiThread
    internal fun updateCancellation(position: Offset): Boolean {
        val wasCancelled = isCancelled
        if (isSeeking) {
            isCancelled = isVerticalDragCancelled(dragStartY, position, cancelVerticalDragDistancePx)
        }
        return isCancelled != wasCancelled
    }

    val isSeeking: Boolean by derivedStateOf {
        !seekDelta.isNaN()
    }

    val deltaSeconds: Int by derivedStateOf {
        if (seekDelta.isNaN()) {
            0
        } else {
            val percentage = seekDelta / screenWidthPx
            (percentage * swipeSeekerConfig.maxDragSeconds).roundToInt()
        }
    }

    companion object {
        fun Modifier.swipeToSeek(
            seekerState: SwipeSeekerState,
            orientation: Orientation,
            enabled: Boolean = true,
            interactionSource: MutableInteractionSource? = null,
            reverseDirection: Boolean = false,
            onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit = {},
            onDragStopped: suspend CoroutineScope.(velocity: Float, cancelled: Boolean) -> Unit = { _, _ -> },
            onCancellationChanged: (cancelled: Boolean) -> Unit = {},
            onDelta: (Float) -> Unit = {},
        ): Modifier {
            return composed(
                inspectorInfo = {
                    name = "videoSeeker"
                    properties["seekerState"] = seekerState
                },
            ) {
                val currentOnDelta by rememberUpdatedState(onDelta)
                val currentOnDragStarted by rememberUpdatedState(onDragStarted)
                val currentOnDragStopped by rememberUpdatedState(onDragStopped)

                val handleDragStarted: suspend CoroutineScope.(Offset) -> Unit = remember(seekerState) {
                    {
                        seekerState.onSwipeStarted()
                        currentOnDragStarted(it)
                    }
                }
                val handleDragStopped: suspend CoroutineScope.(Float) -> Unit = remember(seekerState) {
                    {
                        val cancelled = seekerState.isCancelled
                        seekerState.onSwipeStopped()
                        currentOnDragStopped(it, cancelled)
                    }
                }
                draggable(
                    rememberDraggableState {
                        seekerState.onSwipeOffset(it)
                        currentOnDelta(it)
                    },
                    orientation,
                    onDragStarted = handleDragStarted,
                    onDragStopped = handleDragStopped,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    reverseDirection = reverseDirection,
                ).trackSwipeSeekCancellation(seekerState, onCancellationChanged)
            }
        }
    }
}
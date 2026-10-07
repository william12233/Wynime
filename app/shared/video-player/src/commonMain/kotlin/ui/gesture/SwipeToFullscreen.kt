package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.swipeToFullscreen(
    enabled: Boolean = true,
    swipeThreshold: Dp = 64.dp,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "swipeToFullscreen"
        properties["swipeThreshold"] = swipeThreshold
    },
) {
    val onEnterFullscreenState by rememberUpdatedState(onEnterFullscreen)
    val onExitFullscreenState by rememberUpdatedState(onExitFullscreen)
    val thresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    var totalDelta by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }
    draggable(
        rememberDraggableState { delta ->
            if (triggered) return@rememberDraggableState
            totalDelta += delta
            if (totalDelta <= -thresholdPx) {

                triggered = true
                onEnterFullscreenState()
            } else if (totalDelta >= thresholdPx) {

                triggered = true
                onExitFullscreenState()
            }
        },
        Orientation.Vertical,
        enabled = enabled,
        onDragStarted = {
            totalDelta = 0f
            triggered = false
        },
    )
}

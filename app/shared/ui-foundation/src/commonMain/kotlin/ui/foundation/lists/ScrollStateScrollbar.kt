package com.wynime.app.ui.foundation.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
expect fun ScrollStateVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
)

fun ScrollState.hasScrollableContent(): Boolean {
    return maxValue > 0 && maxValue != Int.MAX_VALUE
}

@Composable
fun ScrollStateVerticalScrollIndicator(
    state: ScrollState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    padding: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
    hideDelayMillis: Long = 700,
) {
    val density = LocalDensity.current
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    val shouldRender by remember(state) {
        derivedStateOf { state.hasScrollableContent() }
    }
    if (!shouldRender) return

    val paddingPx = with(density) { padding.toPx() }
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val trackHeightPx = (containerHeightPx - paddingPx * 2).coerceAtLeast(0f)

    val viewportHeightPx = state.viewportSize.toFloat()
    val totalContentHeightPx = viewportHeightPx + state.maxValue
    val thumbHeightPx = if (trackHeightPx > 0f && totalContentHeightPx > 0f) {
        val effectiveMinThumbHeightPx = minThumbHeightPx.coerceAtMost(trackHeightPx)
        (trackHeightPx * viewportHeightPx / totalContentHeightPx)
            .coerceIn(effectiveMinThumbHeightPx, trackHeightPx)
    } else {
        0f
    }
    val thumbTopPx = if (state.maxValue > 0) {
        (trackHeightPx - thumbHeightPx).coerceAtLeast(0f) *
            (state.value.toFloat() / state.maxValue).coerceIn(0f, 1f)
    } else {
        0f
    }
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

    var visible by remember { mutableStateOf(false) }
    val isScrollInProgress by remember(state) { derivedStateOf { state.isScrollInProgress } }
    LaunchedEffect(state, isScrollInProgress) {
        if (isScrollInProgress) {
            visible = true
        } else {
            delay(hideDelayMillis)
            if (!state.isScrollInProgress) {
                visible = false
            }
        }
    }

    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .width(thickness + padding * 2)
            .fillMaxHeight()
            .onSizeChanged { containerHeightPx = it.height.toFloat() },
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .padding(vertical = padding)
                    .width(thickness)
                    .background(trackColor, CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(vertical = padding)
                    .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                    .width(thickness)
                    .background(thumbColor, CircleShape)
                    .height(thumbHeightDp),
            )
        }
    }
}

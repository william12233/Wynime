package com.wynime.app.ui.foundation.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
expect fun LazyListVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
)

@Composable
fun LazyListVerticalScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    padding: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
    hideDelayMillis: Long = 700,
) {
    val density = LocalDensity.current

    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    val shouldRender by remember {
        derivedStateOf { state.canScrollBackward || state.canScrollForward }
    }
    if (!shouldRender) return

    val metrics by remember(state) {
        derivedStateOf { calculateScrollbarMetrics(state) }
    }
    val resolvedMetrics = metrics ?: return

    val paddingPx = with(density) { padding.toPx() }
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }

    val trackHeightPx = (containerHeightPx - paddingPx * 2).coerceAtLeast(0f)

    val thumbHeightPx = resolvedMetrics.thumbHeightPx(
        trackHeightPx = trackHeightPx,
        minThumbHeightPx = minThumbHeightPx,
    )
    val thumbTopPx = resolvedMetrics.thumbTopPx(
        trackHeightPx = trackHeightPx,
        thumbHeightPx = thumbHeightPx,
    )

    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

    var visible by remember { mutableStateOf(false) }

    val isScrollInProgress by remember { derivedStateOf { state.isScrollInProgress } }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            visible = true
        } else {
            delay(hideDelayMillis)
            if (!state.isScrollInProgress) {
                visible = false
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = modifier
                .width(thickness + padding * 2)
                .fillMaxHeight()
                .onSizeChanged { containerHeightPx = it.height.toFloat() },
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

private data class LazyListScrollbarMetrics(
    val totalItemsCount: Int,
    val averageItemSizePx: Float,
    val viewportHeightPx: Float,
    val scrollPx: Float,
    val maxScrollPx: Float,
) {

    fun thumbHeightPx(trackHeightPx: Float, minThumbHeightPx: Float): Float {

        if (trackHeightPx <= 0f) return 0f
        if (viewportHeightPx <= 0f) return 0f

        val totalContentPx = viewportHeightPx + maxScrollPx
        if (totalContentPx <= 0f) return 0f

        val raw = trackHeightPx * (viewportHeightPx / totalContentPx)

        return raw.coerceIn(minThumbHeightPx, trackHeightPx)
    }

    fun thumbTopPx(trackHeightPx: Float, thumbHeightPx: Float): Float {

        val available = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

        val fraction = if (maxScrollPx > 0f) (scrollPx / maxScrollPx).coerceIn(0f, 1f) else 0f

        return available * fraction
    }
}

private fun calculateScrollbarMetrics(state: LazyListState): LazyListScrollbarMetrics? {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItemsCount = layoutInfo.totalItemsCount
    if (totalItemsCount <= 0 || visibleItems.isEmpty()) return null

    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat()
    if (viewportHeightPx <= 0f) return null

    val averageItemSizePx = visibleItems.map { it.size }.average().toFloat()
    if (averageItemSizePx <= 0f) return null

    val totalContentPx = averageItemSizePx * totalItemsCount
    val maxScrollPx = (totalContentPx - viewportHeightPx).coerceAtLeast(0f)
    if (maxScrollPx <= 0f) return null

    val scrollPx = (state.firstVisibleItemIndex * averageItemSizePx + state.firstVisibleItemScrollOffset)
        .coerceIn(0f, maxScrollPx)

    return LazyListScrollbarMetrics(
        totalItemsCount = totalItemsCount,
        averageItemSizePx = averageItemSizePx,
        viewportHeightPx = viewportHeightPx,
        scrollPx = scrollPx,
        maxScrollPx = maxScrollPx,
    )
}


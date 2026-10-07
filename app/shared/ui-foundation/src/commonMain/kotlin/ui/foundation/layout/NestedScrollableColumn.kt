package com.wynime.app.ui.foundation.layout

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.StandardDecelerateEasing
import com.wynime.app.ui.foundation.effects.onPointerEventMultiplatform
import kotlin.math.roundToInt

@Composable
fun rememberNestedScrollableColumnState(
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
): NestedScrollableColumnState {
    return rememberSaveable(flingBehavior, saver = NestedScrollableColumnState.saver(flingBehavior)) {
        NestedScrollableColumnState(flingBehavior)
    }
}

@Stable
class NestedScrollableColumnState(
    internal val flingBehavior: FlingBehavior,
    initialScrolledOffset: Float = 0f,
) {

    var headerHeight by mutableIntStateOf(0)
        internal set

    var scrolledOffset by mutableFloatStateOf(initialScrolledOffset)
        internal set

    val isHeaderScrolledOut by derivedStateOf {
        headerHeight > 0 && scrolledOffset.roundToInt() >= headerHeight
    }

    val isHeaderFullyVisible by derivedStateOf {
        scrolledOffset.roundToInt() <= 0
    }

    val collapseProgress by derivedStateOf {
        if (headerHeight == 0) 0f else (scrolledOffset / headerHeight).coerceIn(0f, 1f)
    }

    val scrollableState = ScrollableState { delta ->
        val previous = scrolledOffset
        val new = (previous - delta).coerceIn(0f, headerHeight.toFloat())
        scrolledOffset = new
        previous - new
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            return if (available.y < 0) {
                Offset(0f, scrollableState.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y > 0) {
                return Offset(0f, scrollableState.dispatchRawDelta(available.y))
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0) {
                scrollableState.scroll {
                    with(flingBehavior) {
                        performFling(available.y)
                    }
                }
            }
            return super.onPostFling(consumed, available)
        }
    }

    suspend fun animateExpandHeader() {
        scrollableState.animateScrollBy(
            scrolledOffset,
            tween(500, easing = StandardDecelerateEasing),
        )
    }

    companion object {
        fun saver(flingBehavior: FlingBehavior): Saver<NestedScrollableColumnState, Float> = Saver(
            save = { it.scrolledOffset },
            restore = { NestedScrollableColumnState(flingBehavior, initialScrolledOffset = it) },
        )
    }
}

@Stable
abstract class NestedScrollableScope {

    abstract val isHeaderScrolledOut: Boolean

    abstract val collapseProgress: Float

    abstract fun Modifier.nestedScrollWorkaround(scrollableState: ScrollableState): Modifier
}

private class NestedScrollableScopeImpl(
    private val state: NestedScrollableColumnState,
) : NestedScrollableScope() {
    override val isHeaderScrolledOut: Boolean
        get() = state.isHeaderScrolledOut

    override val collapseProgress: Float
        get() = state.collapseProgress

    override fun Modifier.nestedScrollWorkaround(scrollableState: ScrollableState): Modifier = composed {
        val scope = rememberCoroutineScope()
        var isInProgress = false
        onPointerEventMultiplatform(PointerEventType.Scroll, pass = PointerEventPass.Final) {
            if (isInProgress) return@onPointerEventMultiplatform

            val event = it.changes.getOrNull(0) ?: return@onPointerEventMultiplatform
            if (event.type != PointerType.Mouse) {

                return@onPointerEventMultiplatform
            }

            val scrollDelta = event.scrollDelta
            if (scrollDelta != Offset.Unspecified && scrollDelta != Offset.Zero) {
                if (!scrollableState.canScrollBackward && scrollDelta.y < -0.5f) {
                    isInProgress = true
                    scope.launch {
                        try {
                            state.animateExpandHeader()
                        } finally {
                            isInProgress = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NestedScrollableColumn(
    header: @Composable NestedScrollableScope.() -> Unit,
    content: @Composable NestedScrollableScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: NestedScrollableColumnState = rememberNestedScrollableColumnState(),
) {
    val scope = remember(state) { NestedScrollableScopeImpl(state) }
    Layout(
        content = {

            Box { scope.header() }
            Box { scope.content() }
        },
        modifier = modifier
            .clipToBounds()
            .nestedScroll(state.nestedScrollConnection)
            .scrollable(state.scrollableState, Orientation.Vertical),
    ) { measurables, constraints ->
        require(constraints.hasBoundedHeight) { "NestedScrollableColumn requires bounded height constraints" }
        val viewportHeight = constraints.maxHeight

        val headerPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity),
        )
        if (state.headerHeight != headerPlaceable.height) {
            state.headerHeight = headerPlaceable.height
        }

        if (state.scrolledOffset > headerPlaceable.height) {
            state.scrolledOffset = headerPlaceable.height.toFloat()
        }
        val scrolledOffset = state.scrolledOffset.roundToInt().coerceIn(0, headerPlaceable.height)

        val contentHeight = (viewportHeight - headerPlaceable.height + scrolledOffset)
            .coerceIn(0, viewportHeight)
        val contentPlaceable = measurables[1].measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = contentHeight),
        )

        val width = maxOf(headerPlaceable.width, contentPlaceable.width)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, viewportHeight) {
            headerPlaceable.place(0, -scrolledOffset)
            contentPlaceable.place(0, headerPlaceable.height - scrolledOffset)
        }
    }
}

@Composable
@Preview
private fun PreviewNestedScrollableColumn() = ProvideCompositionLocalsForPreview {
    Surface(color = MaterialTheme.colorScheme.surface) {
        val state = rememberNestedScrollableColumnState()
        val pagerState = rememberPagerState(pageCount = { 3 })
        val uiScope = rememberCoroutineScope()

        NestedScrollableColumn(
            header = {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Header\n" +
                                    "collapseProgress = ${(collapseProgress * 100).roundToInt()}%\n" +
                                    "isHeaderScrolledOut = $isHeaderScrolledOut",
                        )
                    }

                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        repeat(3) { index ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { uiScope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text("Tab $index") },
                            )
                        }
                    }
                }
            },
            content = {
                HorizontalPager(
                    pagerState,
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val listState = rememberLazyListState()
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .nestedScrollWorkaround(listState),
                        state = listState,
                    ) {
                        items(100) { item ->
                            Text(
                                "Page $page - Item $item",
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            Modifier.fillMaxSize(),
            state = state,
        )
    }
}

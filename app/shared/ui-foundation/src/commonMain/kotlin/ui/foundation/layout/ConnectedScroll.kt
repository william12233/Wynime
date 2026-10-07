package com.wynime.app.ui.foundation.layout

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import com.wynime.app.ui.foundation.interaction.nestedScrollWorkaround
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun rememberConnectedScrollState(
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
): ConnectedScrollState {
    return rememberSaveable(flingBehavior, saver = ConnectedScrollState.saver(flingBehavior)) {
        ConnectedScrollState(flingBehavior)
    }
}

@Stable
class ConnectedScrollState(
    val flingBehavior: FlingBehavior,
    initialContainerHeight: Int = 0,
    initialScrolledOffset: Float = 0f,
) {
    val scrollableState = ScrollableState { available ->
        val previous = scrolledOffset
        val new = (scrolledOffset + available).coerceIn(-containerHeight.toFloat(), 0f)
        scrolledOffset = new
        new - previous

    }

    var containerHeight by mutableIntStateOf(initialContainerHeight)
        internal set

    var scrolledOffset by mutableFloatStateOf(initialScrolledOffset)
        internal set

    val isScrolledTop by derivedStateOf {
        if (containerHeight == 0) {
            return@derivedStateOf false
        }
        scrolledOffset.toInt() == -containerHeight
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (available.y < 0) {
                Offset(0f, scrollableState.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }
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

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y > 0) {

                return Offset(0f, scrollableState.dispatchRawDelta(available.y))
            }
            return super.onPostScroll(consumed, available, source)
        }
    }

    companion object {
        fun saver(flingBehavior: FlingBehavior) = Saver<ConnectedScrollState, Long>(
            save = {
                it.containerHeight.toLong() or (it.scrolledOffset.toRawBits().toUInt().toLong() shl 32)
            },
            restore = {
                ConnectedScrollState(
                    flingBehavior,
                    initialContainerHeight = (it and 0xFFFFFFFF).toInt(),
                    initialScrolledOffset = Float.fromBits((it shr 32).toInt()),
                )
            },
        )
    }
}

fun Modifier.connectedScrollContainer(state: ConnectedScrollState): Modifier {
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(
            placeable.width,
            (placeable.height - state.scrolledOffset.roundToInt().absoluteValue).coerceAtLeast(0),
        ) {
            placeable.place(0, y = state.scrolledOffset.roundToInt())
        }
    }
}

fun Modifier.connectedScrollTarget(state: ConnectedScrollState): Modifier {
    return onSizeChanged { state.containerHeight = it.height }
}

fun Modifier.connectedScroll(state: ConnectedScrollState): Modifier {
    return connectedScrollContainer(state).connectedScrollTarget(state)
}
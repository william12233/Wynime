package com.wynime.app.ui.foundation.input

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

@Stable
class ActiveInputSourceState {

    var current: PointerType by mutableStateOf(PointerType.Unknown)
        private set

    var latest: PointerType = PointerType.Unknown
        private set

    var hasSeenMouse: Boolean by mutableStateOf(false)
        private set

    internal fun record(type: PointerType) {
        val gesturePointerType = type.asGesturePointerType()
        if (gesturePointerType == PointerType.Touch || gesturePointerType == PointerType.Mouse) {
            latest = gesturePointerType
        }
    }

    internal fun commit() {
        if (latest == PointerType.Mouse && !hasSeenMouse) {
            hasSeenMouse = true
        }
        if (latest != current) {
            current = latest
        }
    }
}

val LocalActiveInputSource = compositionLocalOf { ActiveInputSourceState() }

fun PointerType.asGesturePointerType(): PointerType = when (this) {
    PointerType.Stylus, PointerType.Eraser -> PointerType.Touch
    else -> this
}

fun Modifier.trackActiveInputSource(state: ActiveInputSourceState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)

                val interactive = when (event.type) {
                    PointerEventType.Press,
                    PointerEventType.Move,
                    PointerEventType.Release,
                    PointerEventType.Scroll,
                        -> true

                    else -> false
                }
                if (!interactive) continue

                event.changes.firstOrNull()?.type?.let(state::record)

                if (event.type != PointerEventType.Press) {
                    state.commit()
                }
            }
        }
    }

fun Modifier.scrollFromPointerType(
    requiredPointerType: PointerType,
    orientation: Orientation,
): Modifier = composed {
    val activeInputSource = LocalActiveInputSource.current
    val connection = remember(activeInputSource, requiredPointerType, orientation) {
        PointerTypeNestedScrollConnection(
            activeInputSource = activeInputSource,
            requiredPointerType = requiredPointerType,
            orientation = orientation,
        )
    }
    trackActiveInputSource(activeInputSource).nestedScroll(connection)
}

private class PointerTypeNestedScrollConnection(
    private val activeInputSource: ActiveInputSourceState,
    private val requiredPointerType: PointerType,
    private val orientation: Orientation,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (
            source != NestedScrollSource.UserInput ||
            activeInputSource.latest == requiredPointerType
        ) {
            return Offset.Zero
        }
        return when (orientation) {
            Orientation.Horizontal -> Offset(available.x, 0f)
            Orientation.Vertical -> Offset(0f, available.y)
        }
    }
}

fun Modifier.touchHorizontalScrollOnly(): Modifier =
    scrollFromPointerType(PointerType.Touch, Orientation.Horizontal)

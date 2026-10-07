package com.wynime.app.ui.foundation.interaction

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.animation.StandardDecelerateEasing
import com.wynime.app.ui.foundation.effects.onPointerEventMultiplatform
import com.wynime.app.ui.foundation.layout.ConnectedScrollState

fun Modifier.nestedScrollWorkaround(
    scrollableState: ScrollableState,
    connectedScrollState: ConnectedScrollState,
): Modifier {
    return composed {
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

                            connectedScrollState.scrollableState.animateScrollBy(
                                -connectedScrollState.scrolledOffset,
                                tween(500, easing = StandardDecelerateEasing),
                            )
                        } finally {
                            isInProgress = false
                        }
                    }
                }
            }
        }
    }
}
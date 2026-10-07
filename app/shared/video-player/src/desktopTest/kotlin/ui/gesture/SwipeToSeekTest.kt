package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.videoplayer.ui.gesture.SwipeSeekerState.Companion.swipeToSeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SwipeToSeekTest {
    @Test
    fun `horizontal drag from top corner still seeks`() = runWynimeComposeUiTest {
        val seeks = mutableListOf<Int>()
        setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val state = rememberSwipeSeekerState(constraints.maxWidth, onSeek = seeks::add)
                Box(
                    Modifier.fillMaxSize()
                        .testTag("swipeTarget")
                        .swipeToSeek(state, Orientation.Horizontal),
                )
            }
        }

        onNodeWithTag("swipeTarget").performTouchInput {
            down(Offset(width * 0.95f, height * 0.2f))
            moveTo(Offset(width * 0.5f, height * 0.1f))
            up()
        }

        assertEquals(1, seeks.size)
        assertTrue(seeks[0] < 0)
    }

    @Test
    fun `144 dp cancellation threshold respects density`() = runWynimeComposeUiTest {
        val seeks = mutableListOf<Int>()
        val cancellationThresholdPx = 144f * 2f
        val marginPx = 16f
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val state = rememberSwipeSeekerState(constraints.maxWidth, onSeek = seeks::add)
                    Box(
                        Modifier.fillMaxSize()
                            .testTag("swipeTarget")
                            .swipeToSeek(state, Orientation.Horizontal),
                    )
                }
            }
        }

        onNodeWithTag("swipeTarget").performTouchInput {
            val start = Offset(width * 0.2f, height * 0.8f)
            down(start)
            moveTo(Offset(width * 0.8f, start.y))
            moveTo(Offset(width * 0.8f, start.y - cancellationThresholdPx + marginPx))
            up()
        }
        assertEquals(1, seeks.size)

        seeks.clear()
        onNodeWithTag("swipeTarget").performTouchInput {
            val start = Offset(width * 0.2f, height * 0.8f)
            down(start)
            moveTo(Offset(width * 0.8f, start.y))
            moveTo(Offset(width * 0.8f, start.y - cancellationThresholdPx - marginPx))
            up()
        }

        assertEquals(emptyList(), seeks)
    }
}

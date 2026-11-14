package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.videoplayer.ui.gesture.SwipeSeekerState.Companion.swipeToSeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PlayerGestureHostSwipeSequenceTest {
    @Test
    fun `level gestures remain available after horizontal swipe seek`() = runWynimeComposeUiTest {
        val seeks = mutableListOf<Int>()
        val brightness = mutableFloatStateOf(0.5f)
        val volume = mutableFloatStateOf(0.5f)

        setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val seeker = rememberSwipeSeekerState(constraints.maxWidth, onSeek = seeks::add)
                val brightnessController = remember {
                    TestLevelController(brightness)
                }
                val volumeController = remember {
                    TestLevelController(volume)
                }

                Row(
                    Modifier.fillMaxSize()
                        .testTag("gestureHost")
                        .swipeToSeek(seeker, Orientation.Horizontal),
                ) {
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .testTag("brightnessZone")
                            .swipeLevelControl(
                                brightnessController,
                                stepSize = 20.dp,
                                orientation = Orientation.Vertical,
                                enabled = !seeker.isSeeking,
                                step = 0.05f,
                            ),
                    )
                    Box(Modifier.weight(1f).fillMaxHeight().testTag("centerZone"))
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .testTag("volumeZone")
                            .swipeLevelControl(
                                volumeController,
                                stepSize = 20.dp,
                                orientation = Orientation.Vertical,
                                enabled = !seeker.isSeeking,
                                step = 0.05f,
                            ),
                    )
                }
            }
        }

        onNodeWithTag("gestureHost").performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveTo(Offset(width * 0.7f, height * 0.5f))
            up()
        }
        onNodeWithTag("brightnessZone").performTouchInput {
            down(Offset(width * 0.5f, height * 0.7f))
            moveTo(Offset(width * 0.5f, height * 0.3f))
            up()
        }
        onNodeWithTag("volumeZone").performTouchInput {
            down(Offset(width * 0.5f, height * 0.7f))
            moveTo(Offset(width * 0.5f, height * 0.3f))
            up()
        }

        assertEquals(1, seeks.size)
        assertTrue(brightness.floatValue > 0.5f)
        assertTrue(volume.floatValue > 0.5f)
    }

    private class TestLevelController(
        private val value: MutableFloatState,
    ) : LevelController {
        override val level: Float get() = value.floatValue
        override val range: ClosedRange<Float> = 0f..1f

        override fun setLevel(level: Float) {
            value.floatValue = level.coerceIn(range)
        }
    }
}

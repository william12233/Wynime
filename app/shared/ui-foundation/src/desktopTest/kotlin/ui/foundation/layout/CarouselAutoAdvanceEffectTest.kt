package com.wynime.app.ui.foundation.layout

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.wynime.app.ui.foundation.ProvideFoundationCompositionLocalsForTest
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class CarouselAutoAdvanceEffectTest {
    private fun createTestLifecycleOwner(initialState: Lifecycle.State): TestLifecycleOwner {
        return TestLifecycleOwner(initialState)
    }

    @Test

    fun `auto-advance - whenEnabledAndLifecycleResumed - advancesPageAfterPeriod`() =
        runWynimeComposeUiTest {
            mainClock.autoAdvance = false
            val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
            val pageCount = 20
            val periodMs = 3_000L

            val carouselState = CarouselState(0) { pageCount }

            setContent {
                ProvideFoundationCompositionLocalsForTest {
                    CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                        CarouselAutoAdvanceEffect(
                            enabled = true,
                            carouselState = carouselState,
                            period = periodMs.milliseconds - 50.milliseconds,
                            animationSpec = snap(),
                        )
                    }
                    TestCarousel(carouselState)
                }
            }
            runOnIdle {
                assertEquals(0, carouselState.pagerState1.currentPage)
            }

            mainClock.advanceTimeBy(3000)
            runOnIdle {
                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(2, carouselState.pagerState1.currentPage)
            }
        }

    @Test
    fun `auto-advance - whenDisabled - doesNotAdvancePage`() =
        runWynimeComposeUiTest {
            mainClock.autoAdvance = false
            val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
            val pageCount = 5
            val periodMs = 3_000L

            val carouselState = CarouselState(0) { pageCount }

            setContent {
                ProvideFoundationCompositionLocalsForTest {
                    CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {

                        CarouselAutoAdvanceEffect(
                            enabled = false,
                            carouselState = carouselState,
                            period = periodMs.milliseconds - 50.milliseconds,
                            animationSpec = snap(),
                        )
                    }
                    TestCarousel(carouselState)
                }
            }

            mainClock.advanceTimeBy(periodMs * 2)
            runOnIdle {

                assertEquals(0, carouselState.pagerState1.currentPage)
            }
        }

    @Test
    fun `auto-advance - userScrollInProgress - skipAutoAdvance`() =
        runWynimeComposeUiTest {
            mainClock.autoAdvance = false
            val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
            val pageCount = 20
            val periodMs = 3_000L

            val carouselState = CarouselState(0) { pageCount }

            setContent {
                ProvideFoundationCompositionLocalsForTest {
                    CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                        CarouselAutoAdvanceEffect(
                            enabled = true,
                            carouselState = carouselState,
                            period = periodMs.milliseconds - 50.milliseconds,
                            animationSpec = snap(),
                        )
                    }
                    TestCarousel(carouselState)
                }
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            onNodeWithTag("carousel").performTouchInput {
                down(center)
                moveBy(Offset(viewConfiguration.touchSlop, 0f), delayMillis = 0)

            }
            runOnIdle {
                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {

                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            onNodeWithTag("carousel").performTouchInput {
                up()
            }
            mainClock.autoAdvance = true
            waitForIdle()
            runOnIdle {
                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(2, carouselState.pagerState1.currentPage)
            }
        }

    @Composable
    private fun TestCarousel(carouselState: CarouselState) {
        Surface {
            HorizontalMultiBrowseCarousel(
                carouselState,
                preferredItemWidth = 100.dp,
                Modifier.width(300.dp).height(200.dp).testTag("carousel"),
                flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState),
            ) {
                CarouselItem(
                    label = { Text("Item $it") },
                ) {
                    Box(Modifier.height(200.dp).width(100.dp))
                }
            }
        }
    }

    @Test
    fun `auto-advance - lifecyclePausedThenResumed - resumesAutoAdvance`() =
        runWynimeComposeUiTest {
            mainClock.autoAdvance = false
            val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)
            val pageCount = 10
            val periodMs = 3_000L

            val carouselState = CarouselState(0) { pageCount }

            setContent {
                ProvideFoundationCompositionLocalsForTest {
                    CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                        CarouselAutoAdvanceEffect(
                            enabled = true,
                            carouselState = carouselState,
                            period = periodMs.milliseconds - 50.milliseconds,
                            animationSpec = snap(),
                        )
                    }
                    TestCarousel(carouselState)
                }
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            runOnIdle {
                testLifecycleOwner.currentState = Lifecycle.State.STARTED
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            runOnIdle {
                testLifecycleOwner.currentState = Lifecycle.State.RESUMED
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(2, carouselState.pagerState1.currentPage)
            }
        }

    @Test
    fun `auto-advance - wrapAround - goesBackToFirstPage`() =
        runWynimeComposeUiTest {
            mainClock.autoAdvance = false
            val testLifecycleOwner = createTestLifecycleOwner(Lifecycle.State.RESUMED)

            val pageCount = 6
            val periodMs = 3_000L

            val carouselState = CarouselState(0) { pageCount }

            setContent {
                ProvideFoundationCompositionLocalsForTest {
                    CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                        CarouselAutoAdvanceEffect(
                            enabled = true,
                            carouselState = carouselState,
                            period = periodMs.milliseconds - 500.milliseconds,
                            animationSpec = snap(),
                        )
                    }
                    TestCarousel(carouselState)
                }
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(1, carouselState.pagerState1.currentPage)
            }

            mainClock.advanceTimeBy(periodMs)
            runOnIdle {
                assertEquals(2, carouselState.pagerState1.currentPage)
            }

        }

    private val CarouselState.pagerState1: PagerState
        get() =
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
            this.pagerState

}
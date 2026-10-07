package com.wynime.app.ui.foundation.layout

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun CarouselAutoAdvanceEffect(
    enabled: Boolean,
    carouselState: CarouselState,
    period: Duration = 3.seconds,
    animationSpec: FiniteAnimationSpec<Float> = LocalWynimeMotionScheme.current.carouselAutoAdvanceSpec,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(carouselState, lifecycle, animationSpec) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {

            snapshotFlow { !enabled || carouselState.isScrollInProgress }.collectLatest { skip ->

                if (skip) {
                    return@collectLatest
                }

                while (currentCoroutineContext().isActive) {
                    delay(period)
                    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        val pageCount = carouselState.pagerState.pageCount
                        if (pageCount <= 1) return@launch

                        val targetPage = (carouselState.pagerState.currentPage + 1) % pageCount

                        val pager = carouselState.pagerState
                        val layoutInfo = pager.layoutInfo
                        val visiblePagesInfo = layoutInfo.visiblePagesInfo
                        val lastItem = visiblePagesInfo.lastOrNull() ?: return@launch

                        if (lastItem.index == pager.pageCount - 1) {

                            if (layoutInfo.viewportEndOffset - lastItem.offset >= layoutInfo.pageSize * 0.75f) {
                                carouselState.animateScrollToItem(0, animationSpec)
                            } else {

                                val scrollOffset =
                                    layoutInfo.pageSize - (layoutInfo.viewportEndOffset - lastItem.offset)
                                carouselState.animateScrollBy(scrollOffset.toFloat(), animationSpec)
                            }
                        } else {
                            if (targetPage < 0 || targetPage >= carouselState.pagerState.pageCount) {
                                return@launch
                            }
                            carouselState.animateScrollToItem(targetPage, animationSpec)
                        }
                    }
                }
            }
        }
    }
}

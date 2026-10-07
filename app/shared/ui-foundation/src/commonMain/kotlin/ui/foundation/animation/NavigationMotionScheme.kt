package com.wynime.app.ui.foundation.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wynime.app.ui.foundation.theme.EasingDurations
import kotlin.math.roundToInt

@Stable
@Immutable
data class NavigationMotionScheme(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val popEnterTransition: EnterTransition,
    val popExitTransition: ExitTransition,
) {
    companion object {
        inline val current
            @Composable get() = LocalNavigationMotionScheme.current

        private const val enterDuration = EasingDurations.emphasizedDecelerate
        private const val exitDuration = EasingDurations.emphasizedAccelerate

        private val enterEasing = EmphasizedDecelerateEasing
        private val exitEasing = EmphasizedAccelerateEasing

        fun calculate(useSlide: Boolean): NavigationMotionScheme {
            val slideInMargin = 1f / 16
            val slideOutMargin = 1f / 16

            val enterTransition: EnterTransition = run {
                if (useSlide) {
                    val delay = exitDuration
                    val slideIn = slideInHorizontally(
                        tween(enterDuration, delayMillis = delay, easing = enterEasing),
                        initialOffsetX = { (it * slideInMargin).roundToInt() },
                    )
                    val fadeIn = fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
                    slideIn.plus(fadeIn)
                } else {
                    fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
                }
            }

            val exitTransition: ExitTransition = kotlin.run {
                val fadeOut = fadeOut(tween(exitDuration, easing = exitEasing))
                if (useSlide) {
                    slideOutHorizontally(
                        tween(exitDuration, easing = exitEasing),
                        targetOffsetX = { -(it * slideOutMargin).roundToInt() },
                    ).plus(fadeOut)
                } else {
                    fadeOut
                }
            }

            val popEnterTransition = run {
                val fadeIn = fadeIn(tween(enterDuration, delayMillis = exitDuration, easing = enterEasing))
                if (useSlide) {
                    slideInHorizontally(
                        tween(enterDuration, delayMillis = exitDuration, easing = enterEasing),
                        initialOffsetX = { -(it * slideInMargin).roundToInt() },
                    ) + fadeIn
                } else {
                    fadeIn
                }
            }

            val popExitTransition: ExitTransition = run {
                val fadeOut = fadeOut(tween(exitDuration, easing = exitEasing))
                if (useSlide) {
                    val slide = slideOutHorizontally(
                        tween(exitDuration, easing = exitEasing),
                        targetOffsetX = { (it * slideOutMargin).roundToInt() },
                    )
                    slide.plus(fadeOut)
                } else {
                    fadeOut
                }
            }

            return NavigationMotionScheme(
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            )
        }
    }
}

@Stable
val LocalNavigationMotionScheme = staticCompositionLocalOf<NavigationMotionScheme> {
    error("No LocalNavigationMotionScheme provided")
}

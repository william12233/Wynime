package com.wynime.app.ui.foundation.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Stable

@Stable
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)

@Stable
val StandardDecelerateEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1f)

@Stable
val StandardAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 1f, 1f)

@Stable
val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

@Stable
val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

val EmphasizedEasing: Easing = Easing { fraction ->

    when {
        fraction <= 0f -> 0f
        fraction >= 1f -> 1f

        fraction < 0.166666f -> {

            val localFraction = fraction / 0.166666f

            val yLocal = segment1Easing.transform(localFraction)
            yLocal * 0.4f
        }

        else -> {

            val localFraction = (fraction - 0.166666f) / (1f - 0.166666f)

            val yLocal = segment2Easing.transform(localFraction)
            0.4f + (0.6f * yLocal)
        }
    }
}

private val segment1Easing = CubicBezierEasing(
    a = 0.3f,
    b = 0f,
    c = 0.8f,
    d = 0.15f,
)

private val segment2Easing = CubicBezierEasing(
    a = 0.05f,
    b = 0.7f,
    c = 0.1f,
    d = 1f,
)
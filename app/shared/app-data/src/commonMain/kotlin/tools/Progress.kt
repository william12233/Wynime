package com.wynime.app.tools

import com.wynime.app.tools.Progress.Companion.Unspecified
import kotlin.jvm.JvmInline

@JvmInline
value class Progress private constructor(
    private val rawValue: Float,
) {
    init {
        require(rawValue in 0f..1f || rawValue == -1f) { "Progress must be in range 0f..1f, but was $rawValue" }
    }

    val isUnspecified: Boolean get() = rawValue == -1f

    val isFinished get() = rawValue == 1f

    fun getOrNull(): Float? = if (isUnspecified) null else rawValue

    fun getOrDefault(otherwise: Float): Float = if (isUnspecified) otherwise else rawValue

    companion object {

        val Unspecified = Progress(-1f)
        val Zero = Progress(0f)

        fun fromZeroToOne(value: Float): Progress {
            return Progress(value.coerceIn(0f, 1f))
        }
    }
}

fun Progress.getOrMinusOne(): Float = getOrDefault(-1f)
fun Progress.getOrZero(): Float = getOrDefault(0f)

fun Progress.toPercentageOrZero(): Float = getOrZero() * 100f

fun Float.toProgress(): Progress = Progress.fromZeroToOne(this)
fun Float?.toProgress(): Progress = this?.toProgress() ?: Unspecified

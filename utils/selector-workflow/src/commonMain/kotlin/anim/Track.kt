package com.wynime.utils.selectorworkflow.anim

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import kotlin.time.Duration

fun interface Interpolator<T> {
    fun interpolate(from: T, to: T, fraction: Float): T
}

object Interpolators {
    val Float: Interpolator<kotlin.Float> = Interpolator { a, b, f -> a + (b - a) * f }

    private val StepAny: Interpolator<Any?> = Interpolator { a, _, _ -> a }

    @Suppress("UNCHECKED_CAST")
    fun <T> step(): Interpolator<T> = StepAny as Interpolator<T>
}

@Immutable
data class Keyframe<T>(
    val time: Duration,
    val value: T,
    val easing: Easing = Easings.Linear,
)

internal fun <T> sampleKeyframes(
    keys: List<Keyframe<T>>,
    interpolator: Interpolator<T>,
    time: Duration,
): T {
    if (time <= keys.first().time) return keys.first().value
    if (time >= keys.last().time) return keys.last().value

    var lo = 0
    var hi = keys.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (keys[mid].time <= time) lo = mid else hi = mid - 1
    }
    val from = keys[lo]
    val to = keys.getOrNull(lo + 1) ?: return from.value
    val span = (to.time - from.time).inWholeMicroseconds
    if (span <= 0L) return to.value
    val raw = (time - from.time).inWholeMicroseconds.toFloat() / span
    return interpolator.interpolate(from.value, to.value, from.easing.transform(raw))
}

@Immutable
class Track<T> internal constructor(
    private val keys: List<Keyframe<T>>,
    private val interpolator: Interpolator<T>,
) {
    init {
        require(keys.isNotEmpty()) { "Track must have at least one keyframe" }
    }

    val start: Duration get() = keys.first().time
    val end: Duration get() = keys.last().time
    val keyframes: List<Keyframe<T>> get() = keys

    fun valueAt(time: Duration): T = sampleKeyframes(keys, interpolator, time)

    companion object {
        fun <T> constant(value: T): Track<T> =
            Track(listOf(Keyframe(Duration.ZERO, value)), Interpolators.step())
    }
}

class TrackBuilder<T> internal constructor(
    initial: T,
    private val interpolator: Interpolator<T>,
) {
    private val keys = mutableListOf(Keyframe(Duration.ZERO, initial))

    fun valueAt(time: Duration): T = sampleKeyframes(keys, interpolator, time)

    val lastTime: Duration get() = keys.last().time

    fun key(time: Duration, value: T, easing: Easing = Easings.Linear): TrackBuilder<T> {
        require(time >= Duration.ZERO) { "keyframe time must not be negative: $time" }
        while (keys.size > 1 && keys.last().time > time) {
            keys.removeAt(keys.lastIndex)
        }
        val last = keys.last()
        if (last.time == time) {
            keys[keys.lastIndex] = Keyframe(time, value, easing)
        } else {
            keys += Keyframe(time, value, easing)
        }
        return this
    }

    fun ramp(
        from: Duration,
        duration: Duration,
        value: T,
        easing: Easing = Easings.Standard,
    ): TrackBuilder<T> {
        val at = valueAt(from)
        key(from, at, easing)
        key(from + duration, value)
        return this
    }

    internal fun build(): Track<T> = Track(keys.toList(), interpolator)
}

internal fun floatTrack(initial: Float) = TrackBuilder(initial, Interpolators.Float)

internal fun <T> stepTrack(initial: T) = TrackBuilder(initial, Interpolators.step())

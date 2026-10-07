package com.wynime.utils.selectorworkflow.anim

import kotlin.time.Duration

internal class ToneChannel<T>(initial: T) {
    val current = stepTrack(initial)
    val previous = stepTrack(initial)
    val blend = floatTrack(1f)

    fun shift(to: T, at: Duration, over: Duration) {
        previous.key(at, current.valueAt(at))
        current.key(at, to)
        blend.key(at, 0f)
        blend.key(at + over, 1f)
        previous.key(at + over, to)
    }

    fun snap(to: T, at: Duration) {
        previous.key(at, to)
        current.key(at, to)
        blend.key(at, 1f)
    }

    fun build(): ToneTracks<T> = ToneTracks(current.build(), previous.build(), blend.build())
}

internal class ToneTracks<T>(
    private val current: Track<T>,
    private val previous: Track<T>,
    private val blend: Track<Float>,
) {
    fun current(t: Duration): T = current.valueAt(t)
    fun previous(t: Duration): T = previous.valueAt(t)
    fun blend(t: Duration): Float = blend.valueAt(t)
}

package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.runtime.Stable

@Stable
internal class DoubleTapSeekSequenceState {
    private var direction: SkipDirection? = null
    private var multiplier = 0

    fun nextMultiplier(direction: SkipDirection): Int {
        multiplier = if (this.direction == direction) {
            multiplier + 1
        } else {
            1
        }
        this.direction = direction
        return multiplier
    }

    fun reset() {
        direction = null
        multiplier = 0
    }
}

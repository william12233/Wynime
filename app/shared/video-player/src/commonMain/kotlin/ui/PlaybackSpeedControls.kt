package com.wynime.app.videoplayer.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.SLIDER_VALUE_STEP
import com.wynime.app.ui.foundation.quantizeSliderValue
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.PlaybackSpeed

private const val KEYBOARD_SPEED_STEP: Float = SLIDER_VALUE_STEP

fun nextPlaybackSpeed(
    currentSpeed: Float,
    range: ClosedFloatingPointRange<Float>,
    direction: Int,
): Float = quantizeSliderValue(currentSpeed + direction * KEYBOARD_SPEED_STEP, range)

@Stable
class PlaybackSpeedControllerState(
    private val playbackSpeed: PlaybackSpeed,
    rangeProvider: () -> ClosedFloatingPointRange<Float> = { DEFAULT_SPEED_RANGE },
    private val onCommitSpeed: (Float) -> Unit = {},
    scope: CoroutineScope,
) {
    val speedRange: ClosedFloatingPointRange<Float> by derivedStateOf(rangeProvider)

    var currentSpeed: Float by mutableStateOf(playbackSpeed.value)
        private set

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            playbackSpeed.valueFlow
                .distinctUntilChanged()
                .collect { value -> currentSpeed = value }
        }
    }

    fun previewSpeed(value: Float) {
        applySpeed(value)
    }

    fun commitSpeed(value: Float) {
        applySpeed(value)
        onCommitSpeed(value)
    }

    private fun applySpeed(value: Float) {
        currentSpeed = value
        playbackSpeed.set(value)
    }

    companion object {
        val DEFAULT_SPEED_RANGE: ClosedFloatingPointRange<Float> = 0.5f..2.5f
    }
}

@OptIn(InternalForInheritanceMediampApi::class)
object NoOpPlaybackSpeedController : PlaybackSpeed {
    override val value: Float = 1f
    override val valueFlow: Flow<Float> = flowOf(1f)

    override fun set(speed: Float) {

    }
}

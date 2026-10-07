package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.utils.platform.annotations.SerializationOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Immutable
@Serializable
enum class FullscreenSwitchMode {

    ALWAYS_SHOW_FLOATING,

    AUTO_HIDE_FLOATING,

    ONLY_IN_CONTROLLER
}

@Serializable
enum class VideoEnhancementDefaultMode {
    OFF,
    PERFORMANCE,
    QUALITY,
}

@Serializable
@Immutable
data class VideoScaffoldConfig @SerializationOnly constructor(

    val fullscreenSwitchMode: FullscreenSwitchMode = FullscreenSwitchMode.ALWAYS_SHOW_FLOATING,

    val enableFramePreview: Boolean = true,

    val videoEnhancementDefaultMode: VideoEnhancementDefaultMode = VideoEnhancementDefaultMode.OFF,

    val autoMarkDone: Boolean = true,

    val hideSelectorOnSelect: Boolean = false,

    val autoFullscreenOnLandscapeMode: Boolean = false,

    val autoPlayNext: Boolean = true,

    val autoSkipOpEd: Boolean = true,

    val opEdSkipDuration: Duration = 85.seconds,

    val autoSwitchMediaOnPlayerError: Boolean = true,

    val enableHighQualityAudioTimeStretch: Boolean = true,

    val enableExperimentalHlsSegmentFiltering: Boolean = false,

    val displayModeId: Int = 0,

    val fastForwardSpeed: Float = 2.5f,

    val playbackSpeed: Float = 1f,

    val rememberPlaybackSpeed: Boolean = true,

    val minPlaybackSpeed: Float = 0.5f,

    val maxPlaybackSpeed: Float = 2.5f,

    val playerVolume: PlayerVolume = PlayerVolume(1f, false),

    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {

    fun withPlaybackSpeedRange(
        range: ClosedFloatingPointRange<Float>,
    ): VideoScaffoldConfig {
        require(range.endInclusive - range.start >= MIN_PLAYBACK_SPEED_RANGE_WIDTH) {
            "Playback speed range must span at least $MIN_PLAYBACK_SPEED_RANGE_WIDTH, but was $range"
        }
        return copy(
            minPlaybackSpeed = range.start,
            maxPlaybackSpeed = range.endInclusive,
            playbackSpeed = playbackSpeed.coerceIn(range),
            fastForwardSpeed = fastForwardSpeed.coerceIn(range),
        )
    }

    companion object {

        const val MIN_SUPPORTED_PLAYBACK_SPEED: Float = 0.25f

        const val MAX_SUPPORTED_PLAYBACK_SPEED: Float = 4.0f

        const val MIN_PLAYBACK_SPEED_RANGE_WIDTH: Float = 0.25f

        fun normalizePlaybackSpeedRange(
            range: ClosedFloatingPointRange<Float>,
            previousRange: ClosedFloatingPointRange<Float>? = null,
        ): ClosedFloatingPointRange<Float> {
            val start = range.start.coerceIn(MIN_SUPPORTED_PLAYBACK_SPEED, MAX_SUPPORTED_PLAYBACK_SPEED)
            val end = range.endInclusive.coerceIn(MIN_SUPPORTED_PLAYBACK_SPEED, MAX_SUPPORTED_PLAYBACK_SPEED)
            if (end - start >= MIN_PLAYBACK_SPEED_RANGE_WIDTH) return start..end

            if (previousRange != null) {
                if (start > previousRange.start) {
                    return (end - MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtLeast(MIN_SUPPORTED_PLAYBACK_SPEED)..end
                }
                if (end < previousRange.endInclusive) {
                    return start..(start + MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtMost(MAX_SUPPORTED_PLAYBACK_SPEED)
                }
            }

            val adjustedEnd = (start + MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtMost(MAX_SUPPORTED_PLAYBACK_SPEED)
            return if (adjustedEnd - start >= MIN_PLAYBACK_SPEED_RANGE_WIDTH) {
                start..adjustedEnd
            } else {
                (end - MIN_PLAYBACK_SPEED_RANGE_WIDTH).coerceAtLeast(MIN_SUPPORTED_PLAYBACK_SPEED)..end
            }
        }

        @OptIn(SerializationOnly::class)
        @Stable
        val Default = VideoScaffoldConfig()

        @OptIn(SerializationOnly::class)
        val AllDisabled = VideoScaffoldConfig(
            fullscreenSwitchMode = FullscreenSwitchMode.ONLY_IN_CONTROLLER,
            enableFramePreview = false,
            videoEnhancementDefaultMode = VideoEnhancementDefaultMode.OFF,
            autoMarkDone = false,
            hideSelectorOnSelect = false,
            autoFullscreenOnLandscapeMode = false,
            autoPlayNext = false,
            autoSkipOpEd = false,
            autoSwitchMediaOnPlayerError = false,
            enableHighQualityAudioTimeStretch = false,
            enableExperimentalHlsSegmentFiltering = false,
        )
    }

    @Serializable
    data class PlayerVolume(val level: Float, val mute: Boolean)
}

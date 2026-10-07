package com.wynime.app.videoplayer.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import kotlin.math.roundToLong

const val TAG_MEDIA_PROGRESS_INDICATOR_TEXT = "MediaProgressIndicatorText"

@Composable
fun MediaProgressIndicatorText(
    state: PlayerProgressSliderState,
    modifier: Modifier = Modifier,
    playbackSpeedState: PlaybackSpeedControllerState? = null,
) {
    val renderedText by remember(state, playbackSpeedState) {
        derivedStateOf {
            val currentPositionMillis = if (state.isPreviewing) {
                state.displayPositionRatio.times(state.totalDurationMillis).roundToLong()
            } else {
                state.currentPositionMillis
            }
            val totalDurationMillis = state.totalDurationMillis
            val totalSecs = if (totalDurationMillis == 0L) null else totalDurationMillis / 1000
            val currentSpeed = playbackSpeedState?.currentSpeed ?: 1f
            val remainingSecs = if (kotlin.math.abs(currentSpeed - 1f) > 1e-3f) {
                totalSecs?.let { total ->
                    ((total - currentPositionMillis / 1000).coerceAtLeast(0) / currentSpeed.coerceIn(0.01f, 100f)).roundToLong()
                }
            } else {
                null
            }
            RenderedProgressText(
                text = renderSeconds(currentPositionMillis / 1000, totalSecs, remainingSecs),
                reserve = renderSecondsReserve(totalSecs, includeRemaining = remainingSecs != null),
            )
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(renderedText.reserve, Modifier.alpha(0f))
        Text(
            text = renderedText.text,
            style = LocalTextStyle.current.copy(
                color = Color.DarkGray,
                drawStyle = Stroke(
                    miter = 3f,
                    width = 2f,
                    join = StrokeJoin.Round,
                ),
            ),
        )
        Text(
            text = renderedText.text,
            Modifier.testTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT),
        )
    }
}

private data class RenderedProgressText(
    val text: String,
    val reserve: String,
)

@Stable
internal fun renderSecondsReserve(
    totalSecs: Long?,
    includeRemaining: Boolean,
): String {

    val (base, remaining) = if (totalSecs != null && totalSecs >= 3600) {
        "88:88:88 / 88:88:88" to " (-88:88:88)"
    } else {
        "88:88 / 88:88" to " (-88:88)"
    }
    return if (includeRemaining) base + remaining else base
}

@Stable
internal fun renderSeconds(current: Long, total: Long?, remainingSecs: Long? = null): String {
    if (total == null) {
        return "00:${current.fixToString(2)} / 00:00"
    }
    val base = if (current < 60 && total < 60) {
        "00:${current.fixToString(2)} / 00:${total.fixToString(2)}"
    } else if (current < 3600 && total < 3600) {
        val startM = (current / 60).fixToString(2)
        val startS = (current % 60).fixToString(2)
        val endM = (total / 60).fixToString(2)
        val endS = (total % 60).fixToString(2)
        """$startM:$startS / $endM:$endS"""
    } else {
        val startH = (current / 3600).fixToString(2)
        val startM = (current % 3600 / 60).fixToString(2)
        val startS = (current % 60).fixToString(2)
        val endH = (total / 3600).fixToString(2)
        val endM = (total % 3600 / 60).fixToString(2)
        val endS = (total % 60).fixToString(2)
        """$startH:$startM:$startS / $endH:$endM:$endS"""
    }
    if (remainingSecs == null) return base

    val remaining = if (total >= 3600) {
        val h = (remainingSecs / 3600).fixToString(2)
        val m = (remainingSecs % 3600 / 60).fixToString(2)
        val s = (remainingSecs % 60).fixToString(2)
        "$h:$m:$s"
    } else {
        val m = (remainingSecs / 60).fixToString(2)
        val s = (remainingSecs % 60).fixToString(2)
        "$m:$s"
    }
    return "$base (-$remaining)"
}

private fun Long.fixToString(length: Int, prefix: Char = '0'): String {
    val str = this.toString()
    return if (str.length >= length) {
        str
    } else {
        prefix.toString().repeat(length - str.length) + str
    }
}

package com.wynime.utils.selectorworkflow.draw

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wynime.utils.selectorworkflow.ChipTone
import com.wynime.utils.selectorworkflow.ClockTone
import com.wynime.utils.selectorworkflow.LineTone
import com.wynime.utils.selectorworkflow.RequestTone
import com.wynime.utils.selectorworkflow.WindowTone

@Immutable
data class WorkflowPalette(

    val sourceColors: List<Color>,

    val surfaceLow: Color,

    val surfaceHigh: Color,

    val trackline: Color,

    val outlineVariant: Color,

    val outline: Color,

    val focus: Color,

    val requestIcon: Color,

    val mark: Color,

    val success: Color,

    val error: Color,

    val highlight: Color,
) {
    fun source(index: Int): Color = sourceColors[index.mod(sourceColors.size)]

    fun chip(tone: ChipTone, sourceIndex: Int): Color = when (tone) {
        ChipTone.Source -> source(sourceIndex)
        ChipTone.Selected -> success
        ChipTone.Failed -> error
    }

    fun link(tone: LineTone, sourceIndex: Int): Color = when (tone) {
        LineTone.Source -> source(sourceIndex)
        LineTone.Cached -> success
    }

    fun windowStroke(tone: WindowTone): Color = when (tone) {
        WindowTone.Closed -> trackline
        WindowTone.Open -> outline
        WindowTone.Failed -> error
    }

    fun clock(tone: ClockTone): Color = when (tone) {
        ClockTone.Running -> outlineVariant
        ClockTone.Stopped -> success
        ClockTone.Expired -> error
    }

    fun clockHand(tone: ClockTone): Color = when (tone) {
        ClockTone.Running -> focus
        ClockTone.Stopped -> success
        ClockTone.Expired -> error
    }

    fun requestBar(tone: RequestTone): Color = when (tone) {
        RequestTone.Idle -> outlineVariant
        RequestTone.Hit -> success
    }

    fun rowIcon(tone: RequestTone): Color = when (tone) {
        RequestTone.Idle -> requestIcon
        RequestTone.Hit -> success
    }

    companion object {

        val DefaultSuccess = Color(0xFF5FD48F)
        val DefaultSuccessLight = Color(0xFF1E6F42)

        val DefaultHighlight = Color(0xFFEBD08F)
        val DefaultHighlightLight = Color(0xFF8A7439)
    }
}

@Composable
fun rememberWorkflowPalette(
    success: Color = if (MaterialTheme.colorScheme.surface.luminanceIsDark()) {
        WorkflowPalette.DefaultSuccess
    } else {
        WorkflowPalette.DefaultSuccessLight
    },
    highlight: Color = if (MaterialTheme.colorScheme.surface.luminanceIsDark()) {
        WorkflowPalette.DefaultHighlight
    } else {
        WorkflowPalette.DefaultHighlightLight
    },
): WorkflowPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme, success, highlight) {
        WorkflowPalette(
            sourceColors = listOf(scheme.primary, scheme.secondary, scheme.tertiary),
            surfaceLow = scheme.surfaceContainerLow,
            surfaceHigh = scheme.surfaceContainerHigh,
            trackline = scheme.surfaceContainerHighest,
            outlineVariant = scheme.outlineVariant,
            outline = scheme.outline,
            focus = scheme.onSurface,
            requestIcon = scheme.secondaryContainer,
            mark = scheme.surface,
            success = success,
            error = scheme.error,
            highlight = highlight,
        )
    }
}

fun workflowPaletteOf(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    surface: Color,
    surfaceContainerLow: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    outline: Color,
    outlineVariant: Color,
    onSurface: Color,
    secondaryContainer: Color,
    error: Color,
    success: Color = WorkflowPalette.DefaultSuccess,
    highlight: Color = WorkflowPalette.DefaultHighlight,
): WorkflowPalette = WorkflowPalette(
    sourceColors = listOf(primary, secondary, tertiary),
    surfaceLow = surfaceContainerLow,
    surfaceHigh = surfaceContainerHigh,
    trackline = surfaceContainerHighest,
    outlineVariant = outlineVariant,
    outline = outline,
    focus = onSurface,
    requestIcon = secondaryContainer,
    mark = surface,
    success = success,
    error = error,
    highlight = highlight,
)

internal inline fun <T> blendTone(from: T, to: T, blend: Float, color: (T) -> Color): Color =
    if (from == to || blend >= 1f) color(to) else lerp(color(from), color(to), blend.coerceIn(0f, 1f))

private fun Color.luminanceIsDark(): Boolean = red * 0.299f + green * 0.587f + blue * 0.114f < 0.5f

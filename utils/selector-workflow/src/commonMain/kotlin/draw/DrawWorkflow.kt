package com.wynime.utils.selectorworkflow.draw

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import com.wynime.utils.selectorworkflow.ClockId
import com.wynime.utils.selectorworkflow.RippleTarget
import com.wynime.utils.selectorworkflow.SelectorWorkflowState
import kotlin.math.min

fun DrawScope.drawSelectorWorkflow(
    state: SelectorWorkflowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,

    textMeasurer: TextMeasurer? = null,

    readoutStyle: TextStyle = DefaultReadoutStyle,
) {
    val factor = min(size.width / layout.canvasSize.width, size.height / layout.canvasSize.height)
    if (factor <= 0f) return
    val dx = (size.width - layout.canvasSize.width * factor) / 2f
    val dy = (size.height - layout.canvasSize.height * factor) / 2f

    translate(dx, dy) {
        scale(factor, factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawContent(state, layout, palette, textMeasurer, readoutStyle)
        }
    }
}

private fun DrawScope.drawContent(
    state: SelectorWorkflowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    textMeasurer: TextMeasurer?,
    readoutStyle: TextStyle,
) {

    drawBrowserWindow(state.window, layout, palette)
    drawRequestList(
        state.requestRows, state.scroll,
        state.ripples.filter { it.target == RippleTarget.RequestRow },
        layout, palette,
    )

    val intercept = state.clocks.getValue(ClockId.InterceptBudget)
    val overlay = layout.interceptOverlay(intercept.elapsedSeconds)
    drawClockOverlay(intercept, overlay.bounds, layout, palette)
    drawClock(
        intercept,
        overlay.clockCenter, overlay.readoutAnchor, layout, palette,
        textMeasurer, readoutStyle,
    )

    drawResultContainer(layout, palette)
    state.results.forEach { drawResultChip(it, layout, palette) }
    state.ripples.filter { it.target == RippleTarget.Result }
        .forEach { drawResultRipple(it, layout, palette) }
    state.cursors.forEach { drawCursor(it, layout, palette) }

    val (handoffFrom, handoffTo) = layout.handoffSegment
    drawProgressLine(
        line = state.handoff,
        from = handoffFrom,
        to = handoffTo,
        color = palette.success,
        layout = layout,
        palette = palette,
        drawTrack = false,
    )

    state.sourceLinks.forEachIndexed { index, line ->
        val (from, to) = layout.linkSegments[index]
        val color = blendTone(line.previousTone, line.tone, line.toneBlend) { palette.link(it, index) }
        drawProgressLine(line, from, to, color, layout, palette)
    }
    state.sourceNodes.forEach { drawSourceNode(it, layout, palette, state.time) }
    state.ripples.filter { it.target == RippleTarget.SourceNode }
        .forEach { drawSourceRipple(it, layout, palette) }
    drawClock(
        state.clocks.getValue(ClockId.PriorityWait),
        layout.priorityClockCenter, layout.priorityReadoutAnchor, layout, palette,
        textMeasurer, readoutStyle,
    )

    state.highlights.forEach { drawHighlight(layout.highlight(it), layout, palette, state.time) }
}

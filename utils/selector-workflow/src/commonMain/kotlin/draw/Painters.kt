package com.wynime.utils.selectorworkflow.draw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wynime.utils.selectorworkflow.ClockState
import com.wynime.utils.selectorworkflow.CursorState
import com.wynime.utils.selectorworkflow.LineState
import com.wynime.utils.selectorworkflow.RequestIcon
import com.wynime.utils.selectorworkflow.RequestRowState
import com.wynime.utils.selectorworkflow.ResultChipState
import com.wynime.utils.selectorworkflow.RippleState
import com.wynime.utils.selectorworkflow.ScrollState
import com.wynime.utils.selectorworkflow.SourceNodeState
import com.wynime.utils.selectorworkflow.WindowState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration

private val HaloPeriod: Duration = Duration.parse("1.1s")

internal fun DrawScope.drawSourceNode(
    node: SourceNodeState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    time: Duration,
) {
    val m = layout.metrics
    val center = layout.nodeCenters[node.index]
    val color = palette.source(node.index)

    if (node.pulsing) {
        val phase = (time.inWholeMilliseconds.toFloat() / HaloPeriod.inWholeMilliseconds) % 1f
        drawCircle(
            color = color,
            radius = m.haloRadius * (HALO_FROM + (HALO_TO - HALO_FROM) * phase),
            center = center,
            alpha = (HALO_ALPHA * (1f - phase)).coerceIn(0f, 1f),
            style = Stroke(width = m.strokeMedium),
        )
    }
    drawCircle(color = color, radius = m.nodeRadius, center = center, alpha = node.alpha)
    if (node.priority) {
        drawPath(diamondPath(center, m.priorityNodeMarkRadius), palette.mark, alpha = node.alpha)
    }
}

private const val HALO_FROM = 0.55f
private const val HALO_TO = 1.2f
private const val HALO_ALPHA = 0.55f

internal fun DrawScope.drawProgressLine(
    line: LineState,
    from: Offset,
    to: Offset,
    color: Color,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    drawTrack: Boolean = true,
) {
    val m = layout.metrics
    if (drawTrack) {
        drawLine(palette.trackline, from, to, strokeWidth = m.strokeMedium, cap = StrokeCap.Round)
    }
    if (line.alpha <= 0.001f || line.progress <= 0.001f) return
    val head = Offset(
        from.x + (to.x - from.x) * line.progress,
        from.y + (to.y - from.y) * line.progress,
    )
    drawLine(color, from, head, strokeWidth = m.strokeBold, cap = StrokeCap.Round, alpha = line.alpha)
}

internal fun DrawScope.drawResultChip(
    chip: ResultChipState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (chip.alpha <= 0.001f) return
    val m = layout.metrics
    val rect = layout.cells[chip.cell]
    val color = blendTone(chip.previousTone, chip.tone, chip.toneBlend) { palette.chip(it, chip.key.source) }

    scaleAbout(chip.scale, rect.center) {
        drawRoundRect(
            color = color,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(rect.height / 2f),
            alpha = chip.alpha,
        )
        drawChipMarks(chip, rect, layout, palette)
    }
}

private fun DrawScope.drawChipMarks(
    chip: ResultChipState,
    rect: Rect,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (!chip.candidate && !chip.priority) return
    val m = layout.metrics
    val center = rect.center
    val both = chip.candidate && chip.priority

    val half = (m.priorityMarkRadius * 2 + m.markGap + m.candidateDotRadius * 2) / 2f
    val priorityDx = if (both) -half + m.priorityMarkRadius else 0f
    val candidateDx = if (both) half - m.candidateDotRadius else 0f

    if (chip.priority) {
        drawPath(
            path = diamondPath(Offset(center.x + priorityDx, center.y), m.priorityMarkRadius),
            color = palette.mark,
            alpha = chip.alpha,
        )
    }
    if (chip.candidate) {
        drawCircle(
            color = palette.mark,
            radius = m.candidateDotRadius,
            center = Offset(center.x + candidateDx, center.y),
            alpha = chip.alpha,
        )
    }
}

internal fun DrawScope.drawResultRipple(
    ripple: RippleState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (ripple.alpha <= 0.001f) return
    val m = layout.metrics
    val rect = layout.cells.getOrNull(ripple.index) ?: return
    scaleAbout(ripple.scale, rect.center) {
        drawRoundRect(
            color = palette.success,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(rect.height / 2f),
            alpha = ripple.alpha,
            style = Stroke(width = m.strokeMedium),
        )
    }
}

private fun DrawScope.drawRequestRipple(
    ripple: RippleState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (ripple.alpha <= 0.001f) return
    val m = layout.metrics
    val center = Offset(layout.rowIconCenterX, layout.rowCenterY(ripple.index))
    drawCircle(
        color = palette.success,
        radius = m.rowIconRadius * ripple.scale,
        center = center,
        alpha = ripple.alpha,
        style = Stroke(width = m.strokeMedium),
    )
}

internal fun DrawScope.drawSourceRipple(
    ripple: RippleState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (ripple.alpha <= 0.001f) return
    val m = layout.metrics
    val center = layout.nodeCenters.getOrNull(ripple.index) ?: return
    drawCircle(
        color = palette.success,
        radius = m.nodeRadius * ripple.scale,
        center = center,
        alpha = ripple.alpha,
        style = Stroke(width = m.strokeMedium),
    )
}

internal fun DrawScope.drawCursor(
    cursor: CursorState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (cursor.alpha <= 0.001f) return
    val m = layout.metrics
    val center = layout.cellCenter(cursor.cell)
    val size = Size(m.chipWidth + m.cursorInflate * 2, m.chipHeight + m.cursorInflate * 2)
    val topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f)
    val radius = CornerRadius(size.height / 2f)

    val stroke = cursor.owner?.let { palette.source(it) } ?: palette.focus

    drawRoundRect(palette.focus, topLeft, size, radius, alpha = cursor.alpha * FOCUS_LAYER_ALPHA)
    drawRoundRect(stroke, topLeft, size, radius, alpha = cursor.alpha, style = Stroke(m.strokeThin))
}

private const val FOCUS_LAYER_ALPHA = 0.08f

internal fun DrawScope.drawClock(
    clock: ClockState,
    center: Offset,
    readoutAnchor: Offset,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    textMeasurer: TextMeasurer?,
    readoutStyle: TextStyle,
) {
    if (clock.alpha <= 0.001f) return
    val m = layout.metrics
    val r = m.clockRadius
    val ring = blendTone(clock.previousTone, clock.tone, clock.toneBlend, palette::clock)
    val hand = blendTone(clock.previousTone, clock.tone, clock.toneBlend, palette::clockHand)

    drawCircle(palette.surfaceLow, radius = r, center = center, alpha = clock.alpha)
    if (clock.overlayAlpha > 0.001f) {
        drawCircle(palette.error, radius = r, center = center, alpha = clock.alpha * clock.overlayAlpha)
    }
    drawCircle(ring, radius = r, center = center, alpha = clock.alpha, style = Stroke(m.strokeThin))

    drawCircle(
        palette.outline, radius = TICK_RADIUS, alpha = clock.alpha,
        center = Offset(center.x, center.y - r + TICK_INSET),
    )

    val angle = (clock.handDegrees - 90f) * PI.toFloat() / 180f
    val length = r - TICK_INSET
    drawLine(
        color = hand,
        start = center,
        end = Offset(center.x + cos(angle) * length, center.y + sin(angle) * length),
        strokeWidth = m.strokeMedium,
        cap = StrokeCap.Round,
        alpha = clock.alpha,
    )

    if (textMeasurer != null && clock.hasReadout) {
        drawScaledText(
            textMeasurer = textMeasurer,
            text = formatSeconds(clock.elapsedSeconds),
            anchor = readoutAnchor,
            targetHeight = m.readoutHeight,
            color = hand,
            alpha = clock.alpha,
            style = readoutStyle,
        )
    }
}

internal fun formatSeconds(seconds: Float): String {
    val tenths = (seconds * 10f + 0.5f).toInt().coerceAtLeast(0)
    return "${tenths / 10}.${tenths % 10}s"
}

private fun DrawScope.drawScaledText(
    textMeasurer: TextMeasurer,
    text: String,
    anchor: Offset,
    targetHeight: Float,
    color: Color,
    alpha: Float,
    style: TextStyle,
) {
    val measured = textMeasurer.measure(text, style)
    val height = measured.size.height.toFloat()
    if (height <= 0f) return
    val k = targetHeight / height
    translate(anchor.x, anchor.y - targetHeight / 2f) {
        scale(k, k, pivot = Offset.Zero) {
            drawText(measured, color = color, alpha = alpha)
        }
    }
}

val DefaultReadoutStyle = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.sp,
)

private const val TICK_RADIUS = 0.8f
private const val TICK_INSET = 1.4f

internal fun DrawScope.drawClockOverlay(
    clock: ClockState,
    rect: Rect,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (clock.alpha <= 0.001f) return
    val m = layout.metrics
    val radius = CornerRadius(m.overlayRadius)
    drawRoundRect(palette.surfaceHigh, rect.topLeft, rect.size, radius, alpha = clock.alpha)
    drawRoundRect(
        color = palette.outlineVariant,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
        alpha = clock.alpha,
        style = Stroke(width = m.hairline),
    )
}

internal fun DrawScope.drawBrowserWindow(
    window: WindowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    val m = layout.metrics
    val rect = layout.window
    val radius = CornerRadius(m.windowRadius)

    drawRoundRect(palette.surfaceHigh, rect.topLeft, rect.size, radius)
    drawRoundRect(
        blendTone(window.previousTone, window.tone, window.toneBlend, palette::windowStroke),
        rect.topLeft, rect.size, radius,
        style = Stroke(m.strokeMedium),
    )
    layout.chromeDots.forEach {
        drawCircle(palette.outlineVariant, radius = m.chromeDotRadius, center = it)
    }
    drawRoundRect(
        palette.outlineVariant,
        layout.addressBar.topLeft,
        layout.addressBar.size,
        CornerRadius(layout.addressBar.height / 2f),
    )
    drawRoundRect(
        palette.surfaceLow,
        layout.listViewport.topLeft,
        layout.listViewport.size,
        CornerRadius(m.windowInset),
    )
}

internal fun DrawScope.drawRequestList(
    rows: List<RequestRowState>,
    scroll: ScrollState,
    ripples: List<RippleState>,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    val m = layout.metrics
    val viewport = layout.listViewport

    clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
        translate(top = -scroll.rowOffset * m.rowHeight) {
            rows.forEach { row -> drawRequestRow(row, layout, palette) }
            ripples.forEach { drawRequestRipple(it, layout, palette) }
        }
    }
}

private fun DrawScope.drawRequestRow(
    row: RequestRowState,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
) {
    if (row.alpha <= 0.001f) return
    val m = layout.metrics
    val cy = layout.rowCenterY(row.index)
    val iconCenter = Offset(layout.rowIconCenterX, cy)

    val iconColor = blendTone(row.previousTone, row.tone, row.toneBlend, palette::rowIcon)
    val barColor = blendTone(row.previousTone, row.tone, row.toneBlend, palette::requestBar)

    scaleAbout(row.iconScale, iconCenter) {
        when (row.icon) {
            RequestIcon.Request -> drawCircle(iconColor, m.rowIconRadius, iconCenter, alpha = row.alpha)
            RequestIcon.Media -> drawPlayTriangle(
                center = iconCenter,
                radius = m.rowIconRadius,
                corner = m.rowIconCornerRadius,
                color = iconColor,
                alpha = row.alpha,
            )
        }
    }
    val barWidth = layout.rowBarWidths.getOrElse(row.index) { m.chipWidth }
    drawRoundRect(
        color = barColor,
        topLeft = Offset(layout.rowBarLeft, cy - m.rowBarHeight / 2f),
        size = Size(barWidth, m.rowBarHeight),
        cornerRadius = CornerRadius(m.rowBarHeight / 2f),
        alpha = row.alpha,
    )
}

private val BreathPeriod: Duration = Duration.parse("1.8s")

internal fun DrawScope.drawHighlight(
    highlight: Highlight,
    layout: WorkflowLayout,
    palette: WorkflowPalette,
    time: Duration,
) {
    val phase = (time.inWholeMilliseconds.toFloat() / BreathPeriod.inWholeMilliseconds) % 1f
    val alpha = (1f - cos(phase * 2f * PI.toFloat())) / 2f
    if (alpha <= 0.001f) return
    drawRoundRect(
        color = palette.highlight,
        topLeft = highlight.bounds.topLeft,
        size = highlight.bounds.size,
        cornerRadius = CornerRadius(highlight.cornerRadius),
        alpha = alpha,
        style = Stroke(width = layout.metrics.strokeBold),
    )
}

internal fun DrawScope.drawResultContainer(layout: WorkflowLayout, palette: WorkflowPalette) {
    val m = layout.metrics
    val rect = layout.container
    val radius = CornerRadius(m.containerRadius)
    drawRoundRect(palette.surfaceLow, rect.topLeft, rect.size, radius)
    drawRoundRect(palette.outlineVariant, rect.topLeft, rect.size, radius, style = Stroke(m.strokeMedium))
}

private inline fun DrawScope.scaleAbout(factor: Float, pivot: Offset, crossinline block: DrawScope.() -> Unit) {
    if (factor == 1f) {
        block()
    } else {
        scale(factor, factor, pivot) { block() }
    }
}

private fun diamondPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius, center.y)
    lineTo(center.x, center.y + radius)
    lineTo(center.x - radius, center.y)
    close()
}

private fun DrawScope.drawPlayTriangle(
    center: Offset,
    radius: Float,
    corner: Float,
    color: Color,
    alpha: Float,
) {
    val r = (radius - corner).coerceAtLeast(0.1f)
    val path = Path().apply {
        moveTo(center.x - r * 0.85f, center.y - r)
        lineTo(center.x + r * 1.15f, center.y)
        lineTo(center.x - r * 0.85f, center.y + r)
        close()
    }
    drawPath(path, color, alpha = alpha)
    drawPath(
        path, color, alpha = alpha,
        style = Stroke(width = corner * 2, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

package com.wynime.utils.selectorworkflow.draw

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.wynime.utils.selectorworkflow.HighlightRegion
import com.wynime.utils.selectorworkflow.SelectorWorkflowConfig
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Immutable
data class WorkflowMetrics(

    val chipWidth: Float = 23f,
    val chipHeight: Float = 10f,
    val chipGapX: Float = 6f,
    val chipGapY: Float = 6f,

    val containerPadding: Float = 6f,
    val containerRadius: Float = 10f,

    val nodeRadius: Float = 5f,
    val haloRadius: Float = 7f,
    val nodeSpacing: Float = 18f,

    val linkLength: Float = 28f,

    val handoffLength: Float = 24f,

    val windowWidth: Float = 110f,
    val windowHeight: Float = 70f,
    val windowRadius: Float = 10f,
    val windowInset: Float = 8f,
    val titleBarHeight: Float = 21f,
    val chromeDotRadius: Float = 3.5f,

    val rowHeight: Float = 10f,
    val rowIconRadius: Float = 3.5f,

    val rowIconCornerRadius: Float = 1.1f,
    val rowBarHeight: Float = 4f,

    val clockRadius: Float = 6f,

    val readoutHeight: Float = 8f,
    val readoutGap: Float = 3f,

    val readoutCharWidth: Float = 0.5f,

    val overlayInset: Float = 3f,
    val overlayPadding: Float = 2.5f,
    val overlayRadius: Float = 4f,

    val cursorInflate: Float = 2f,

    val candidateDotRadius: Float = 2f,
    val priorityMarkRadius: Float = 2.2f,

    val priorityNodeMarkRadius: Float = 2.6f,
    val markGap: Float = 2f,

    val highlightInset: Float = 3f,

    val sourcesHighlightInset: Float = 6f,

    val sourcesHighlightRadius: Float = 6f,

    val outerPadding: Float = 8f,

    val hairline: Float = 1f,
    val strokeThin: Float = 1.2f,
    val strokeMedium: Float = 1.5f,
    val strokeBold: Float = 2.5f,
) {
    companion object {
        val Default = WorkflowMetrics()
    }
}

@Immutable
class WorkflowLayout internal constructor(
    val config: SelectorWorkflowConfig,
    val metrics: WorkflowMetrics,
    val canvasSize: Size,

    val nodeCenters: List<Offset>,

    val linkSegments: List<Pair<Offset, Offset>>,
    val container: Rect,

    val cells: List<Rect>,

    val handoffSegment: Pair<Offset, Offset>,
    val window: Rect,
    val chromeDots: List<Offset>,
    val addressBar: Rect,

    val listViewport: Rect,

    val rowBarWidths: List<Float>,
    val priorityClockCenter: Offset,

    val priorityReadoutAnchor: Offset,

    val interceptOverlayAnchor: Offset,
) {

    fun interceptOverlay(readoutSeconds: Float): ClockOverlay = with(metrics) {
        val readoutW = readoutTextWidth(readoutSeconds, readoutHeight, readoutCharWidth)
        val height = clockRadius * 2 + overlayPadding * 2
        val width = overlayPadding * 2 + clockRadius * 2 + readoutGap + readoutW
        val bounds = Rect(
            interceptOverlayAnchor.x - width,
            interceptOverlayAnchor.y,
            interceptOverlayAnchor.x,
            interceptOverlayAnchor.y + height,
        )
        val clockCenter = Offset(bounds.left + overlayPadding + clockRadius, bounds.center.y)
        ClockOverlay(
            bounds = bounds,
            clockCenter = clockCenter,
            readoutAnchor = Offset(clockCenter.x + clockRadius + readoutGap, bounds.center.y),
            readoutWidth = readoutW,
        )
    }

    fun highlight(region: HighlightRegion): Highlight = with(metrics) {
        when (region) {
            HighlightRegion.Sources -> Highlight(
                bounds = Rect(
                    left = nodeCenters.minOf { it.x } - haloRadius - sourcesHighlightInset,
                    top = nodeCenters.minOf { it.y } - nodeRadius - sourcesHighlightInset,

                    right = container.left + containerPadding,
                    bottom = nodeCenters.maxOf { it.y } + nodeRadius + sourcesHighlightInset,
                ),
                cornerRadius = sourcesHighlightRadius,
            )

            HighlightRegion.Results -> Highlight(
                bounds = container.inflate(highlightInset),
                cornerRadius = containerRadius + highlightInset,
            )

            HighlightRegion.Resolve -> Highlight(
                bounds = window.inflate(highlightInset),
                cornerRadius = windowRadius + highlightInset,
            )
        }
    }

    fun rowCenterY(index: Int): Float {
        val inset = (listViewport.height - config.resolve.visibleRows * metrics.rowHeight) / 2f
        return listViewport.top + inset + metrics.rowHeight * (index + 0.5f)
    }

    val rowIconCenterX: Float get() = listViewport.left + metrics.windowInset + metrics.rowIconRadius * 0.5f
    val rowBarLeft: Float get() = listViewport.left + metrics.windowInset * 2f + metrics.rowIconRadius

    fun cellCenter(cell: Float): Offset {
        if (cells.isEmpty()) return container.center
        val lo = floor(cell).toInt().coerceIn(0, cells.lastIndex)
        val hi = ceil(cell).toInt().coerceIn(0, cells.lastIndex)
        val f = (cell - lo).coerceIn(0f, 1f)
        val a = cells[lo].center
        val b = cells[hi].center
        return Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    companion object {

        fun of(
            config: SelectorWorkflowConfig,
            metrics: WorkflowMetrics = WorkflowMetrics.Default,
        ): WorkflowLayout = with(metrics) {
            val columns = config.gridColumns
            val rows = config.gridRows.coerceAtLeast(1)

            val containerWidth = containerPadding * 2 + columns * chipWidth + (columns - 1) * chipGapX
            val containerHeight = containerPadding * 2 + rows * chipHeight + (rows - 1) * chipGapY

            val contentHeight = max(containerHeight, windowHeight)
            val height = contentHeight + outerPadding * 2

            val nodeColumnX = outerPadding + haloRadius
            val containerLeft = nodeColumnX + haloRadius + linkLength
            val windowLeft = containerLeft + containerWidth + handoffLength
            val width = windowLeft + windowWidth + outerPadding

            val containerTop = outerPadding + (contentHeight - containerHeight) / 2f
            val container = Rect(
                containerLeft, containerTop,
                containerLeft + containerWidth, containerTop + containerHeight,
            )
            val windowTop = outerPadding + (contentHeight - windowHeight) / 2f
            val window = Rect(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight)

            val n = config.sources.size
            val axis = container.center.y
            val nodeCenters = List(n) { i ->
                Offset(nodeColumnX, axis + (i - (n - 1) / 2f) * nodeSpacing)
            }

            val straightTop = container.top + containerRadius
            val straightBottom = container.bottom - containerRadius
            val linkSegments = nodeCenters.map { c ->
                val y = c.y.coerceIn(straightTop, straightBottom)
                Offset(c.x + nodeRadius + 1f, c.y) to Offset(container.left, y)
            }

            val cells = List(config.results.size) { index ->
                val col = index % columns
                val row = index / columns
                val left = container.left + containerPadding + col * (chipWidth + chipGapX)
                val top = container.top + containerPadding + row * (chipHeight + chipGapY)
                Rect(left, top, left + chipWidth, top + chipHeight)
            }

            val listViewport = Rect(
                window.left + windowInset,
                window.top + titleBarHeight,
                window.right - windowInset,
                window.top + titleBarHeight + config.resolve.visibleRows * rowHeight + 2f,
            )

            val titleBarCenterY = window.top + titleBarHeight / 2f
            val chromeDots = List(CHROME_DOT_COUNT) { i ->
                Offset(
                    listViewport.left + chromeDotRadius + i * (chromeDotRadius * 2 + CHROME_DOT_GAP),
                    titleBarCenterY,
                )
            }

            val addressLeft = chromeDots.last().x + chromeDotRadius + ADDRESS_BAR_GAP
            val addressBar = Rect(
                addressLeft,
                titleBarCenterY - chromeDotRadius,
                max(listViewport.right, addressLeft + chromeDotRadius * 2),
                titleBarCenterY + chromeDotRadius,
            )

            val overlayAnchor = Offset(
                listViewport.right - overlayInset,
                listViewport.top + overlayInset,
            )

            val barSpan = listViewport.right - (listViewport.left + windowInset * 2f + rowIconRadius) - windowInset
            val rowBarWidths = List(config.resolve.requestCount) { i ->
                barSpan * (BAR_MIN_RATIO + (BAR_MAX_RATIO - BAR_MIN_RATIO) * pseudoRandom(i))
            }

            WorkflowLayout(
                config = config,
                metrics = metrics,
                canvasSize = Size(width, height),
                nodeCenters = nodeCenters,
                linkSegments = linkSegments,
                container = container,
                cells = cells,
                handoffSegment = Offset(container.right, axis) to Offset(window.left, axis),
                window = window,
                chromeDots = chromeDots,
                addressBar = addressBar,
                listViewport = listViewport,
                rowBarWidths = rowBarWidths,
                priorityClockCenter = Offset(nodeColumnX, outerPadding),
                priorityReadoutAnchor = Offset(nodeColumnX + clockRadius + readoutGap, outerPadding),
                interceptOverlayAnchor = overlayAnchor,
            )
        }

        internal fun readoutTextWidth(seconds: Float, height: Float, charWidth: Float): Float =
            formatSeconds(seconds).length * height * charWidth

        private const val CHROME_DOT_COUNT = 3
        private const val CHROME_DOT_GAP = 2f
        private const val ADDRESS_BAR_GAP = 6f
        private const val BAR_MIN_RATIO = 0.52f
        private const val BAR_MAX_RATIO = 0.96f

        private fun pseudoRandom(index: Int): Float = ((index * 37 + 11) % 23) / 22f
    }
}

@Immutable
data class Highlight(val bounds: Rect, val cornerRadius: Float)

@Immutable
data class ClockOverlay(
    val bounds: Rect,
    val clockCenter: Offset,
    val readoutAnchor: Offset,
    val readoutWidth: Float,
)

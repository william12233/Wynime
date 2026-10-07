package com.wynime.utils.selectorworkflow

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.wynime.utils.selectorworkflow.draw.WorkflowLayout
import com.wynime.utils.selectorworkflow.draw.WorkflowMetrics
import com.wynime.utils.selectorworkflow.draw.drawSelectorWorkflow
import com.wynime.utils.selectorworkflow.draw.workflowPaletteOf
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

class WorkflowRenderTest {

    private val palette = workflowPaletteOf(
        primary = Color(0xFFD0BCFF),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8),
        surface = Color(0xFF141218),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        onSurface = Color(0xFFE6E0E9),
        secondaryContainer = Color(0xFF4A4458),
        error = Color(0xFFFFB4AB),
    )

    private fun render(
        timeline: SelectorWorkflowTimeline,
        time: Duration,
        width: Int = 508,
        height: Int = 172,
    ): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val layout = WorkflowLayout.of(timeline.config, WorkflowMetrics.Default)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat()),
        ) {
            drawRect(Color(0xFF141218))
            drawSelectorWorkflow(timeline.sampleAt(time), layout, palette)
        }
        return bitmap
    }

    private fun ImageBitmap.distinctColors(): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.toHashSet().size
    }

    private fun IntArray.toHashSet(): HashSet<Int> = HashSet<Int>(size).also { set -> forEach { set.add(it) } }

    @Test
    fun `every frame of every switch combination renders without throwing`() {
        val combinations = listOf(
            SelectMode.WaitAll to false,
            SelectMode.Eager to false,
            SelectMode.WaitAll to true,
            SelectMode.Eager to true,
        )
        val outcomeSets = listOf(
            listOf(ResolveOutcome.Hit),
            listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback),
        )
        for ((mode, prio) in combinations) {
            for (outcomes in outcomeSets) {
                for (cached in listOf(false, true)) {
                    val base = SelectorWorkflowPresets.threeSources(
                        mode = mode,
                        priorityWait = if (prio) kotlin.time.Duration.parse("5s") else null,
                        resolveOutcomes = outcomes,
                    )

                    val config = base.copy(
                        selection = base.selection.copy(demoBothPriorityPaths = prio),
                        cachedQuery = cached,
                    ).withFeatureHighlights()
                    val timeline = config.buildTimeline()
                    var t = Duration.ZERO
                    val step = timeline.duration / 60.0
                    while (t <= timeline.duration) {
                        render(timeline, t, width = 254, height = 86)
                        t += step
                    }
                }
            }
        }
    }

    @Test
    fun `the picture is not blank at any stage`() {
        val config = SelectorWorkflowPresets.threeSources(
            resolveOutcomes = listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback),
        )
        val timeline = config.buildTimeline()

        val samples = listOf(0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f)
        samples.forEach { fraction ->
            val image = render(timeline, timeline.duration * fraction.toDouble())
            assertTrue(
                image.distinctColors() > MIN_COLORS,
                "第 $fraction 处几乎是空的, 只有 ${image.distinctColors()} 种颜色",
            )
        }
    }

    @Test
    fun `scaling to a different size keeps the picture proportional`() {
        val timeline = SelectorWorkflowPresets.threeSources().buildTimeline()
        val at = timeline.duration * 0.5
        val small = render(timeline, at, width = 254, height = 86)
        val large = render(timeline, at, width = 1016, height = 344)
        assertTrue(small.distinctColors() > MIN_COLORS)
        assertTrue(
            large.distinctColors() > small.distinctColors(),
            "放大之后细节该更多, 不是把小图拉伸",
        )
    }

    private companion object {

        const val MIN_COLORS = 40
    }
}

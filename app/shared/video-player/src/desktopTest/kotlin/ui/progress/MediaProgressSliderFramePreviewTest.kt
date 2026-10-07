package com.wynime.app.videoplayer.ui.progress

import androidx.collection.floatListOf
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.MediaCacheProgressInfo
import com.wynime.app.ui.framework.exists
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import org.openani.mediamp.features.PreviewFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaProgressSliderFramePreviewTest {

    private fun createSliderState() = PlayerProgressSliderState(
        currentPositionMillis = { 30_000L },
        totalDurationMillis = { 100_000L },
        chapters = { emptyList() },
        onPreview = {},
        onPreviewFinished = {},
    )

    private fun solidFrame(color: Color, width: Int = 160, height: Int = 90): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.color = color })
        return bitmap
    }

    @Test
    fun `hover shows frame preview in popup`() = runWynimeComposeUiTest {
        val frame = solidFrame(Color.Green)
        val requestedPositions = mutableListOf<Long>()
        val framePreview = MediaProgressFramePreviewState(
            fetchFrame = { positionMillis ->
                requestedPositions.add(positionMillis)
                frame
            },
            debounceMillis = 0,
        )
        setContent {
            MediaProgressSlider(
                createSliderState(),
                cacheProgressInfoFlow = { null },
                framePreview = framePreview,
            )
        }

        waitForIdle()
        runOnUiThread {
            onNodeWithTag(TAG_PROGRESS_SLIDER).performMouseInput {
                moveTo(center)
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = 5_000) {
                onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).exists()
            }
        }
        onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP, useUnmergedTree = true).assertExists()
        assertTrue(requestedPositions.isNotEmpty(), "fetchFrame should have been called on hover")
    }

    @Test
    fun `drag requests frame at dragged position`() = runWynimeComposeUiTest {
        val frame = solidFrame(Color.Green)
        val requestedPositions = mutableListOf<Long>()
        val framePreview = MediaProgressFramePreviewState(
            fetchFrame = { positionMillis ->
                requestedPositions.add(positionMillis)
                frame
            },
            debounceMillis = 0,
        )
        setContent {
            MediaProgressSlider(
                createSliderState(),
                cacheProgressInfoFlow = { null },
                framePreview = framePreview,
            )
        }

        waitForIdle()
        runOnUiThread {
            onNodeWithTag(TAG_PROGRESS_SLIDER).performTouchInput {
                down(centerLeft)
                moveBy(Offset(width / 2f, 0f))
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = 5_000) {
                onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).exists()
            }
        }

        assertTrue(
            (requestedPositions.lastOrNull() ?: -1L) in 40_000L..60_000L,
            "requested positions $requestedPositions do not end near the dragged center",
        )
        runOnUiThread {
            onNodeWithTag(TAG_PROGRESS_SLIDER).performTouchInput {
                up()
            }
        }
    }

    @Test
    fun `uncached position does not request frame but still shows time popup`() = runWynimeComposeUiTest {
        var fetchCount = 0
        val framePreview = MediaProgressFramePreviewState(
            fetchFrame = {
                fetchCount++
                solidFrame(Color.Red)
            },
            debounceMillis = 0,
        )
        val uncachedInfo = MediaCacheProgressInfo(
            chunkWeights = floatListOf(1f),
            chunkStates = listOf(ChunkState.NONE),
        )
        setContent {
            MediaProgressSlider(
                createSliderState(),
                cacheProgressInfoFlow = { uncachedInfo },
                framePreview = framePreview,
            )
        }

        onNodeWithTag(TAG_PROGRESS_SLIDER).performMouseInput {
            moveTo(center)
        }
        waitUntil(timeoutMillis = 5_000) {
            onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP, useUnmergedTree = true).exists()
        }
        waitForIdle()
        assertTrue(
            onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).exists().not(),
            "frame should not be shown for uncached position",
        )
        assertEquals(0, fetchCount, "fetchFrame should not be called for uncached position")
    }

    @Test
    fun `no frame preview state keeps time-only popup`() = runWynimeComposeUiTest {
        setContent {
            MediaProgressSlider(
                createSliderState(),
                cacheProgressInfoFlow = { null },
                framePreview = null,
            )
        }
        onNodeWithTag(TAG_PROGRESS_SLIDER).performMouseInput {
            moveTo(center)
        }
        waitUntil(timeoutMillis = 5_000) {
            onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP, useUnmergedTree = true).exists()
        }
        assertTrue(
            onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).exists().not(),
            "frame should not be shown when framePreview is null",
        )
    }

    @Test
    fun `preview frame pixels convert to image bitmap`() {

        val frame = PreviewFrame(
            positionMillis = 0,
            width = 2,
            height = 1,
            pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
        )
        val bitmap = frame.toImageBitmap()
        val pixels = bitmap.toPixelMap()
        assertEquals(Color(0xFFFF0000), pixels[0, 0])
        assertEquals(Color(0xFF0000FF), pixels[1, 0])
    }
}

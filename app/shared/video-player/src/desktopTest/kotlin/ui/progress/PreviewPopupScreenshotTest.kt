package com.wynime.app.videoplayer.ui.progress

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.wynime.app.ui.framework.exists
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PreviewPopupScreenshotTest {

    private fun solidFrame(color: Color, width: Int = 160, height: Int = 90): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.color = color })
        return bitmap
    }

    @Test
    fun `dump popup rendering`() = runWynimeComposeUiTest {
        val framePreview = MediaProgressFramePreviewState(
            fetchFrame = { solidFrame(Color.Green) },
            debounceMillis = 0,
        )
        setContent {
            MediaProgressSlider(
                PlayerProgressSliderState(
                    currentPositionMillis = { 30_000L },
                    totalDurationMillis = { 100_000L },
                    chapters = { emptyList() },
                    onPreview = {},
                    onPreviewFinished = {},
                ),
                cacheProgressInfoFlow = { null },
                framePreview = framePreview,
            )
        }
        waitForIdle()
        runOnUiThread {
            onNodeWithTag(TAG_PROGRESS_SLIDER).performMouseInput { moveTo(center) }
        }
        runOnIdle {
            waitUntil(timeoutMillis = 5_000) {
                onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).exists()
            }
        }

        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val popupNode = onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP, useUnmergedTree = true)
        val image = popupNode.captureToImage()
        val out = File(System.getProperty("java.io.tmpdir"), "preview-popup.png")
        ImageIO.write(image.toAwtImage(), "png", out)
        println("POPUP_PNG=${out.absolutePath} size=${image.width}x${image.height}")

        val popupBounds = popupNode.fetchSemanticsNode().boundsInWindow
        val frameBounds = onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        assertTrue(
            frameBounds.top >= popupBounds.top && frameBounds.bottom <= popupBounds.bottom &&
                frameBounds.left >= popupBounds.left && frameBounds.right <= popupBounds.right,
            "frame $frameBounds must be inside popup $popupBounds",
        )
    }
}

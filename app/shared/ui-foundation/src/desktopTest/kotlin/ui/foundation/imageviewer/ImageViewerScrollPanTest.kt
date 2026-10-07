package com.wynime.app.ui.foundation.imageviewer

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.ZoomState
import com.github.panpf.zoomimage.compose.rememberZoomState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.imageScrollPan
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.framework.runOnSwingEdt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ImageViewerScrollPanTest {
    private fun runScrollTest(block: androidx.compose.ui.test.ComposeUiTest.(ZoomState) -> Unit) = runOnSwingEdt {
        runWynimeComposeUiTest {
            lateinit var zoomState: ZoomState

            val painter = BitmapPainter(ImageBitmap(800, 600))
            setContent {
                ProvideCompositionLocalsForPreview {
                    zoomState = rememberZoomState()
                    ZoomImage(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(400.dp, 300.dp).testTag("image").imageScrollPan(zoomState.zoomable),
                        zoomState = zoomState,
                    )
                }
            }
            waitUntil(timeoutMillis = 5_000) { zoomState.zoomable.maxScale > zoomState.zoomable.minScale && zoomState.zoomable.transform.scaleX > 0f }
            block(zoomState)
        }
    }

    @Test
    fun `ctrl + wheel zooms around the pointer`() = runScrollTest { zoomState ->
        val zoomable = zoomState.zoomable
        val initial = zoomable.transform.scaleX
        onNodeWithTag("image").performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag("image").performMouseInput {
            moveTo(center)
            scroll(-3f)
        }
        onNodeWithTag("image").performKeyInput { keyUp(Key.CtrlLeft) }
        waitUntil(timeoutMillis = 5_000) { zoomable.transform.scaleX > initial + 0.01f }
        assertTrue(zoomable.transform.scaleX > initial, "scale ${zoomable.transform.scaleX} should exceed $initial")
    }

    @Test
    fun `plain wheel pans when zoomed in and does nothing at fit`() = runScrollTest { zoomState ->
        val zoomable = zoomState.zoomable
        val fitOffset = zoomable.transform.offset
        onNodeWithTag("image").performMouseInput {
            moveTo(center)
            scroll(3f)
        }
        waitForIdle()
        assertEquals(fitOffset, zoomable.transform.offset)

        onNodeWithTag("image").performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag("image").performMouseInput { scroll(-6f) }
        onNodeWithTag("image").performKeyInput { keyUp(Key.CtrlLeft) }
        waitUntil(timeoutMillis = 5_000) { zoomable.transform.scaleX > zoomable.minScale + 0.01f }
        val zoomedOffset = zoomable.transform.offset
        onNodeWithTag("image").performMouseInput { scroll(3f) }
        waitUntil(timeoutMillis = 5_000) { zoomable.transform.offset != zoomedOffset }
    }
}

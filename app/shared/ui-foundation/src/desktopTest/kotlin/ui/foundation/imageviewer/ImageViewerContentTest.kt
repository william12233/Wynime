package com.wynime.app.ui.foundation.imageviewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.Sketch
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.wynime.app.ui.foundation.IMAGE_VIEWER_TEST_TAG
import com.wynime.app.ui.foundation.LocalSketch
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.createDefaultSketch
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.Toaster
import com.wynime.app.ui.framework.WynimeComposeUiTest
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.framework.runOnSwingEdt
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.image_viewer_copied
import com.wynime.app.ui.lang.image_viewer_save_failed
import com.wynime.app.ui.lang.image_viewer_saved
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.resolve
import com.wynime.utils.ktor.asScopedHttpClient
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageViewerContentTest {
    private class Env(
        val mainScheduler: TestCoroutineScheduler,
        val sketch: Sketch,
        val url: String,
        val exportDirectory: SystemPath,
        val closeCount: AtomicInteger,
        val toasts: CopyOnWriteArrayList<String>,
    )

    private fun Env.waitUntilPumpingMain(
        test: WynimeComposeUiTest,
        timeoutMillis: Long = 5_000,
        condition: () -> Boolean,
    ) {
        test.waitUntil(timeoutMillis = timeoutMillis) {
            mainScheduler.advanceUntilIdle()
            condition()
        }
    }

    private fun runViewerTest(
        fileSaver: ImageFileSaver = ImageFileSaver { true },
        imageClipboard: ImageClipboard? = null,
        block: WynimeComposeUiTest.(Env) -> Unit,
    ) {
        val tempDirectory = SystemPaths.createTempDirectory("ani-image-viewer-content-test")
        val bytes = encodedRaster(width = 400, height = 300)
        val client = HttpClient(
            MockEngine {
                respond(content = bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
            },
        )
        val sketch = createDefaultSketch(
            PlatformContext.INSTANCE,
            client.asScopedHttpClient(),
            tempDirectory.resolve("sketch").absolutePath.toPath(),
        )
        val mainScheduler = TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val env = Env(
            mainScheduler = mainScheduler,
            sketch = sketch,
            url = "https://example.com/viewer-${System.nanoTime()}.png",
            exportDirectory = tempDirectory.resolve("export"),
            closeCount = AtomicInteger(),
            toasts = CopyOnWriteArrayList(),
        )
        val toaster = object : Toaster {
            override fun toast(text: String) {
                env.toasts.add(text)
            }
        }
        try {

            runOnSwingEdt {
                runWynimeComposeUiTest {
                    Dispatchers.setMain(mainDispatcher)
                    setContent {
                        ProvideCompositionLocalsForPreview {
                            CompositionLocalProvider(
                                LocalSketch provides sketch,
                                LocalToaster provides toaster,
                            ) {
                                ImageViewerContent(
                                    model = env.url,
                                    onClose = { env.closeCount.incrementAndGet() },
                                    modifier = Modifier.fillMaxSize(),
                                    fileSaver = fileSaver,
                                    imageClipboard = imageClipboard,
                                    exportDirectory = env.exportDirectory,
                                )
                            }
                        }
                    }

                    env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                        runCatching {
                            onNodeWithTag(ImageViewerTestTags.ZOOM_IN).assertIsEnabled()
                        }.isSuccess
                    }
                    block(env)
                }
            }
        } finally {
            Dispatchers.resetMain()
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
    }

    @Test
    fun `zoom in and reset change scale`() = runViewerTest { env ->
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertIsDisplayed()
        val initial = scalePercent()
        assertTrue(initial > 0, "initial scale should be positive, was $initial")

        onNodeWithTag(ImageViewerTestTags.ZOOM_OUT).assertIsNotEnabled()
        onNodeWithTag(ImageViewerTestTags.RESET_ZOOM).assertIsNotEnabled()

        onNodeWithTag(ImageViewerTestTags.ZOOM_IN).performClick()
        env.waitUntilPumpingMain(this) { scalePercent() > initial }
        val zoomed = scalePercent()
        assertTrue(zoomed > initial, "zoomed=$zoomed initial=$initial")
        onNodeWithTag(ImageViewerTestTags.ZOOM_OUT).assertIsEnabled()

        onNodeWithTag(ImageViewerTestTags.RESET_ZOOM).performClick()
        env.waitUntilPumpingMain(this) { scalePercent() == initial }
        onNodeWithTag(ImageViewerTestTags.ZOOM_OUT).assertIsNotEnabled()
    }

    @Test
    fun `tap on the image closes, drag does not`() = runViewerTest { env ->

        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(200f, 0f))
            up()
        }

        mainClock.advanceTimeBy(1_000)
        env.mainScheduler.advanceUntilIdle()
        waitForIdle()
        assertEquals(0, env.closeCount.get())

        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput { down(center); up() }
        env.waitUntilPumpingMain(this, timeoutMillis = 5_000) { env.closeCount.get() == 1 }
        assertEquals(1, env.closeCount.get())
    }

    @Test
    fun `close button calls onClose`() = runViewerTest { env ->
        onNodeWithTag(ImageViewerTestTags.CLOSE).performClick()
        waitForIdle()
        assertEquals(1, env.closeCount.get())
    }

    @Test
    fun `save exports the image and reports success`() {
        val saved = CopyOnWriteArrayList<ImageViewerExportedFile>()
        runViewerTest(fileSaver = { saved.add(it); true }) { env ->
            env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                runCatching { onNodeWithTag(ImageViewerTestTags.SAVE).assertIsEnabled() }.isSuccess
            }
            onNodeWithTag(ImageViewerTestTags.SAVE).performClick()
            env.waitUntilPumpingMain(this) { env.toasts.isNotEmpty() }
            assertEquals(1, saved.size)
            assertEquals("png", saved.single().extension)
            assertTrue(saved.single().path.absolutePath.startsWith(env.exportDirectory.absolutePath))
            assertEquals(listOf(runBlocking { getString(Lang.image_viewer_saved) }), env.toasts)
        }
    }

    @Test
    fun `copy button and shortcut copy the exported image`() {
        val copied = CopyOnWriteArrayList<ImageViewerExportedFile>()
        runViewerTest(imageClipboard = { copied.add(it) }) { env ->
            env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                runCatching { onNodeWithTag(ImageViewerTestTags.COPY).assertIsEnabled() }.isSuccess
            }
            onNodeWithTag(ImageViewerTestTags.COPY).performClick()
            env.waitUntilPumpingMain(this) { env.toasts.size == 1 }
            assertEquals(1, copied.size)
            assertEquals("png", copied.single().extension)

            onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performKeyInput {
                withKeyDown(Key.CtrlLeft) { pressKey(Key.C) }
            }
            env.waitUntilPumpingMain(this) { env.toasts.size == 2 }
            assertEquals(2, copied.size)
            assertEquals(List(2) { runBlocking { getString(Lang.image_viewer_copied) } }, env.toasts)
        }
    }

    @Test
    fun `no copy button when the platform has no image clipboard`() = runViewerTest(imageClipboard = null) {
        onNodeWithTag(ImageViewerTestTags.COPY).assertDoesNotExist()
    }

    @Test
    fun `cancelled save shows nothing, failed save reports error`() {
        val attempts = AtomicInteger()
        runViewerTest(
            fileSaver = {
                if (attempts.getAndIncrement() == 0) false else error("disk full")
            },
        ) { env ->
            env.waitUntilPumpingMain(this, timeoutMillis = 10_000) {
                runCatching { onNodeWithTag(ImageViewerTestTags.SAVE).assertIsEnabled() }.isSuccess
            }
            onNodeWithTag(ImageViewerTestTags.SAVE).performClick()
            env.waitUntilPumpingMain(this) { attempts.get() == 1 }
            waitForIdle()
            assertTrue(env.toasts.isEmpty(), "cancel should not toast, got ${env.toasts}")

            onNodeWithTag(ImageViewerTestTags.SAVE).performClick()
            env.waitUntilPumpingMain(this) { env.toasts.isNotEmpty() }
            assertEquals(listOf(runBlocking { getString(Lang.image_viewer_save_failed) }), env.toasts)
        }
    }

    private fun WynimeComposeUiTest.scalePercent(): Int {
        val node = onNodeWithTag(ImageViewerTestTags.SCALE_TEXT).fetchSemanticsNode()
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            ?: return -1
        return text.removeSuffix("%").toIntOrNull() ?: -1
    }

    private fun encodedRaster(width: Int, height: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.clear(0xFF6750A4.toInt())
            return requireNotNull(
                surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG, 100),
            ).bytes
        } finally {
            surface.close()
        }
    }
}

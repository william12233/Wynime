package com.wynime.app.ui.foundation.imageviewer

import com.github.panpf.sketch.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import com.wynime.app.ui.foundation.createDefaultSketch
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.readBytes
import com.wynime.utils.io.resolve
import com.wynime.utils.io.writeBytes
import com.wynime.utils.ktor.asScopedHttpClient
import okio.Path.Companion.toPath
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageViewerExportTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 9)

    @Test
    fun `file name from url, extension from content`() {
        assertEquals("cover" to "png", deriveImageFileName("https://a.com/pics/cover.jpg?x=1#frag", png))
        assertEquals("cover" to "jpg", deriveImageFileName("https://a.com/pics/cover.jpg", jpeg))
    }

    @Test
    fun `unknown content falls back to url extension then jpg`() {
        val unknown = byteArrayOf(1, 2, 3)
        assertEquals("cover" to "webp", deriveImageFileName("https://a.com/cover.webp", unknown))
        assertEquals("image" to "jpg", deriveImageFileName("https://a.com/", unknown))

        assertEquals("abc.php" to "jpg", deriveImageFileName("https://a.com/abc.php", unknown))
    }

    @Test
    fun `generic size segment is prefixed with the previous segment`() {
        assertEquals("277554_large" to "jpg", deriveImageFileName("https://static.myani.org/bangumi/subjects/277554/large", jpeg))
        assertEquals("large" to "jpg", deriveImageFileName("https://a.com/large", jpeg))
        assertEquals("large" to "png", deriveImageFileName("https://a.com/x/large.png", png))
    }

    @Test
    fun `invalid characters are sanitized`() {
        assertEquals("a_b_c" to "png", deriveImageFileName("https://a.com/a:b*c.png", png))
        assertEquals("image" to "png", deriveImageFileName("https://a.com/....png", png))
    }

    @Test
    fun `export writes bytes once and reuses the file`() = runTest {
        val tempDirectory = SystemPaths.createTempDirectory("ani-image-viewer-export-test")
        val requests = AtomicInteger()
        val client = HttpClient(
            MockEngine {
                requests.incrementAndGet()
                respond(content = png, headers = headersOf(HttpHeaders.ContentType, "image/png"))
            },
        )
        val sketch = createDefaultSketch(
            PlatformContext.INSTANCE,
            client.asScopedHttpClient(),
            tempDirectory.resolve("sketch").absolutePath.toPath(),
        )
        val exportDirectory = tempDirectory.resolve("export")
        try {
            val url = "https://example.com/images/cover.jpg?size=large"
            val first = sketch.exportImageForViewer(url, exportDirectory)
            assertEquals("cover.png", first.fileName)
            assertTrue(first.path.exists())
            assertContentEquals(png, first.path.readBytes())
            assertTrue(first.path.absolutePath.startsWith(exportDirectory.absolutePath))

            first.path.writeBytes(byteArrayOf(42))
            val second = sketch.exportImageForViewer(url, exportDirectory)
            assertEquals(first.path, second.path)
            assertContentEquals(byteArrayOf(42), second.path.readBytes())
            assertEquals(1, requests.get())

            val other = sketch.exportImageForViewer("https://example.com/other/cover.jpg", exportDirectory)
            assertEquals("cover.png", other.fileName)
            assertFalse(other.path == first.path)
        } finally {
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
    }
}

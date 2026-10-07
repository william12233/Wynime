package com.wynime.app.ui.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.LoadState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.ktor.asScopedHttpClient
import okio.Path.Companion.toPath
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

class AsyncImageSizingTest {
    @Test
    fun `background reloads at a larger draw size`() = verifyResize { url, modifier, onSuccess ->
        val state = rememberAsyncImageState()
        val loadState = state.loadState
        LaunchedEffect(loadState) {
            if (loadState is LoadState.Success) onSuccess()
        }
        Box(modifier.paintBackground(url, state))
    }

    @Test
    fun `async image reloads at a larger layout size`() = verifyResize { url, modifier, onSuccess ->
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            crossfade = false,
            onSuccess = { onSuccess() },
        )
    }

    private fun verifyResize(content: @Composable (String, Modifier, () -> Unit) -> Unit) {
        val tempDirectory = SystemPaths.createTempDirectory("ani-sketch-resize-test")
        val bytes = encodedRaster(width = 600, height = 900)
        val successfulLoads = AtomicInteger()
        val client = HttpClient(
            MockEngine {
                respond(
                    content = bytes,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
            },
        )
        val sketch = createDefaultSketch(
            PlatformContext.INSTANCE,
            client.asScopedHttpClient(),
            tempDirectory.absolutePath.toPath(),
        )
        val side = mutableStateOf(100.dp)
        val url = "https://example.com/background-resize-${System.nanoTime()}.png"

        try {
            runWynimeComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalSketch provides sketch) {
                        content(url, Modifier.size(side.value)) {
                            successfulLoads.incrementAndGet()
                        }
                    }
                }

                waitUntil(timeoutMillis = 5_000) {
                    successfulLoads.get() >= 1
                }
                val initialSuccessfulLoads = successfulLoads.get()

                runOnUiThread {
                    side.value = 300.dp
                }
                waitUntil(timeoutMillis = 5_000) {
                    successfulLoads.get() > initialSuccessfulLoads
                }

                assertTrue(successfulLoads.get() > initialSuccessfulLoads)
                assertTrue(sketch.memoryCache.size <= sketch.memoryCache.maxSize)
            }
        } finally {
            sketch.shutdown()
            client.close()
            tempDirectory.deleteRecursively()
        }
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

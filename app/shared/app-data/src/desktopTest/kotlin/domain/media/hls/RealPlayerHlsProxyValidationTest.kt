package com.wynime.app.domain.media.hls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.wynime.app.domain.foundation.DefaultHttpClientProvider
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.prefetch.MediaTimeRange
import com.wynime.app.domain.settings.NoProxyProvider
import org.openani.mediamp.source.UriMediaData
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class AbstractRealPlayerHlsProxyValidationTest internal constructor(
    private val serverFactory: HlsProxyServerFactory,
) {
    private val enabled = System.getenv("ANI_HLS_REAL_PLAYER") == "1"
    private val ffmpeg = findExecutable("ffmpeg")
    private val ffprobe = findExecutable("ffprobe")
    private val mpv = findExecutable("mpv")

    private class Fixture(val origin: HlsFixtureOrigin, val preparer: PlatformHlsPlaybackPreparer)

    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val origin = HlsFixtureOrigin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider = DefaultHttpClientProvider(NoProxyProvider, scope)
        try {
            Fixture(
                origin,
                PlatformHlsPlaybackPreparer(provider, PlatformHlsPlaybackPreparer.DEFAULT_SEGMENT_CACHE_MAX_BYTES, serverFactory),
            ).block()
        } finally {
            provider.forceReleaseAll()
            scope.cancel()
            origin.close()
        }
    }

    private fun skipUnless(vararg tools: String?): Boolean {
        if (!enabled) {
            println("[RealPlayer] skipped: set ANI_HLS_REAL_PLAYER=1 to enable")
            return true
        }
        if (tools.any { it == null }) {
            println("[RealPlayer] skipped: required tool not installed")
            return true
        }
        return false
    }

    private class Run(val exitCode: Int, val output: String, val millis: Long)

    private fun run(vararg command: String, timeoutSeconds: Long = 120): Run {
        val start = System.nanoTime()
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "timed out: ${command.joinToString(" ")}" }
        return Run(process.exitValue(), output, (System.nanoTime() - start) / 1_000_000)
    }

    private suspend fun Fixture.proxied(path: String, options: HlsPlaybackOptions = HlsPlaybackOptions(proxySegments = true)): HlsPlaybackPreparerResult {
        val result = preparer.prepare(UriMediaData(origin.url(path)), options)
        assertNotNull(result.session, "expected proxy session for $path")
        return result
    }

    private fun probeDuration(url: String): Double {
        val r = run(ffprobe!!, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", url)
        assertEquals(0, r.exitCode, r.output)
        return r.output.trim().lines().last().toDouble()
    }

    private fun decodeAll(url: String): Run =
        run(ffmpeg!!, "-v", "error", "-xerror", "-i", url, "-f", "null", "-")

    @Test
    fun `ffmpeg decodes every fixture variant through the proxy without errors`() = withFixture {
        if (skipUnless(ffmpeg, ffprobe)) return@withFixture

        data class Case(
            val path: String,
            val options: HlsPlaybackOptions,
            val expectedSeconds: Double,
            val cleanStream: Boolean = true,
        )

        val cases = listOf(
            Case("/hls/vod/index.m3u8", HlsPlaybackOptions(proxySegments = true), 96.0),
            Case("/hls/master.m3u8", HlsPlaybackOptions(proxySegments = true), 96.0),
            Case("/hls/aes/index.m3u8", HlsPlaybackOptions(proxySegments = true), 60.0),
            Case("/hls/fmp4/index.m3u8", HlsPlaybackOptions(proxySegments = true), 60.0),
            Case("/hls/withads.m3u8", HlsPlaybackOptions(filterSegments = true, proxySegments = true), 96.0),
            Case("/hls/withads.m3u8", HlsPlaybackOptions(proxySegments = true), 102.0, cleanStream = false),
        )
        for (case in cases) {
            val result = proxied(case.path, case.options)
            try {
                val duration = probeDuration(result.data.uri)
                assertTrue(
                    duration in (case.expectedSeconds - 1.5)..(case.expectedSeconds + 1.5),
                    "${case.path} ${case.options}: duration $duration, expected ~${case.expectedSeconds}",
                )
                val decode = decodeAll(result.data.uri)
                if (case.cleanStream) {
                    assertEquals(0, decode.exitCode, "${case.path}: ffmpeg failed:\n${decode.output}")
                    assertEquals("", decode.output.trim(), "${case.path}: ffmpeg reported decode errors")
                } else {
                    val direct = decodeAll(origin.url(case.path))
                    assertEquals(direct.exitCode, decode.exitCode, "${case.path}: proxy must behave like the origin.\ndirect:\n${direct.output}\nproxied:\n${decode.output}")
                }
                println("[RealPlayer] ffmpeg OK ${case.path} ${case.options}: duration=$duration decode=${decode.millis}ms")
            } finally {
                result.session?.close()
            }
        }
    }

    @Test
    fun `mpv plays through the proxy and seeks into the middle`() = withFixture {
        if (skipUnless(mpv)) return@withFixture
        for (path in listOf("/hls/vod/index.m3u8", "/hls/master.m3u8", "/hls/aes/index.m3u8", "/hls/fmp4/index.m3u8")) {
            val result = proxied(path)
            try {

                val r = run(
                    mpv!!, "--no-config", "--vo=null", "--ao=null", "--msg-level=all=warn",
                    "--start=40", "--length=3", result.data.uri,
                )
                assertEquals(0, r.exitCode, "$path: mpv failed:\n${r.output}")
                assertTrue(
                    r.output.lineSequence().none { "error" in it.lowercase() || "failed" in it.lowercase() },
                    "$path: mpv reported problems:\n${r.output}",
                )
                println("[RealPlayer] mpv OK $path in ${r.millis}ms")
            } finally {
                result.session?.close()
            }
        }
    }

    @Test
    fun `prefetch makes mpv start faster after jumping to the prefetched position on a slow origin`() = withFixture {
        if (skipUnless(mpv)) return@withFixture

        origin.segmentLatencyMillis = 1_500
        fun playFrom48(url: String): Run = run(
            mpv!!, "--no-config", "--vo=null", "--ao=null", "--msg-level=all=warn",
            "--start=48", "--frames=10", "--untimed", url,
        )

        val cold = proxied("/hls/vod/index.m3u8")
        val coldRun = try {
            playFrom48(cold.data.uri)
        } finally {
            cold.session?.close()
        }
        assertEquals(0, coldRun.exitCode, coldRun.output)

        val warm = proxied("/hls/vod/index.m3u8")
        val warmRun = try {

            httpGet(warm.data.uri)
            warm.session!!.setPrefetchRange(MediaTimeRange(48_000, 78_000))
            withTimeout(60_000) {
                warm.session!!.prefetchProgress.first { list -> list.size == 10 && list.all { it.state == ChunkState.DONE } }
            }
            val before = origin.requests.size
            val run = playFrom48(warm.data.uri)
            val requested = origin.requests.drop(before).map { it.path }.filter { it.endsWith(".ts") }
            println("[RealPlayer] origin segment requests during prefetched playback: $requested")

            val prefetched = (16..25).map { "/hls/vod/seg%03d.ts".format(it) }.toSet()
            assertTrue(requested.none { it in prefetched }, "prefetched segments must be served from cache: $requested")
            run
        } finally {
            warm.session?.close()
        }
        assertEquals(0, warmRun.exitCode, warmRun.output)

        println("[RealPlayer] jump to 48s on slow origin: without prefetch=${coldRun.millis}ms, with prefetch=${warmRun.millis}ms")
        assertTrue(
            warmRun.millis + 1_000 < coldRun.millis,
            "expected prefetch to save at least one segment latency: cold=${coldRun.millis}ms warm=${warmRun.millis}ms",
        )
    }

    private fun findExecutable(name: String): String? {
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin")
        return dirs.map { File(it, name) }.firstOrNull { it.canExecute() }?.absolutePath
    }
}

class RealPlayerHlsProxyValidationTest : AbstractRealPlayerHlsProxyValidationTest(PlatformHlsProxyServerFactory)

class KtorNetworkRealPlayerHlsProxyValidationTest : AbstractRealPlayerHlsProxyValidationTest(KtorNetworkHlsProxyServer.Factory)

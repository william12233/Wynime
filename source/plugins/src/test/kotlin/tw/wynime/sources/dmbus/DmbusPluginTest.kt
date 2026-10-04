package tw.wynime.sources.dmbus

import kotlinx.coroutines.runBlocking
import me.him188.ani.source.plugin.api.ResolvedMediaFormat
import me.him188.ani.source.plugin.api.SourceHttpClient
import me.him188.ani.source.plugin.api.SourceHttpRequest
import me.him188.ani.source.plugin.api.SourceHttpResponse
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.source.plugin.api.SourcePluginLogger
import me.him188.ani.source.plugin.api.SourcePluginPlatform
import me.him188.ani.source.plugin.api.SourceResolveRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DmbusPluginTest {
    @Test
    fun `hhjx media is resolved without a CDN rejected referer`() = runBlocking {
        val http = FixtureHttpClient()
        val plugin = DmbusEntryPoint().create(FixtureContext(http))
        try {
            val resolved = plugin.resolve(
                SourceResolveRequest(
                    subjectId = "6108",
                    channelId = "1",
                    episodeId = "1",
                ),
            )

            assertEquals(
                "https://groupvideo.photo.qq.com/video/episode-1.mp4?token=fixture",
                resolved.url,
            )
            assertEquals(ResolvedMediaFormat.MP4, resolved.format)
            assertTrue(resolved.headers.isEmpty())
            assertTrue(resolved.requestHeaders().isEmpty())
        } finally {
            plugin.close()
        }
    }

    private class FixtureContext(
        override val http: FixtureHttpClient,
    ) : SourcePluginContext {
        override val pluginId: String = "dmbus"
        override val hostVersion: String = "4.9.0-dev"
        override val platform: SourcePluginPlatform = SourcePluginPlatform.DESKTOP
        override val logger: SourcePluginLogger = object : SourcePluginLogger {
            override fun debug(message: String) = Unit
            override fun info(message: String) = Unit
            override fun warn(message: String, throwable: Throwable?) = Unit
            override fun error(message: String, throwable: Throwable?) = Unit
        }
    }

    private class FixtureHttpClient : SourceHttpClient {
        override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse {
            val body = when {
                request.url.contains("/p/6108-1-1.html") -> PAGE
                request.url.contains("hhjx.hhplayer.com/?url=") -> PLAYER
                request.url.endsWith("/api/parse") -> API
                else -> error("Unexpected fixture request: ${request.url}")
            }
            return SourceHttpResponse(
                statusCode = 200,
                finalUrl = request.url,
                body = body.encodeToByteArray(),
            )
        }
    }

    private companion object {
        const val PAGE = """
            <iframe src="https://hhjx.hhplayer.com/?url=fixture-token"></iframe>
        """

        const val PLAYER = """
            <script>
              window.__HHJX_BOOTSTRAP__={"url":"fixture-token","t":1791072866,"key":"fixture-key"};
            </script>
        """

        const val API = """
            {"code":200,"msg":"成功","url":"https://groupvideo.photo.qq.com/video/episode-1.mp4?token=fixture"}
        """
    }
}

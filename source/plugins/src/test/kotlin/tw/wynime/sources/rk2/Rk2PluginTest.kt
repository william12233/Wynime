package tw.wynime.sources.rk2

import kotlinx.coroutines.runBlocking
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import tw.wynime.sources.shared.SourceSiteException

class Rk2PluginTest {
    @Test
    fun `search accepts the required Chinese title`() = runBlocking {
        val plugin = Rk2EntryPoint().create(FixtureContext(FixtureHttpClient()))
        try {
            val results = plugin.search(
                SourceSearchRequest("关于我转生变成史莱姆这档事"),
            )

            assertEquals(listOf("demo#1"), results.map { it.id })
            assertEquals("关于我转生变成史莱姆这档事", results.single().title)
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `subject exposes the three site lines with original names`() = runBlocking {
        val http = FixtureHttpClient()
        val plugin = Rk2EntryPoint().create(FixtureContext(http))
        try {
            val details = plugin.getSubject("demo#1")

            assertEquals(
                listOf("www.2rk.cc", "v1.2rk.cc", "v2.2rk.cc"),
                details.channels.map { it.channel.id },
            )
            assertEquals(
                listOf("线路1", "线路2", "线路3"),
                details.channels.map { it.channel.displayName },
            )
            assertEquals(
                "https://www.2rk.cc/detail/demo?id=2",
                details.channels[2].episodes[1].playPageUrl,
            )
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `resolve uses the site cookie to select a line and preserves referer`() = runBlocking {
        val http = FixtureHttpClient()
        val plugin = Rk2EntryPoint().create(FixtureContext(http))
        try {
            val resolved = plugin.resolve(
                SourceResolveRequest(
                    subjectId = "demo#1",
                    channelId = "v1.2rk.cc",
                    episodeId = "2",
                ),
            )

            val detailRequest = http.requests.last { it.url == "https://www.2rk.cc/detail/demo?id=2" }
            assertEquals("curXianlu=v1.2rk.cc", detailRequest.headers["Cookie"])
            assertEquals(
                "https://v1.2rk.cc/video/demo/2/episode.m3u8",
                resolved.url,
            )
            assertEquals(
                "https://www.2rk.cc/detail/demo?id=2",
                resolved.requestHeaders()["Referer"],
            )
            assertEquals("curXianlu=v1.2rk.cc", resolved.requestHeaders()["Cookie"])
            assertEquals("https://www.2rk.cc", resolved.requestHeaders()["Origin"])
            assertEquals("*/*", resolved.requestHeaders()["Accept"])
            assertTrue(resolved.requestHeaders()["User-Agent"].orEmpty().contains("Android"))
            assertEquals("v1.2rk.cc", resolved.requestContext.cookies["curXianlu"])
            assertTrue(resolved.stableIdentity.contains("2rk"))
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `resolve rejects an invalid upstream hls key`() = runBlocking {
        val http = FixtureHttpClient(keyBody = "x".repeat(31))
        val plugin = Rk2EntryPoint().create(FixtureContext(http))
        try {
            assertFailsWith<SourceSiteException> {
                plugin.resolve(
                    SourceResolveRequest(
                        subjectId = "demo#1",
                        channelId = "www.2rk.cc",
                        episodeId = "1",
                    ),
                )
            }
            Unit
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `resolve falls back to primary line when an alternate edge is unavailable`() = runBlocking {
        val http = FixtureHttpClient(unavailableChannel = "v1.2rk.cc")
        val plugin = Rk2EntryPoint().create(FixtureContext(http))
        try {
            val resolved = plugin.resolve(
                SourceResolveRequest(
                    subjectId = "demo#1",
                    channelId = "v1.2rk.cc",
                    episodeId = "2",
                ),
            )

            assertEquals(
                "https://www.2rk.cc/video/demo/2/episode.m3u8",
                resolved.url,
            )
            assertEquals("curXianlu=www.2rk.cc", resolved.requestHeaders()["Cookie"])
        } finally {
            plugin.close()
        }
    }

    private class FixtureContext(
        override val http: FixtureHttpClient,
    ) : SourcePluginContext {
        override val pluginId: String = "2rk"
        override val hostVersion: String = "4.9.0-dev"
        override val platform: SourcePluginPlatform = SourcePluginPlatform.DESKTOP
        override val logger: SourcePluginLogger = object : SourcePluginLogger {
            override fun debug(message: String) = Unit
            override fun info(message: String) = Unit
            override fun warn(message: String, throwable: Throwable?) = Unit
            override fun error(message: String, throwable: Throwable?) = Unit
        }
    }

    private class FixtureHttpClient(
        private val keyBody: String = FIXTURE_KEY,
        private val unavailableChannel: String? = null,
    ) : SourceHttpClient {
        val requests = mutableListOf<SourceHttpRequest>()

        override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse {
            requests += request
            val body = when {
                request.url.contains("/search?") ->
                    "<a href=\"/detail/demo?id=1\"><span>关于我转生变成史莱姆这档事</span></a>"

                request.url.endsWith("/c.js") ->
                    "const channels=[\"www.2rk.cc\",\"v1.2rk.cc\",\"v2.2rk.cc\"];"

                request.url.endsWith(".m3u8") -> FIXTURE_PLAYLIST

                request.url.endsWith("/saber") -> keyBody

                else -> if (request.headers["Cookie"]?.contains("www.2rk.cc") == true) {
                    FIXTURE_PRIMARY_PAGE
                } else {
                    FIXTURE_PAGE
                }
            }
            return SourceHttpResponse(
                statusCode = if (unavailableChannel != null &&
                    request.url.contains(unavailableChannel) && request.url.endsWith(".m3u8")
                ) 503 else 200,
                finalUrl = request.url,
                body = body.encodeToByteArray(),
            )
        }
    }

    private companion object {
        val FIXTURE_PAGE = """
            <h1>关于我转生变成史莱姆这档事</h1>
            <ul>
              <li><a href="/detail/demo?id=1" title="第01话">第01话</a></li>
              <li><a href="/detail/demo?id=2" title="第02话">第02话</a></li>
            </ul>
            <script>h.loadSource("https://v1.2rk.cc/video/demo/2/episode.m3u8")</script>
        """.trimIndent()
        val FIXTURE_PRIMARY_PAGE = FIXTURE_PAGE.replace(
            "https://v1.2rk.cc/video/demo/2/episode.m3u8",
            "https://www.2rk.cc/video/demo/2/episode.m3u8",
        )
        const val FIXTURE_KEY = "0123456789abcdef"
        val FIXTURE_PLAYLIST = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://www.2rk.cc/saber"
            #EXTINF:1,
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()
    }
}

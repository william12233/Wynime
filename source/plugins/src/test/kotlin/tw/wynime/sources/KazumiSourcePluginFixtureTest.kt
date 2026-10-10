package tw.wynime.sources

import kotlinx.coroutines.runBlocking
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceWebResourceMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tw.wynime.sources.akianime.AkianimeEntryPoint
import tw.wynime.sources.baimao.BaimaoEntryPoint
import tw.wynime.sources.mxdm.MxdmEntryPoint

class KazumiSourcePluginFixtureTest {
    @Test
    fun `baimao parses current search and routes the play page to web matching`() = runBlocking {
        val plugin = BaimaoEntryPoint().create(FixtureContext("baimao") { request ->
            when {
                "/s_all?" in request.url ->
                    "<a href=\"/show/42.html\"><h2>新网球王子 U-17 世界杯 半决赛</h2></a>"
                "/show/42.html" in request.url -> BAIMAO_SUBJECT
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("新网球王子 U-17 世界杯 半决赛")).single()
            val details = plugin.getSubject(subject.id)
            val episode = details.channels.first().episodes.single()
            val resolved = plugin.resolve(SourceResolveRequest(subject.id, details.channels.first().channel.id, episode.id))

            assertEquals("42", subject.id)
            assertEquals("播放Ⅰ", details.channels.first().channel.displayName)
            assertEquals("https://www.bmmdmm.com/play/42-0-0.html", resolved.url)
            assertEquals(ResolvedMediaFormat.WEB, resolved.format)
            assertEquals(SourceWebResourceMatch.LoadPage, plugin.matchWebResource(resolved.url))
            assertEquals(
                SourceWebResourceMatch.Matched("https://cdn.example/baimao.m3u8"),
                plugin.matchWebResource("https://cdn.example/baimao.m3u8"),
            )
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `akianime parses the u17 semifinal source and direct hls play page`() = runBlocking {
        val plugin = AkianimeEntryPoint().create(FixtureContext("akianime") { request ->
            when {
                "/bgmsearch/" in request.url ->
                    "<a href=\"/bgmdetail/xhcDDE.html\"><h3>新网球王子 U-17 世界杯 半决赛</h3></a>"
                "/bgmdetail/xhcDDE.html" in request.url -> AKIANIME_SUBJECT
                "/bgmplay/xhcDDE-1-1.html" in request.url -> AKIANIME_PLAY_PAGE
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("新网球王子 U-17 世界杯 半决赛")).single()
            val details = plugin.getSubject(subject.id)
            val episode = details.channels.single().episodes.single()
            val resolved = plugin.resolve(SourceResolveRequest(subject.id, details.channels.single().channel.id, episode.id))

            assertEquals("xhcDDE", subject.id)
            assertEquals("超高画三线", details.channels.single().channel.displayName)
            assertEquals("https://cdn.example/akianime.m3u8", resolved.url)
            assertEquals(ResolvedMediaFormat.HLS, resolved.format)
            assertEquals(
                "https://www.akianime.cc/bgmplay/xhcDDE-1-1.html",
                resolved.requestHeaders()["Referer"],
            )
            assertTrue(plugin.matchWebResource("https://cdn.example/akianime.mp4") is SourceWebResourceMatch.Matched)
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `akianime resolves the encrypted high quality line through the parser`() = runBlocking {
        val plugin = AkianimeEntryPoint().create(FixtureContext("akianime") { request ->
            when {
                "/bgmplay/high-1-1.html" in request.url ->
                    "<script>var player_aaaa={\"url\":\"Doki-fixture-token\",\"from\":\"YDY\"}</script>"
                "aniplayer.xn--gmqr9gevarqk8t.cn/?url=" in request.url ->
                    "<script>var config={\"url\":\"Doki-fixture-token\",\"key\":\"fixture-key\",\"time\":\"fixture-time\"}</script>"
                request.method == "POST" && "api_config.php" in request.url ->
                    "{\"code\":\"200\",\"url\":\"https://cdn.example/akianime-high.mp4?token=one&part=two\"}"
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val resolved = plugin.resolve(SourceResolveRequest("high", "1", "1"))

            assertEquals(
                "https://cdn.example/akianime-high.mp4?token=one&part=two",
                resolved.url,
            )
            assertEquals(ResolvedMediaFormat.MP4, resolved.format)
            assertTrue(resolved.requestHeaders().isEmpty())
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `baimao decrypts playinfo and returns the direct media url`() = runBlocking {
        val plugin = BaimaoEntryPoint().create(
            FixtureContext(
                pluginId = "baimao",
                handler = { request ->
                    when {
                        "/play/42-0-0.html" in request.url -> "<html>player</html>"
                        request.url.endsWith("/time") -> "1791606027"
                        "/playinfo?" in request.url -> BAIMAO_ENCRYPTED_PLAYINFO
                        else -> "<html><body>ok</body></html>"
                    }
                },
                responseHeaders = { request ->
                    if ("/play/42-0-0.html" in request.url) {
                        mapOf("Set-Cookie" to "t1=1791599772393; Path=/, k1=38161845068; Path=/")
                    } else {
                        emptyMap()
                    }
                },
            ),
        )
        try {
            val resolved = plugin.resolve(SourceResolveRequest("42", "0", "0"))

            assertEquals("https://cdn.example/baimao.m3u8", resolved.url)
            assertEquals(ResolvedMediaFormat.HLS, resolved.format)
            assertEquals(
                "https://www.bmmdmm.com/play/42-0-0.html",
                resolved.requestHeaders()["Referer"],
            )
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `mxdm keeps real channel ids and lets the site player resolve media`() = runBlocking {
        val plugin = MxdmEntryPoint().create(FixtureContext("mxdm") { request ->
            when {
                "/search/?" in request.url ->
                    "<a href=\"/detail/7202/\">黑子的篮球第二季</a>"
                "/detail/7202/" in request.url -> MXDM_SUBJECT
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("黑子的篮球第二季")).single()
            val details = plugin.getSubject(subject.id)
            val channel = details.channels.first()
            val resolved = plugin.resolve(SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.first().id))

            assertEquals("7202", subject.id)
            assertEquals("3", channel.channel.id)
            assertEquals("高清", channel.channel.displayName)
            assertEquals("https://www.dcc3.com/play/7202-3-1/", resolved.url)
            assertEquals(ResolvedMediaFormat.WEB, resolved.format)
            assertEquals(SourceWebResourceMatch.LoadPage, plugin.matchWebResource(resolved.url))
        } finally {
            plugin.close()
        }
    }

    private class FixtureContext(
        override val pluginId: String,
        private val responseHeaders: (SourceHttpRequest) -> Map<String, String> = { emptyMap() },
        handler: (SourceHttpRequest) -> String,
    ) : SourcePluginContext {
        override val hostVersion: String = "4.9.0-dev"
        override val platform: SourcePluginPlatform = SourcePluginPlatform.DESKTOP
        override val http: SourceHttpClient = object : SourceHttpClient {
            override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse = SourceHttpResponse(
                statusCode = 200,
                finalUrl = request.url,
                headers = responseHeaders(request),
                body = handler(request).encodeToByteArray(),
            )
        }
        override val logger: SourcePluginLogger = SilentLogger
    }

    private object SilentLogger : SourcePluginLogger {
        override fun debug(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String, throwable: Throwable?) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }

    private companion object {
        const val BAIMAO_SUBJECT = """
            <h1>新网球王子 U-17 世界杯 半决赛</h1>
            <ul id="menu0"><li>播放Ⅰ</li><li>播放Ⅱ</li></ul>
            <ul><li><a href="/play/42-0-0.html" title="第1集">第1集</a></li></ul>
            <ul><li><a href="/play/42-1-0.html" title="第1集">第1集</a></li></ul>
        """

        const val AKIANIME_SUBJECT = """
            <h3 class="slide-info-title hide">新网球王子 U-17 世界杯 半决赛</h3>
            <div class="anthology-tab nav-swiper b-b"><div class="swiper-wrapper">
                <a class="swiper-slide"><i>播放</i>&nbsp;超高画三线<span>13</span></a>
            </div></div>
            <ul class="anthology-list-play size">
                <li><a class="hide this-link" href="/bgmplay/xhcDDE-1-1.html">01</a></li>
            </ul>
        """

        const val AKIANIME_PLAY_PAGE = """
            <script>var player_aaaa={"url":"https://cdn.example/akianime.m3u8"}</script>
        """

        const val BAIMAO_ENCRYPTED_PLAYINFO = "23c7d4c5dcc3160d07bfc8bdd20ecb04c404f500fbf2f2bef3f9fcf8eb01edb5f4e9e7b2b1bbf3eff2f1e49db49be4e9ebeb969f94aedce1e3acd8d8ded196dfcbc9d2cc91d3c5d8bfc9cccfccba87c9bbceb5bfc280c4c2b2b57b6d846bb4b9bbb566be"

        const val MXDM_SUBJECT = """
            <h2>黑子的篮球第二季</h2>
            <div class="playlist">
                <div class="tabs"><a class="active">高清</a><a>非凡</a><a>量子</a></div>
                <div class="row"><ul><li><a href="/play/7202-3-1/">第01集</a></li></ul></div>
                <div class="row"><ul><li><a href="/play/7202-1-1/">第01集</a></li></ul></div>
                <div class="row"><ul><li><a href="/play/7202-2-1/">第01集</a></li></ul></div>
            </div>
        """
    }
}

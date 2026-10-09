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
    fun `akianime parses the u17 semifinal source and encrypted play page`() = runBlocking {
        val plugin = AkianimeEntryPoint().create(FixtureContext("akianime") { request ->
            when {
                "/bgmsearch/" in request.url ->
                    "<a href=\"/bgmdetail/xhcDDE.html\"><h3>新网球王子 U-17 世界杯 半决赛</h3></a>"
                "/bgmdetail/xhcDDE.html" in request.url -> AKIANIME_SUBJECT
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
            assertEquals("https://www.akianime.cc/bgmplay/xhcDDE-1-1.html", resolved.url)
            assertEquals(ResolvedMediaFormat.WEB, resolved.format)
            assertEquals(SourceWebResourceMatch.LoadPage, plugin.matchWebResource(resolved.url))
            assertTrue(plugin.matchWebResource("https://cdn.example/akianime.mp4") is SourceWebResourceMatch.Matched)
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
        handler: (SourceHttpRequest) -> String,
    ) : SourcePluginContext {
        override val hostVersion: String = "4.9.0-dev"
        override val platform: SourcePluginPlatform = SourcePluginPlatform.DESKTOP
        override val http: SourceHttpClient = object : SourceHttpClient {
            override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse = SourceHttpResponse(
                statusCode = 200,
                finalUrl = request.url,
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

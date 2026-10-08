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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import tw.wynime.sources.dida.DidaEntryPoint
import tw.wynime.sources.dm1.Dm1EntryPoint
import tw.wynime.sources.dmbus.DmbusEntryPoint
import tw.wynime.sources.dyttzy.DyttzyEntryPoint
import tw.wynime.sources.eacg.EacgEntryPoint
import tw.wynime.sources.girigiri.GirigiriEntryPoint
import tw.wynime.sources.next.NextEntryPoint
import tw.wynime.sources.shared.SourceSiteException

class SourcePluginFixtureTest {
    @Test
    fun `eacg parses search subject channel episode and mp4`() = runBlocking {
        val plugin = EacgEntryPoint().create(FixtureContext("eacg") { request ->
            when {
                "/vodsearch/" in request.url -> """
                    <a href="/voddetails-rezero.html">Re:从零开始的异世界生活</a>
                """.trimIndent()
                "/voddetails-rezero.html" in request.url -> EACG_SUBJECT
                "/Comicplay/rezero-1-1.html" in request.url -> """<div data-play="https://sns-video-bd.xhscdn.com/spectrum/re0"></div>"""
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("re0")).single()
            val details = plugin.getSubject(subject.id)
            val channel = details.channels.single()
            val resolved = plugin.resolve(
                SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.single().id),
            )

            assertEquals("rezero", subject.id)
            assertEquals("简中", channel.channel.displayName)
            assertEquals("第01集", channel.episodes.single().displayName)
            assertEquals("https://sns-video-bd.xhscdn.com/spectrum/re0", resolved.url)
            assertEquals(ResolvedMediaFormat.MP4, resolved.format)
            assertTrue("Referer" !in resolved.headers)
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `dm1 parses search subject channel episode and hls`() = runBlocking {
        val plugin = Dm1EntryPoint().create(FixtureContext("dm1") { request ->
            when {
                "/search.html" in request.url -> "<a href=\"/bangumi/123.html\">Re:Zero</a>"
                "/bangumi/123.html" in request.url -> DM1_SUBJECT
                "/watch/123/1/1.html" in request.url -> "<script>var player_aaaa={\"url\":\"https://cdn.example/re0.m3u8\"}</script>"
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("re0")).single()
            val details = plugin.getSubject(subject.id)
            val channel = details.channels.single()
            val resolved = plugin.resolve(
                SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.single().id),
            )

            assertEquals("123", subject.id)
            assertEquals("简中", channel.channel.displayName)
            assertEquals("https://cdn.example/re0.m3u8", resolved.url)
            assertEquals(ResolvedMediaFormat.HLS, resolved.format)
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `next parses server search source name episode and api playback`() = runBlocking {
        val plugin = NextEntryPoint().create(FixtureContext("next") { request ->
            when {
                request.url.contains("/search?") -> "<a href=\"/anime/42\">Re:Zero</a>"
                request.url.contains("/anime/42/play/1") -> NEXT_PLAY
                request.url.endsWith("/anime/42") -> NEXT_SUBJECT
                request.url.contains("/rest/v1/rpc/search_animes") -> "{\"id\":42,\"title\":\"Re:Zero\"}"
                request.url.contains("/functions/v1/issue-web-playback") -> "{\"master_playlist\":\"https://cdn.example/next.m3u8\"}"
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("re0")).single()
            val details = plugin.getSubject(subject.id)
            val channel = details.channels.single()
            val resolved = plugin.resolve(
                SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.single().id),
            )

            assertEquals("42", subject.id)
            assertEquals("主線", channel.channel.displayName)
            assertEquals("https://cdn.example/next.m3u8", resolved.url)
            assertEquals(ResolvedMediaFormat.HLS, resolved.format)
            assertEquals("https://next.xifanacg.com", resolved.requestHeaders()["Origin"])
            assertEquals("https://next.xifanacg.com/anime/42/play/1?source=main", resolved.requestHeaders()["Referer"])
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `next rejects a playback page when the API has no valid media`() = runBlocking {
        val plugin = NextEntryPoint().create(FixtureContext("next") { request ->
            when {
                request.url.contains("/anime/42/play/1") -> NEXT_PLAY
                request.url.contains("/functions/v1/issue-web-playback") ->
                    "{\"url\":\"https://next.xifanacg.com/anime/42/play/1?source=main\",\"candidates\":[{\"url\":\"https://next.xifanacg.com/anime/42/play/1\"}]}"
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            assertFailsWith<SourceSiteException> {
                plugin.resolve(SourceResolveRequest("42", "main", "1"))
            }
            Unit
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `girigiri parses api result from the first search page and keeps one site channel`() = runBlocking {
        val plugin = GirigiriEntryPoint().create(FixtureContext("girigiri") { request ->
            when {
                request.url.contains("api.php/provide/vod") -> GIRIGIRI_SEARCH
                request.url.contains("/GV1222/") -> GIRIGIRI_SUBJECT
                request.url.contains("/playGV1222-1-1/") -> "<script>var player_aaaa={\"url\":\"https://cdn.example/girigiri.mp4\"}</script>"
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("re0")).single()
            val details = plugin.getSubject(subject.id)
            val channel = details.channels.single()
            val resolved = plugin.resolve(
                SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.single().id),
            )

            assertEquals("GV1222", subject.id)
            assertEquals("简中", channel.channel.displayName)
            assertEquals("https://cdn.example/girigiri.mp4", resolved.url)
            assertEquals(ResolvedMediaFormat.MP4, resolved.format)
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `dida parses search subject playlist channel episode and mp4`() = runBlocking {
        val plugin = DidaEntryPoint().create(FixtureContext("dida") { request ->
            when {
                "/search/" in request.url -> "<li><h4 class=\"title\"><a href=\"/detail/321.html\">Re:Zero</a></h4></li>"
                "/detail/321.html" in request.url -> DIDA_SUBJECT
                "/play/321-1-1.html" in request.url -> "<script>var player_aaaa={\"url\":\"https://cdn.example/dida.mp4\"}</script>"
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("re0")).single()
            val details = plugin.getSubject(subject.id)
            val channel = details.channels.single()
            val resolved = plugin.resolve(
                SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.single().id),
            )

            assertEquals("321", subject.id)
            assertEquals("极速線", channel.channel.displayName)
            assertEquals("https://cdn.example/dida.mp4", resolved.url)
            assertEquals(ResolvedMediaFormat.MP4, resolved.format)
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `dmbus parses search subject channel episode and m3u8`() = runBlocking {
        val plugin = DmbusEntryPoint().create(FixtureContext("dmbus") { request ->
            when {
                "/s----------.html" in request.url -> "<a href=\"/v/6108.html\">Re:Zero</a>"
                "/v/6108.html" in request.url -> DMBUS_SUBJECT
                "/p/6108-1-1.html" in request.url -> "<script>var player_aaaa={\"url\":\"https://cdn.example/dmbus.m3u8\"}</script>"
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("re0")).single()
            val details = plugin.getSubject(subject.id)
            val channel = details.channels.single()
            val resolved = plugin.resolve(
                SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.single().id),
            )

            assertEquals("6108", subject.id)
            assertEquals("超清線", channel.channel.displayName)
            assertEquals("https://cdn.example/dmbus.m3u8", resolved.url)
            assertEquals(ResolvedMediaFormat.HLS, resolved.format)
            assertTrue(resolved.requestHeaders()["Referer"].orEmpty().contains("dmbus.cc"))
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `dyttzy keeps only direct hls entries and resolves the current detail`() = runBlocking {
        val requests = mutableListOf<String>()
        val plugin = DyttzyEntryPoint().create(FixtureContext("dyttzy") { request ->
            requests += request.url
            when {
                request.url.contains("wd=") -> DYTTZY_SEARCH
                request.url.contains("ids=2354") -> DYTTZY_DETAIL
                else -> "<html><body>ok</body></html>"
            }
        })
        try {
            val subject = plugin.search(SourceSearchRequest("流浪地球")).single()
            assertEquals("2354", subject.id)
            assertEquals("流浪地球", subject.title)
            assertEquals("https://image.example/2354.jpg", subject.coverUrl)

            val details = plugin.getSubject(subject.id)
            val channel = details.channels.single()
            assertEquals("dyttm3u8", channel.channel.id)
            assertEquals("dyttm3u8", channel.channel.displayName)
            assertEquals(listOf("HD国语", "第2集"), channel.episodes.map { it.displayName })
            assertEquals(listOf(1f, 2f), channel.episodes.map { it.episodeSort })
            assertEquals(listOf("dyttm3u8-1", "dyttm3u8-2"), channel.episodes.map { it.id })
            assertEquals(
                "https://caiji.dyttzyapi.com/api.php/provide/vod?ac=videolist&ids=2354",
                channel.episodes.first().playPageUrl,
            )

            val resolved = plugin.resolve(
                SourceResolveRequest(subject.id, channel.channel.id, channel.episodes.first().id),
            )
            assertEquals("https://cdn.example/film/index.m3u8", resolved.url)
            assertEquals(ResolvedMediaFormat.HLS, resolved.format)
            assertTrue(resolved.requestHeaders()["User-Agent"].orEmpty().contains("Chrome/128.0"))
            assertEquals("application/vnd.apple.mpegurl,application/x-mpegURL,*/*", resolved.requestHeaders()["Accept"])
            assertTrue(resolved.requestHeaders().keys.none { it.equals("Referer", ignoreCase = true) })
            assertEquals(2, requests.count { it.contains("ids=2354") })
        } finally {
            plugin.close()
        }
    }

    @Test
    fun `dyttzy does not expose share or non-hls entries`() = runBlocking {
        val plugin = DyttzyEntryPoint().create(FixtureContext("dyttzy") { request ->
            if (request.url.contains("ids=7")) {
                DYTTZY_INVALID_DETAIL
            } else {
                "<html><body>ok</body></html>"
            }
        })
        try {
            val details = plugin.getSubject("7")
            assertTrue(details.channels.isEmpty())
            assertFailsWith<SourceSiteException> {
                plugin.resolve(SourceResolveRequest("7", "dyttm3u8", "dyttm3u8-1"))
            }
            Unit
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
        const val EACG_SUBJECT = """
            <ul><li class="fed-play-btns"><a>简中</a></li></ul>
            <div class="fed-play-item"><a href="/Comicplay/rezero-1-1.html">第01集</a></div>
        """

        const val DM1_SUBJECT = """
            <div class="anthology-tab"><div class="swiper-wrapper"><a class="swiper-slide">简中</a></div></div>
            <a href="/watch/123/1/1.html">第01集</a>
        """

        const val NEXT_SUBJECT = """
            <script>"id":7,"code":"main","name":"主線"</script>
            <a href="/anime/42/play/1?source=main">第01集</a>
        """

        const val NEXT_PLAY = NEXT_SUBJECT

        const val GIRIGIRI_SEARCH = """
            {"list":[{"vod_id":1222,"vod_name":"Re：从零开始的异世界生活"}]}
        """

        const val GIRIGIRI_SUBJECT = """
            <div class="anthology-tab"><div class="swiper-wrapper"><a class="swiper-slide">简中</a></div></div>
            <a href="/playGV1222-1-1/">第01集</a>
        """

        const val DIDA_SUBJECT = """
            <ul><li><a href="#playlist1">极速線</a></li></ul>
            <div id="playlist1"><a href="/play/321-1-1.html">第01集</a></div>
        """

        const val DMBUS_SUBJECT = """
            <ul class="play_from"><li>超清線</li></ul>
            <a href="/p/6108-1-1.html">第01集</a>
        """

        const val DYTTZY_SEARCH = """
            {"list":[{"vod_id":2354,"vod_name":"流浪地球","vod_pic":"https://image.example/2354.jpg"}]}
        """

        const val DYTTZY_DETAIL = """
            {"list":[{"vod_id":2354,"vod_name":"流浪地球","vod_pic":"https://image.example/2354.jpg","vod_play_from":"dytt${'$'}${'$'}${'$'}dyttm3u8","vod_play_url":"HD国语${'$'}https://vip.dytt-film.com/share/invalid${'$'}${'$'}${'$'}HD国语${'$'}https://cdn.example/film/index.m3u8#第2集${'$'}https://cdn.example/film/2/index.m3u8","vod_down_from":"no","vod_down_url":""}]}
        """

        const val DYTTZY_INVALID_DETAIL = """
            {"list":[{"vod_id":7,"vod_name":"測試","vod_play_from":"dyttm3u8","vod_play_url":"正片${'$'}https://cdn.example/share/only#預告${'$'}https://cdn.example/movie.mp4"}]}
        """
    }
}

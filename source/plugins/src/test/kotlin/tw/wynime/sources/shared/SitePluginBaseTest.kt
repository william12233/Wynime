package tw.wynime.sources.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourceWebResourceMatch
import tw.wynime.sources.baimao.BaimaoEntryPoint

class SitePluginBaseTest {
    @Test
    fun reZeroSearchVariantsKeepTheRequestedShortAlias() {
        val variants = searchQueryVariants("Re：从零开始的异世界生活")

        assertTrue("re0" in variants)
    }

    @Test
    fun presentationOnlyCoverTextDoesNotBecomeTheSubjectTitle() {
        val link = links(
            """
            <a href="/bangumi/1476.html" title="Re：从零开始的异世界生活">Re：从零开始的异世界生活封面图</a>
            """.trimIndent(),
        ).single()

        assertEquals("Re：从零开始的异世界生活", link.text)
        assertEquals(
            "Re：从零开始的异世界生活",
            normalizePresentationTitle("Re：从零开始的异世界生活封面图"),
        )

        val imageOnlyCard = links(
            """
            <a class="public-list-exp" href="/bangumi/1476.html">
                <img alt="Re：从零开始的异世界生活封面图" />
                <span>已完结</span>
            </a>
            """.trimIndent(),
        ).single()
        assertEquals("Re：从零开始的异世界生活", imageOnlyCard.text)
    }

    @Test
    fun decodesSitePrefixedBase64MediaUrl() {
        assertEquals(
            "https://cdn.yzzy31-play.com/20260408/18071_cbd3de5c/index.m3u8",
            decodePlayerUrl("MGaaHR0cHM6Ly9jZG4ueXp6eTMxLXBsYXkuY29tLzIwMjYwNDA4LzE4MDcxX2NiZDNkZTVjL2luZGV4Lm0zdTg="),
        )
    }

    @Test
    fun decodesJsonUnicodeEscapesInMediaUrl() {
        assertEquals(
            "https://media.example/video.mp4?token=one&part=two",
            decodeJsonString("https://media.example/video.mp4?token=one\\u0026part=two"),
        )
    }

    @Test
    fun keepsStaticPlayerAssetsOutOfWebViewResourceCapture() {
        val plugin = BaimaoEntryPoint().create(FakeContext())
        try {
            assertEquals(
                SourceWebResourceMatch.Continue,
                plugin.matchWebResource("https://www.bmmdmm.com/hdst/js/player.js"),
            )
            assertEquals(
                SourceWebResourceMatch.LoadPage,
                plugin.matchWebResource("https://www.bmmdmm.com/hdst/player/artplayer/?url=token"),
            )
        } finally {
            plugin.close()
        }
    }

    private class FakeContext : SourcePluginContext {
        override val pluginId: String = "baimao"
        override val hostVersion: String = "4.9.0-dev"
        override val platform: SourcePluginPlatform = SourcePluginPlatform.DESKTOP
        override val http: SourceHttpClient = object : SourceHttpClient {
            override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse = SourceHttpResponse(
                statusCode = 200,
                finalUrl = request.url,
                body = "<html></html>".encodeToByteArray(),
            )
        }
        override val logger: SourcePluginLogger = object : SourcePluginLogger {
            override fun debug(message: String) = Unit
            override fun info(message: String) = Unit
            override fun warn(message: String, throwable: Throwable?) = Unit
            override fun error(message: String, throwable: Throwable?) = Unit
        }
    }
}

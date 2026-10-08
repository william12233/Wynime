package tw.wynime.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import com.wynime.source.plugin.api.SourceConnectionState
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.source.plugin.api.SourcePluginPlatform
import tw.wynime.sources.dida.DidaEntryPoint
import tw.wynime.sources.dm1.Dm1EntryPoint
import tw.wynime.sources.dmbus.DmbusEntryPoint
import tw.wynime.sources.dyttzy.DyttzyEntryPoint
import tw.wynime.sources.eacg.EacgEntryPoint
import tw.wynime.sources.girigiri.GirigiriEntryPoint
import tw.wynime.sources.next.NextEntryPoint
import tw.wynime.sources.rk2.Rk2EntryPoint
import tw.wynime.sources.shared.jsonArrayObjects

class SourcePluginContractTest {
    @Test
    fun jsonArrayObjectsReturnsEveryItemInsteadOfTheResponseWrapper() {
        val response = """{"list":[{"vod_id":27030,"vod_name":"第四季"},{"vod_id":1222,"vod_name":"第一季"}]}"""

        assertEquals(
            listOf(
                "{\"vod_id\":27030,\"vod_name\":\"第四季\"}",
                "{\"vod_id\":1222,\"vod_name\":\"第一季\"}",
            ),
            jsonArrayObjects(response, "list"),
        )
    }

    @Test
    fun everyPublishedEntryPointHasCompatibleMetadataAndConnectionCheck() = runBlocking {
        val entryPoints = listOf(
            "eacg" to ("https://eacg1.com" to EacgEntryPoint()),
            "dm1" to ("https://dm1.xfdm.pro" to Dm1EntryPoint()),
            "next" to ("https://next.xifanacg.com" to NextEntryPoint()),
            "girigiri" to ("https://ani.girigirilove.com" to GirigiriEntryPoint()),
            "2rk" to ("https://www.2rk.cc" to Rk2EntryPoint()),
            "dida" to ("https://www.didahd.pro" to DidaEntryPoint()),
            "dmbus" to ("https://dmbus.cc" to DmbusEntryPoint()),
            "dyttzy" to ("https://caiji.dyttzyapi.com" to DyttzyEntryPoint()),
        )

        entryPoints.forEach { (id, expected) ->
            val (website, entryPoint) = expected
            val plugin = entryPoint.create(FakeContext(id))
            try {
                assertEquals(id, plugin.metadata.id)
                assertEquals("1.0.26", plugin.metadata.version)
                assertEquals(3, plugin.metadata.pluginApiVersion)
                assertEquals(website, plugin.metadata.website)
                assertTrue(plugin.metadata.iconUrl.orEmpty().startsWith("https://"))
                assertTrue(SourcePluginPlatform.DESKTOP in plugin.metadata.supportedPlatforms)
                assertTrue(SourcePluginPlatform.ANDROID in plugin.metadata.supportedPlatforms)
                assertEquals(SourceConnectionState.CONNECTED, plugin.checkConnection().state)
            } finally {
                plugin.close()
            }
        }
    }

    private class FakeContext(
        override val pluginId: String,
    ) : SourcePluginContext {
        override val hostVersion: String = "4.9.0-dev"
        override val platform: SourcePluginPlatform = SourcePluginPlatform.DESKTOP
        override val http: SourceHttpClient = object : SourceHttpClient {
            override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse = SourceHttpResponse(
                statusCode = 200,
                finalUrl = request.url,
                body = "<html><head><title>fixture</title></head><body>fixture</body></html>"
                    .encodeToByteArray(),
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

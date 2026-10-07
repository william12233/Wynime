package com.wynime.tools.datasourcetestmcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.wynime.app.domain.foundation.WebSourceIdentityFeatureHandler
import com.wynime.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.wynime.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import com.wynime.tools.datasourcetestmcp.mcp.McpRequestHandler
import com.wynime.tools.datasourcetestmcp.mcp.buildToolRegistrations
import com.wynime.tools.datasourcetestmcp.mcp.runHttpMcpServer
import com.wynime.tools.datasourcetestmcp.video.M3u8AdAnalyzer
import com.wynime.tools.datasourcetestmcp.video.MpvVideoAnalyzer
import com.wynime.tools.datasourcetestmcp.video.VideoProbe
import com.wynime.tools.datasourcetestmcp.video.VideoService
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_HOST = "127.0.0.1"
private const val DEFAULT_PORT = 8264

fun main(args: Array<String>) {
    val host = cliOption(args, "--host") ?: DEFAULT_HOST
    val port = cliOption(args, "--port")?.toIntOrNull() ?: DEFAULT_PORT

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    val webSourceCookieJar = WebSourceCookieJar()
    val webSourceIdentityRegistry = WebSourceIdentityRegistry()

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpRequestRetry) {
            maxRetries = 1
            delayMillis { 1_000 }
        }
        install(HttpCookies) {
            storage = webSourceCookieJar
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300.seconds.inWholeMilliseconds
            connectTimeoutMillis = 30.seconds.inWholeMilliseconds

            socketTimeoutMillis = 90.seconds.inWholeMilliseconds
        }
        BrowserUserAgent()
        followRedirects = true
        install(HttpRedirect) {
            checkHttpMethod = false
            allowHttpsDowngrade = true
        }
        expectSuccess = true
    }

    WebSourceIdentityFeatureHandler.applyToClient(client, webSourceIdentityRegistry)

    client.use { client ->
        val probe = VideoProbe(client)
        val videoService = VideoService(
            probe = probe,
            analyzer = MpvVideoAnalyzer(),
            adAnalyzer = M3u8AdAnalyzer(client),
        )

        val handler = McpRequestHandler(
            registrations = buildToolRegistrations(
                json = json,
                videoService = videoService,
            ),
            json = json,
        )
        println("wynime-source-plugin-media-test: starting HTTP MCP server at http://$host:$port/mcp")
        runHttpMcpServer(host = host, port = port, handler = handler)
    }
}

private fun cliOption(args: Array<String>, name: String): String? {
    args.forEachIndexed { index, arg ->
        if (arg == name) return args.getOrNull(index + 1)
        if (arg.startsWith("$name=")) return arg.substringAfter('=')
    }
    return null
}

package com.wynime.app.domain.media.hls

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorNetworkHlsProxyServerTest {
    private suspend fun startEchoServer(): KtorNetworkHlsProxyServer {
        val server = KtorNetworkHlsProxyServer.create()
        server.start { request, sink ->
            val body = "path=${request.path};range=${request.headers["range"]}".encodeToByteArray()
            sink.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
            sink.write(body)
        }
        return server
    }

    private suspend fun awaitServing(url: String): String = withTimeout(15_000) {
        while (true) {
            val result = runCatching { httpGet(url) }.getOrNull()
            if (result != null && result.status == 200) return@withTimeout result.body.decodeToString()
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    @Test
    fun `parses request line and headers`() = runBlocking {
        val server = startEchoServer()
        try {
            val result = httpGet("http://127.0.0.1:${server.port}/segment/3.ts?token=1", mapOf("Range" to "bytes=0-9"))
            assertEquals("path=/segment/3.ts;range=bytes=0-9", result.body.decodeToString())
        } finally {
            server.close()
        }
    }

    @Test
    fun `recovers on the same port after the listener breaks`() = runBlocking {
        val server = startEchoServer()
        try {
            val url = "http://127.0.0.1:${server.port}/playlist.m3u8"
            assertEquals("path=/playlist.m3u8;range=null", awaitServing(url))
            val port = server.port
            repeat(3) {
                server.breakListenerForTest()
                assertEquals("path=/playlist.m3u8;range=null", awaitServing(url))
                assertEquals(port, server.port)
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `does not come back after close`() = runBlocking {
        val server = startEchoServer()
        val url = "http://127.0.0.1:${server.port}/playlist.m3u8"
        awaitServing(url)
        server.close()
        delay(1_500)
        assertFailsWith<Exception> { httpGet(url) }
        Unit
    }
}

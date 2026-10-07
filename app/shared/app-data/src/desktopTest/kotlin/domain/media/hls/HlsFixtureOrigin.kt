package com.wynime.app.domain.media.hls

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class HlsFixtureOrigin : AutoCloseable {
    class RecordedRequest(val path: String, val headers: Map<String, String>)

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "HlsFixtureOrigin").apply { isDaemon = true }
        }
        createContext("/") { exchange -> handle(exchange) }
        start()
    }

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    @Volatile
    var segmentLatencyMillis: Long = 0

    val chunkedPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val failPaths: MutableMap<String, Int> = ConcurrentHashMap()

    private val recorded = ConcurrentLinkedQueue<RecordedRequest>()
    private val resourceCache = ConcurrentHashMap<String, ByteArray?>()

    val requests: List<RecordedRequest> get() = recorded.toList()
    fun count(path: String): Int = recorded.count { it.path == path }
    fun lastHeaders(path: String): Map<String, String>? = recorded.lastOrNull { it.path == path }?.headers

    fun bytesOf(path: String): ByteArray = checkNotNull(resource(path)) { "No fixture at $path" }

    fun url(path: String): String = baseUrl + path

    private fun resource(path: String): ByteArray? = resourceCache.getOrPut(path) {
        HlsFixtureOrigin::class.java.getResourceAsStream(path)?.use { it.readBytes() }
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val headers = exchange.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") }
        recorded += RecordedRequest(path, headers)
        try {
            if (segmentLatencyMillis > 0 && !path.endsWith(".m3u8")) Thread.sleep(segmentLatencyMillis)
            failPaths[path]?.let { status ->
                exchange.sendResponseHeaders(status, -1)
                return
            }
            val body = resource(path)
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            exchange.responseHeaders.add("Content-Type", contentTypeOf(path))
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            val range = headers["range"]?.let { parseRange(it, body.size) }
            val (status, slice) = if (range != null) {
                exchange.responseHeaders.add("Content-Range", "bytes ${range.first}-${range.last}/${body.size}")
                206 to body.copyOfRange(range.first, range.last + 1)
            } else {
                200 to body
            }
            if (path in chunkedPaths) {
                exchange.sendResponseHeaders(status, 0)
            } else {
                exchange.sendResponseHeaders(status, slice.size.toLong())
            }
            exchange.responseBody.use { it.write(slice) }
        } finally {
            exchange.close()
        }
    }

    private fun parseRange(header: String, total: Int): IntRange? {
        val spec = header.removePrefix("bytes=")
        val start = spec.substringBefore('-').toIntOrNull() ?: return null
        val end = spec.substringAfter('-').toIntOrNull() ?: (total - 1)
        if (start < 0 || start >= total) return null
        return start..end.coerceAtMost(total - 1)
    }

    private fun contentTypeOf(path: String): String = when (path.substringAfterLast('.')) {
        "m3u8" -> "application/vnd.apple.mpegurl"
        "ts" -> "video/mp2t"
        "m4s", "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }

    override fun close() {
        server.stop(0)
    }
}

class HttpResult(val status: Int, val headers: Map<String, String>, val body: ByteArray)

fun httpGet(url: String, headers: Map<String, String> = emptyMap()): HttpResult {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.useCaches = false
    connection.connectTimeout = 5_000
    connection.readTimeout = 20_000
    headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
    val status = connection.responseCode
    val stream = if (status >= 400) connection.errorStream else connection.inputStream
    val body = stream?.use { it.readBytes() } ?: ByteArray(0)
    val responseHeaders = connection.headerFields
        .filterKeys { it != null }
        .mapKeys { it.key.lowercase() }
        .mapValues { it.value.joinToString(",") }
    connection.disconnect()
    return HttpResult(status, responseHeaders, body)
}

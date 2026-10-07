package com.wynime.app.domain.media.hls

internal interface HlsProxyServer : AutoCloseable {
    val port: Int

    fun start(handler: HlsProxyRequestHandler)
}

internal fun interface HlsProxyServerFactory {

    suspend fun create(): HlsProxyServer
}

internal class HlsProxyRequest(val path: String, val headers: Map<String, String>)

internal interface HlsProxyResponseSink {
    suspend fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)
}

internal fun interface HlsProxyRequestHandler {

    suspend fun handle(request: HlsProxyRequest, sink: HlsProxyResponseSink)
}

internal expect val PlatformHlsProxyServerFactory: HlsProxyServerFactory

internal inline fun parseHlsProxyRequest(requestLine: String?, readHeaderLine: () -> String?): HlsProxyRequest? {
    if (requestLine == null) return null
    val headers = LinkedHashMap<String, String>()
    while (true) {
        val line = readHeaderLine() ?: break
        if (line.isEmpty()) break
        val colon = line.indexOf(':')
        if (colon > 0) {
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
    }
    val path = requestLine
        .substringAfter(" ", missingDelimiterValue = "")
        .substringBefore(" ", missingDelimiterValue = "")
        .substringBefore("?")
        .ifEmpty { "/playlist.m3u8" }
    return HlsProxyRequest(path, headers)
}

internal expect fun resolveHlsUri(baseUri: String, uri: String): String

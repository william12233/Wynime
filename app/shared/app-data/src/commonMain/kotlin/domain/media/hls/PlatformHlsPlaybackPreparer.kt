package com.wynime.app.domain.media.hls

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.ScopedHttpClientUserAgent
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.prefetch.MediaTimeRange
import com.wynime.app.domain.media.player.prefetch.PrefetchSegmentInfo
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.httpdownloader.m3u.DefaultM3u8Parser
import com.wynime.utils.httpdownloader.m3u.M3u8Playlist
import com.wynime.utils.logging.info
import com.wynime.utils.logging.warn
import org.openani.mediamp.source.UriMediaData
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong

class PlatformHlsPlaybackPreparer internal constructor(
    private val httpClientProvider: HttpClientProvider,
    private val segmentCacheMaxBytes: Long,
    private val serverFactory: HlsProxyServerFactory,
) : HlsPlaybackPreparer {

    constructor(
        httpClientProvider: HttpClientProvider,
        segmentCacheMaxBytes: Long = DEFAULT_SEGMENT_CACHE_MAX_BYTES,
    ) : this(httpClientProvider, segmentCacheMaxBytes, PlatformHlsProxyServerFactory)

    override suspend fun prepare(data: UriMediaData, options: HlsPlaybackOptions): HlsPlaybackPreparerResult {
        if (!options.isEnabled) {
            return HlsPlaybackPreparerResult(data)
        }
        if (!data.uri.isCandidateHlsUri()) {
            return HlsPlaybackPreparerResult(data)
        }
        var baseUri = data.uri
        val manifest = try {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                val response = get(data.uri) {
                    data.headers.forEach { (name, value) -> header(name, value) }
                }
                baseUri = response.call.request.url.toString()
                response.bodyAsText()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return HlsPlaybackPreparerResult(data)
        }

        val session = try {
            LocalHlsProxySession.createOrNull(
                manifest = manifest,
                baseUri = baseUri,
                headers = data.headers,
                httpClientProvider = httpClientProvider,
                options = options,
                segmentCacheMaxBytes = segmentCacheMaxBytes,
                serverFactory = serverFactory,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to prepare HLS playback proxy; falling back to original media data" }
            null
        } ?: return HlsPlaybackPreparerResult(data)

        return HlsPlaybackPreparerResult(
            data = UriMediaData(session.playlistUri, data.headers, data.extraFiles),
            session = session,
        )
    }

    companion object {
        const val DEFAULT_SEGMENT_CACHE_MAX_BYTES: Long = 64L * 1024 * 1024
    }
}

private class LocalHlsProxySession private constructor(
    private val headers: Map<String, String>,
    private val httpClientProvider: HttpClientProvider,
    private val options: HlsPlaybackOptions,
    segmentCacheMaxBytes: Long,
    private val server: HlsProxyServer,
) : HlsPlaybackProxySession {
    private val closed = atomic(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO_ + CoroutineName("HlsProxy-${server.port}"))

    private val routesLock = SynchronizedObject()
    private var nextRouteId = 1

    private val playlistRoutes = HashMap<String, String>()

    private val segmentRoutes = HashMap<String, ProxiedSegment>()

    private val resourceRoutes = HashMap<String, String>()

    @Volatile
    private var activePlaylist: ProxiedPlaylist? = null

    private lateinit var initialContent: String

    val playlistUri: String = "http://127.0.0.1:${server.port}/playlist.m3u8"

    private val segmentCache = SegmentCache(maxBytes = segmentCacheMaxBytes)
    private val prefetchLock = SynchronizedObject()
    private var requestedPrefetchRange: MediaTimeRange? = null
    private var prefetchJob: Job? = null

    private var prefetchTargetUris: List<String> = emptyList()

    @Volatile
    private var prefetchStopRequested = false
    private val prefetchProgressFlow = MutableStateFlow<List<PrefetchSegmentInfo>>(emptyList())

    override val prefetchProgress: Flow<List<PrefetchSegmentInfo>> get() = prefetchProgressFlow

    override fun setPrefetchRange(range: MediaTimeRange?) {
        if (!options.proxySegments) return
        synchronized(prefetchLock) {
            if (requestedPrefetchRange == range) return
            requestedPrefetchRange = range
            restartPrefetchLocked()
        }
    }

    private fun onActivePlaylistChanged() {
        synchronized(prefetchLock) {
            if (requestedPrefetchRange != null) restartPrefetchLocked()
        }
    }

    private fun restartPrefetchLocked() {
        val range = requestedPrefetchRange
        val playlist = activePlaylist
        val targets = if (range == null || playlist == null || closed.value) {
            emptyList()
        } else {
            playlist.segments.filter { it.timeRange.overlaps(range) }
        }
        val targetUris = targets.map { it.remoteUri }

        if (targetUris.isNotEmpty() && targetUris == prefetchTargetUris && prefetchJob?.isActive == true) {
            return
        }
        if (targets.isEmpty()) {

            prefetchStopRequested = true
            prefetchTargetUris = emptyList()
            prefetchProgressFlow.value = emptyList()
            return
        }
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchStopRequested = false
        prefetchTargetUris = targetUris
        range!!
        logger.info { "HLS prefetch $range -> segments ${targets.first().index}..${targets.last().index}" }
        segmentCache.pin(targets.map { it.remoteUri })
        prefetchProgressFlow.value = targets.map { segment ->
            val state = if (segmentCache.isComplete(segment.remoteUri)) ChunkState.DONE else ChunkState.DOWNLOADING
            PrefetchSegmentInfo(segment.timeRange, state)
        }
        prefetchJob = scope.launch {
            for (segment in targets) {
                if (prefetchStopRequested) break
                if (segmentCache.isComplete(segment.remoteUri)) continue
                val success = try {
                    segmentCache.getOrDownload(segment.remoteUri) { downloadSegment(segment.remoteUri) }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warn(e) { "HLS prefetch failed for segment ${segment.index}: ${segment.remoteUri}" }
                    false
                }
                prefetchProgressFlow.update { list ->
                    if (success) {
                        list.map { if (it.range == segment.timeRange) it.copy(state = ChunkState.DONE) else it }
                    } else {
                        list.filterNot { it.range == segment.timeRange }
                    }
                }
            }
        }
    }

    private suspend fun downloadSegment(remoteUri: String): ByteArray {
        return httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
            val response = get(remoteUri) {
                this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
            }
            if (response.status.value !in 200..299) {
                throw IOException("Remote returned ${response.status} for $remoteUri")
            }
            response.bodyAsBytes()
        }
    }

    private suspend fun respond(request: HlsProxyRequest, output: HlsProxyResponseSink) {
        val path = request.path
        val segment = synchronized(routesLock) { segmentRoutes[path] }
        if (segment != null) {
            serveSegment(segment, request, output)
            return
        }
        val resource = synchronized(routesLock) { resourceRoutes[path] }
        if (resource != null) {
            serveRemoteResource(resource, request, output, DEFAULT_RESOURCE_CONTENT_TYPE)
            return
        }
        val content = try {
            playlistContentFor(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to prepare HLS playlist proxy response" }
            null
        }
        if (content == null) {
            output.write(errorResponseHeader(502, "Bad Gateway").encodeToByteArray())
        } else {
            val bytes = content.encodeToByteArray()
            output.write(playlistResponseHeader(bytes.size).encodeToByteArray())
            output.write(bytes)
        }
    }

    private suspend fun playlistContentFor(path: String): String? {
        if (path == "/playlist.m3u8") {
            return initialContent
        }
        val remoteUri = synchronized(routesLock) { playlistRoutes[path] } ?: return null
        return fetchRemotePlaylist(remoteUri).toLocalPlaylist().content
    }

    private suspend fun fetchRemotePlaylist(uri: String): RemotePlaylist {
        return httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
            val response = get(uri) {
                this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
            }
            RemotePlaylist(response.bodyAsText(), response.call.request.url.toString())
        }
    }

    private suspend fun serveSegment(segment: ProxiedSegment, request: HlsProxyRequest, output: HlsProxyResponseSink) {
        val cached = segmentCache.getCompleted(segment.remoteUri) ?: segmentCache.awaitInFlight(segment.remoteUri)
        if (cached != null) {
            serveBytes(cached, request.headers["range"], output)
            return
        }
        serveRemoteResource(segment.remoteUri, request, output, DEFAULT_SEGMENT_CONTENT_TYPE)
    }

    private suspend fun serveRemoteResource(
        remoteUri: String,
        request: HlsProxyRequest,
        output: HlsProxyResponseSink,
        fallbackContentType: String,
    ) {
        var headersWritten = false
        try {
            httpClientProvider.get(ScopedHttpClientUserAgent.BROWSER).use {
                prepareGet(remoteUri) {

                    expectSuccess = false
                    this@LocalHlsProxySession.headers.forEach { (name, value) -> header(name, value) }
                    request.headers["range"]?.let { header(HttpHeaders.Range, it) }
                }.execute { response ->
                    val contentLength = response.contentLength()
                    val header = buildString {
                        append("HTTP/1.1 ").append(response.status.value).append(' ').append(response.status.description).append("\r\n")
                        append("Content-Type: ").append(response.contentType()?.toString() ?: fallbackContentType).append("\r\n")
                        response.headers[HttpHeaders.ContentRange]?.let { append("Content-Range: ").append(it).append("\r\n") }
                        response.headers[HttpHeaders.AcceptRanges]?.let { append("Accept-Ranges: ").append(it).append("\r\n") }
                        if (contentLength != null) {
                            append("Content-Length: ").append(contentLength).append("\r\n")
                        } else {
                            append("Transfer-Encoding: chunked\r\n")
                        }
                        append("Cache-Control: no-store\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }
                    headersWritten = true
                    output.write(header.encodeToByteArray())
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) continue
                        if (contentLength != null) {
                            output.write(buffer, 0, read)
                        } else {
                            output.write(read.toString(16).encodeToByteArray())
                            output.write(CRLF)
                            output.write(buffer, 0, read)
                            output.write(CRLF)
                        }
                    }
                    if (contentLength == null) {
                        output.write("0\r\n\r\n".encodeToByteArray())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {

            if (!headersWritten) {
                logger.warn(e) { "Failed to proxy HLS resource $remoteUri" }
                output.write(errorResponseHeader(502, "Bad Gateway").encodeToByteArray())
            } else {
                throw e
            }
        }
    }

    private suspend fun serveBytes(bytes: ByteArray, rangeHeader: String?, output: HlsProxyResponseSink) {
        val range = rangeHeader?.let { parseByteRange(it, bytes.size.toLong()) }
        if (rangeHeader != null && range == null) {
            output.write(errorResponseHeader(416, "Range Not Satisfiable").encodeToByteArray())
            return
        }
        val start = range?.first ?: 0L
        val endInclusive = range?.last ?: (bytes.size - 1L)
        val length = (endInclusive - start + 1).toInt()
        val header = buildString {
            if (range != null) {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Range: bytes ").append(start).append('-').append(endInclusive).append('/').append(bytes.size).append("\r\n")
            } else {
                append("HTTP/1.1 200 OK\r\n")
            }
            append("Content-Type: ").append(DEFAULT_SEGMENT_CONTENT_TYPE).append("\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: ").append(length).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.encodeToByteArray())
        output.write(bytes, start.toInt(), length)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            scope.cancel()
            server.close()
            segmentCache.clear()
        }
    }

    private class RemotePlaylist(val content: String, val baseUri: String)
    private class LocalPlaylist(val content: String)

    private fun RemotePlaylist.toLocalPlaylist(): LocalPlaylist {
        val filterResult = HlsManifestFilter.filter(content, baseUri)
        if (options.filterSegments) {
            logger.info { "HLS filter result $baseUri is ${filterResult.status}, reason: ${filterResult.reason}, removed groups: ${filterResult.removedGroups}" }
        }
        val isMaster = filterResult.status == HlsManifestFilterStatus.Unsupported && filterResult.reason == "master_playlist"
        if (isMaster) {
            return LocalPlaylist(content.rewriteMasterPlaylistUris(baseUri))
        }
        val mediaContent = if (options.filterSegments && filterResult.status == HlsManifestFilterStatus.Filtered) {
            filterResult.content
        } else {
            content
        }
        return LocalPlaylist(rewriteMediaPlaylist(mediaContent, baseUri))
    }

    private fun rewriteMediaPlaylist(content: String, baseUri: String): String {
        val playlist = runCatching { DefaultM3u8Parser.parse(content, baseUri) }.getOrNull()
        val eligible = playlist is M3u8Playlist.MediaPlaylist &&
                playlist.isEndlist &&
                playlist.segments.isNotEmpty() &&
                playlist.segments.none { it.byteRange != null }
        val proxied = if (options.proxySegments && eligible) {
            val segmentLineCount = content.lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
            if (segmentLineCount != playlist.segments.size) {
                logger.warn { "HLS segment line count $segmentLineCount != parsed ${playlist.segments.size}; using generic resource routes for $baseUri" }
                null
            } else {
                val result = ArrayList<ProxiedSegment>(playlist.segments.size)
                var cursorMillis = 0L
                synchronized(routesLock) {
                    for ((index, segment) in playlist.segments.withIndex()) {
                        val durationMillis = (segment.duration.toDouble() * 1000).roundToLong().coerceAtLeast(0L)
                        val remoteUri = resolveHlsUri(baseUri, segment.uri)

                        val route = "/segment/${nextRouteId++}${segmentExtension(remoteUri)}"
                        val item = ProxiedSegment(
                            index = index,
                            route = route,
                            remoteUri = remoteUri,
                            timeRange = MediaTimeRange(cursorMillis, cursorMillis + durationMillis),
                        )
                        result += item
                        segmentRoutes[route] = item
                        cursorMillis += durationMillis
                    }
                }
                result
            }
        } else {
            null
        }
        if (proxied != null) {
            activePlaylist = ProxiedPlaylist(proxied)
            onActivePlaylistChanged()
        }

        var segmentCursor = 0
        val rewritten = content.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        match.groupValues[1] + localResourceUri(resolveHlsUri(baseUri, match.groupValues[2])) + match.groupValues[3]
                    }
                }

                else -> {
                    val remoteUri = resolveHlsUri(baseUri, line)
                    proxied?.getOrNull(segmentCursor++)?.let { "http://127.0.0.1:${server.port}${it.route}" }
                        ?: localResourceUri(remoteUri)
                }
            }
        } + if (content.endsWith('\n')) "\n" else ""
        return rewritten
    }

    private fun String.rewriteMasterPlaylistUris(baseUri: String): String {
        return lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("#EXT-X-MEDIA") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        val uri = resolveHlsUri(baseUri, match.groupValues[2])
                        match.groupValues[1] + localPlaylistUri(uri) + match.groupValues[3]
                    }
                }

                line.startsWith("#") -> {
                    line.replace(URI_ATTRIBUTE_REGEX) { match ->
                        val uri = resolveHlsUri(baseUri, match.groupValues[2])
                        match.groupValues[1] + localResourceUri(uri) + match.groupValues[3]
                    }
                }

                line.isBlank() -> line
                else -> localPlaylistUri(resolveHlsUri(baseUri, line))
            }
        } + if (endsWith('\n')) "\n" else ""
    }

    private fun localPlaylistUri(remoteUri: String): String {
        val route = synchronized(routesLock) {
            "/playlist/${nextRouteId++}.m3u8".also { playlistRoutes[it] = remoteUri }
        }
        return "http://127.0.0.1:${server.port}$route"
    }

    private fun localResourceUri(remoteUri: String): String {
        val route = synchronized(routesLock) {
            "/resource/${nextRouteId++}".also { resourceRoutes[it] = remoteUri }
        }
        return "http://127.0.0.1:${server.port}$route"
    }

    private class ProxiedSegment(
        val index: Int,
        val route: String,
        val remoteUri: String,
        val timeRange: MediaTimeRange,
    )

    private class ProxiedPlaylist(val segments: List<ProxiedSegment>)

    companion object {
        private const val DEFAULT_SEGMENT_CONTENT_TYPE = "video/mp2t"
        private const val DEFAULT_RESOURCE_CONTENT_TYPE = "application/octet-stream"
        private val CRLF = "\r\n".encodeToByteArray()

        suspend fun createOrNull(
            manifest: String,
            baseUri: String,
            headers: Map<String, String>,
            httpClientProvider: HttpClientProvider,
            options: HlsPlaybackOptions,
            segmentCacheMaxBytes: Long,
            serverFactory: HlsProxyServerFactory,
        ): LocalHlsProxySession? {
            val filterResult = HlsManifestFilter.filter(manifest, baseUri)
            logger.info { "HLS prepare filter result $baseUri is ${filterResult.status}, reason: ${filterResult.reason}, removed groups: ${filterResult.removedGroups}" }
            val isMaster = filterResult.status == HlsManifestFilterStatus.Unsupported && filterResult.reason == "master_playlist"
            val isFiltered = options.filterSegments && filterResult.status == HlsManifestFilterStatus.Filtered
            val needsProxy = isMaster || isFiltered || (options.proxySegments && manifest.isProxyableMediaPlaylist(baseUri))
            if (!needsProxy) return null

            val session = LocalHlsProxySession(headers, httpClientProvider, options, segmentCacheMaxBytes, serverFactory.create())
            try {
                session.initialContent = with(session) { RemotePlaylist(manifest, baseUri).toLocalPlaylist().content }
                session.server.start { request, sink -> session.respond(request, sink) }
            } catch (e: Throwable) {
                session.close()
                throw e
            }
            return session
        }

        private fun String.isProxyableMediaPlaylist(baseUri: String): Boolean {
            val playlist = runCatching { DefaultM3u8Parser.parse(this, baseUri) }.getOrNull()
            return playlist is M3u8Playlist.MediaPlaylist &&
                    playlist.isEndlist &&
                    playlist.segments.isNotEmpty() &&
                    playlist.segments.none { it.byteRange != null }
        }
    }
}

private class SegmentCache(private val maxBytes: Long) {
    private class Entry(val deferred: kotlinx.coroutines.CompletableDeferred<ByteArray>) {
        val bytes: ByteArray? get() = if (deferred.isCompleted && !deferred.isCancelled) deferred.getCompleted() else null
    }

    private val lock = SynchronizedObject()

    private val entries = LinkedHashMap<String, Entry>()
    private var pinned: Set<String> = emptySet()
    private var totalBytes = 0L

    fun pin(uris: List<String>) = synchronized(lock) { pinned = uris.toSet() }

    private fun touchLocked(uri: String): Entry? {
        val entry = entries.remove(uri) ?: return null
        entries[uri] = entry
        return entry
    }

    fun isComplete(uri: String): Boolean = synchronized(lock) { touchLocked(uri)?.bytes != null }

    fun getCompleted(uri: String): ByteArray? = synchronized(lock) { touchLocked(uri)?.bytes }

    suspend fun awaitInFlight(uri: String): ByteArray? {
        val deferred = synchronized(lock) { touchLocked(uri)?.deferred } ?: return null
        return runCatching { deferred.await() }.getOrNull()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun getOrDownload(uri: String, download: suspend () -> ByteArray): ByteArray {
        while (true) {
            val (entry, owner) = synchronized(lock) {
                val existing = touchLocked(uri)
                if (existing != null && !existing.deferred.isCancelled) {
                    existing to false
                } else {
                    val created = Entry(kotlinx.coroutines.CompletableDeferred())
                    entries[uri] = created
                    created to true
                }
            }
            if (!owner) {
                try {
                    return entry.deferred.await()
                } catch (e: CancellationException) {

                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    continue
                }
            }
            try {
                val bytes = download()
                synchronized(lock) {
                    totalBytes += bytes.size
                    entry.deferred.complete(bytes)
                    evictLocked()
                }
                return bytes
            } catch (e: Throwable) {
                synchronized(lock) {
                    if (entries[uri] === entry) entries.remove(uri)

                    entry.deferred.cancel(kotlinx.coroutines.CancellationException("download failed", e))
                }
                throw e
            }
        }
    }

    private fun evictLocked() {
        if (totalBytes <= maxBytes) return
        val iterator = entries.entries.iterator()
        while (totalBytes > maxBytes && iterator.hasNext()) {
            val (uri, entry) = iterator.next()
            val bytes = entry.bytes ?: continue
            if (uri in pinned) continue
            totalBytes -= bytes.size
            iterator.remove()
        }
    }

    fun clear() = synchronized(lock) {
        entries.values.forEach { it.deferred.cancel() }
        entries.clear()
        totalBytes = 0
    }
}

private val logger = com.wynime.utils.logging.logger<PlatformHlsPlaybackPreparer>()

private fun String.isCandidateHlsUri(): Boolean {
    val scheme = substringBefore("://", missingDelimiterValue = "").lowercase()
    return (scheme == "http" || scheme == "https") && lowercase().contains(".m3u8")
}

private fun segmentExtension(remoteUri: String): String {
    val fileName = remoteUri.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
    return if (extension.length in 1..5 && extension.all { it.isLetterOrDigit() }) ".$extension" else ".ts"
}

private fun parseByteRange(header: String, totalLength: Long): LongRange? {
    val spec = header.trim().removePrefix("bytes=").takeIf { it != header.trim() } ?: return null
    if (',' in spec) return null
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val startText = spec.substring(0, dash).trim()
    val endText = spec.substring(dash + 1).trim()
    val start: Long
    val end: Long
    if (startText.isEmpty()) {
        val suffix = endText.toLongOrNull() ?: return null
        if (suffix <= 0) return null
        start = (totalLength - suffix).coerceAtLeast(0)
        end = totalLength - 1
    } else {
        start = startText.toLongOrNull() ?: return null
        end = if (endText.isEmpty()) totalLength - 1 else (endText.toLongOrNull() ?: return null).coerceAtMost(totalLength - 1)
    }
    if (start < 0 || start >= totalLength || end < start) return null
    return start..end
}

private fun playlistResponseHeader(contentLength: Int): String {
    return buildString {
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: application/vnd.apple.mpegurl; charset=utf-8\r\n")
        append("Content-Length: ").append(contentLength).append("\r\n")
        append("Cache-Control: no-store\r\n")
        append("Connection: close\r\n")
        append("\r\n")
    }
}

private fun errorResponseHeader(code: Int, reason: String): String {
    return buildString {
        append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        append("Content-Length: 0\r\n")
        append("Cache-Control: no-store\r\n")
        append("Connection: close\r\n")
        append("\r\n")
    }
}

private val URI_ATTRIBUTE_REGEX = Regex("""(URI=")([^"]+)(")""")

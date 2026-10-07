package com.wynime.tools.datasourcetestmcp.video

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import com.wynime.utils.ktor.UrlHelpers

interface VideoUrlProbeEngine {
    suspend fun probe(url: String, headers: Map<String, String>): VideoProbeResult
}

class VideoProbe(
    private val httpClient: HttpClient,
) : VideoUrlProbeEngine {
    override suspend fun probe(
        url: String,
        headers: Map<String, String>,
    ): VideoProbeResult {
        return try {
            probeImpl(url, headers)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            VideoProbeResult(
                ok = false,
                url = url,
                kind = "unknown",
                summary = "Probe failed with exception",
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
            )
        }
    }

    private suspend fun probeImpl(
        url: String,
        headers: Map<String, String>,
    ): VideoProbeResult {
        val response = request(url, headers)
        val contentType = response.contentType()?.toString()
        val finalUrl = response.request.url.toString()

        if (url.lowercase().contains(".m3u8") || contentType?.contains("mpegurl", ignoreCase = true) == true) {
            val playlist = response.bodyAsText()
            val entries = playlist.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
            if (entries.isEmpty()) {
                return VideoProbeResult(
                    ok = false,
                    url = url,
                    finalUrl = finalUrl,
                    kind = "m3u8",
                    statusCode = response.status.value,
                    contentType = contentType,
                    headers = response.headersSnapshot(),
                    summary = "Playlist loaded but contains no playable entries",
                    playlistEntries = 0,
                    errors = listOf("Empty playlist"),
                )
            }

            val firstEntry = UrlHelpers.computeAbsoluteUrl(finalUrl, entries.first())
            if (firstEntry.lowercase().contains(".m3u8")) {
                return probe(firstEntry, headers).copy(
                    kind = "m3u8-master",
                    nestedPlaylistUrl = firstEntry,
                )
            }

            val segmentResponse = request(firstEntry, headers, rangeProbe = true)
            return VideoProbeResult(
                ok = segmentResponse.status.isSuccess(),
                url = url,
                finalUrl = finalUrl,
                kind = "m3u8",
                statusCode = response.status.value,
                contentType = contentType,
                headers = response.headersSnapshot(),
                summary = if (segmentResponse.status.isSuccess()) {
                    "Playlist and first segment are reachable"
                } else {
                    "Playlist is reachable but first segment probe failed"
                },
                playlistEntries = entries.size,
                sampledSegmentUrl = firstEntry,
                sampledSegmentStatusCode = segmentResponse.status.value,
                errors = if (segmentResponse.status.isSuccess()) emptyList() else listOf("First segment probe returned ${segmentResponse.status.value}"),
            )
        }

        return VideoProbeResult(
            ok = response.status.isSuccess(),
            url = url,
            finalUrl = finalUrl,
            kind = "file",
            statusCode = response.status.value,
            contentType = contentType,
            headers = response.headersSnapshot(),
            summary = if (response.status.isSuccess()) "Media URL is reachable" else "Media URL probe failed",
            errors = if (response.status.isSuccess()) emptyList() else listOf("Probe returned ${response.status.value}"),
        )
    }

    private suspend fun request(
        url: String,
        headers: Map<String, String>,
        rangeProbe: Boolean = false,
    ): HttpResponse {
        return httpClient.get(url) {
            headers.forEach { (key, value) -> header(key, value) }
            if (rangeProbe) {
                header(HttpHeaders.Range, "bytes=0-1023")
            }
        }
    }

    private fun HttpResponse.headersSnapshot(): Map<String, String> {
        return headers.entries().associate { it.key to it.value.joinToString(", ") }
    }
}

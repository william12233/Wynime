package com.wynime.source.plugin.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SourcePluginTest {
    @Test
    fun mediaIdentityIsStableAndUnambiguous() {
        val first = SourceResolveRequest("subject/1", "channel", "episode").mediaIdentity("plugin")
        val second = SourceResolveRequest("subject", "1/channel", "episode").mediaIdentity("plugin")

        assertEquals(first.asStableId(), first.asStableId())
        assertNotEquals(first.asStableId(), second.asStableId())
        assertTrue(first.asStableId().startsWith("6:plugin"))
    }

    @Test
    fun resolvedMediaRoundTripsRequestContext() {
        val media = ResolvedMedia(
            stableIdentity = "plugin-media",
            url = "https://media.example/playlist.m3u8",
            format = ResolvedMediaFormat.HLS,
            headers = mapOf(
                "User-Agent" to "WynimeTest/1",
            ),
            originalPageUrl = "https://site.example/watch/episode",
            expiresAt = Instant.parse("2030-01-02T03:04:05Z"),
            requestContext = SourceMediaRequestContext(
                cookies = mapOf("session" to "opaque"),
                referrer = "https://site.example/watch/episode",
                origin = "https://site.example",
            ),
        )

        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString(ResolvedMedia.serializer(), json.encodeToString(media))

        assertEquals(media, decoded)
        assertEquals(
            mapOf(
                "User-Agent" to "WynimeTest/1",
                "Referer" to "https://site.example/watch/episode",
                "Origin" to "https://site.example",
                "Cookie" to "session=opaque",
            ),
            decoded.requestHeaders(),
        )
        assertEquals(media.expiresAt, decoded.expiresAt)
    }
}

package com.wynime.utils.httpdownloader.m3u

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultM3u8ParserTest {

    private val parser = DefaultM3u8Parser

    @Test
    fun `parse - master playlist with single variant`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            http://example.com/low.m3u8
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MasterPlaylist, "Should parse as MasterPlaylist")

        assertEquals(3, playlist.version)

        assertEquals(1, playlist.variants.size)
        val variant = playlist.variants.first()
        assertEquals("http://example.com/low.m3u8", variant.uri)
        assertEquals(1280000, variant.bandwidth)
        assertEquals("640x360", variant.resolution)
        assertEquals("avc1.4d401e,mp4a.40.2", variant.codecs)
    }

    @Test
    fun `parse - master playlist with multiple variants`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,AVERAGE-BANDWIDTH=1000000
            http://example.com/low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720,FRAME-RATE=30.0
            http://example.com/high.m3u8
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MasterPlaylist, "Should parse as MasterPlaylist")

        assertEquals(6, playlist.version)

        assertEquals(2, playlist.variants.size)

        val (first, second) = playlist.variants
        assertEquals("http://example.com/low.m3u8", first.uri)
        assertEquals(1280000, first.bandwidth)
        assertEquals(1000000, first.averageBandwidth)
        assertNull(first.resolution)

        assertEquals("http://example.com/high.m3u8", second.uri)
        assertEquals(2560000, second.bandwidth)
        assertNull(second.averageBandwidth)
        assertEquals("1280x720", second.resolution)
        assertEquals(30.0f, second.frameRate)
    }

    @Test
    fun `parse - basic media playlist`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:1
            #EXTINF:9.0,
            http://example.com/segment1.ts
            #EXTINF:9.0,
            http://example.com/segment2.ts
            #EXTINF:9.0,
            http://example.com/segment3.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist, "Should parse as MediaPlaylist")

        assertEquals(3, playlist.version)
        assertEquals(10, playlist.targetDuration)
        assertEquals(1, playlist.mediaSequence)
        assertTrue(playlist.isEndlist)

        assertEquals(3, playlist.segments.size)
        playlist.segments.forEach { segment ->

            assertEquals(9.0f, segment.duration)

            assertTrue(segment.uri.startsWith("http://example.com/segment"))
        }
    }

    @Test
    fun `parse - media playlist with discontinuity and byte range`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:50
            #EXTINF:10.0,Segment 50
            http://example.com/segment50.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.0,Segment 51
            #EXT-X-BYTERANGE:75232@0
            http://example.com/segment51.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist)

        assertEquals(4, playlist.version)
        assertEquals(10, playlist.targetDuration)
        assertEquals(50, playlist.mediaSequence)
        assertEquals(2, playlist.segments.size)

        val firstSegment = playlist.segments[0]
        assertEquals(10.0f, firstSegment.duration)
        assertEquals("http://example.com/segment50.ts", firstSegment.uri)
        assertEquals("Segment 50", firstSegment.title)
        assertFalse(firstSegment.isDiscontinuity)
        assertNull(firstSegment.byteRange)

        val secondSegment = playlist.segments[1]
        assertEquals(10.0f, secondSegment.duration)
        assertEquals("http://example.com/segment51.ts", secondSegment.uri)
        assertEquals("Segment 51", secondSegment.title)

        assertTrue(secondSegment.isDiscontinuity)

        assertEquals(75232, secondSegment.byteRange?.length)
        assertEquals(0, secondSegment.byteRange?.offset)

        assertTrue(playlist.isEndlist)
    }

    @Test
    fun `parse - discontinuity sequence is not segment discontinuity`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-DISCONTINUITY-SEQUENCE:7
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            segment0.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist)
        assertEquals(1, playlist.segments.size)
        assertFalse(playlist.segments.single().isDiscontinuity)
    }

    @Test
    fun `parse - media segment source range`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            segment0.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:10.0,
            #EXT-X-BYTERANGE:75232@0
            segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist)
        assertEquals(M3u8SourceRange(startLine = 4, endLine = 5), playlist.segments[0].sourceRange)
        assertEquals(M3u8SourceRange(startLine = 6, endLine = 9), playlist.segments[1].sourceRange)
    }

    @Test
    fun `parse - media playlist with encryption key`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:5
            #EXT-X-TARGETDURATION:8
            #EXT-X-KEY:METHOD=AES-128,URI="https://keyserver.example.com/key",IV=0x1A2B3C4D5E6F
            #EXTINF:8.0,
            http://cdn.example.com/fileSequence0.ts
            #EXTINF:8.0,
            http://cdn.example.com/fileSequence1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")

        assertTrue(playlist is M3u8Playlist.MediaPlaylist)

        assertEquals(5, playlist.version)
        assertEquals(8, playlist.targetDuration)
        assertTrue(playlist.isEndlist)
        assertEquals(2, playlist.segments.size)

        val segment0 = playlist.segments[0]
        assertEquals(8.0f, segment0.duration)
        assertEquals("AES-128", segment0.encryption?.method)
        assertEquals("https://keyserver.example.com/key", segment0.encryption?.uri)
        assertEquals("0x1A2B3C4D5E6F", segment0.encryption?.iv)

        val segment1 = playlist.segments[1]
        assertEquals("AES-128", segment1.encryption?.method)
        assertEquals("https://keyserver.example.com/key", segment1.encryption?.uri)
        assertEquals("0x1A2B3C4D5E6F", segment1.encryption?.iv)
    }

    @Test
    fun `parse - master playlist fallback to version default`() {

        val content = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000
            http://example.com/only.m3u8
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")
        assertTrue(playlist is M3u8Playlist.MasterPlaylist)

        assertEquals(3, playlist.version)
        assertEquals(1, playlist.variants.size)
    }

    @Test
    fun `parse - media playlist fallback to default fields`() {

        val content = """
            #EXTM3U
            #EXTINF:6.0,
            fileSequence0.ts
            #EXTINF:6.0,
            fileSequence1.ts
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")
        assertTrue(playlist is M3u8Playlist.MediaPlaylist)

        assertEquals(3, playlist.version)

        assertNull(playlist.targetDuration)

        assertEquals(0, playlist.mediaSequence)

        assertFalse(playlist.isEndlist)

        assertEquals(2, playlist.segments.size)
    }

    @Test
    fun `parse - invalid input throws exception`() {
        assertFailsWith<M3uFormatException> {
            parser.parse("", "http://example.com")
        }
        assertFailsWith<M3uFormatException> {

            parser.parse("#EXT-X-STREAM-INF:BANDWIDTH=1000000\nhttp://example.com/video.m3u8", "http://example.com")
        }

        parser.parse("#EXTM3U\n#EXTX-STREAM-INF:BANDWIDTH=1000000\nhttp://example.com/video.m3u8", "http://example.com")

    }

    @Test
    fun `parse - media playlist with byte range no offset`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,No offset
            #EXT-X-BYTERANGE:45000
            http://example.com/segmentNoOffset.ts
        """.trimIndent()

        val playlist = parser.parse(content, "http://example.com")
        assertTrue(playlist is M3u8Playlist.MediaPlaylist)
        assertEquals(1, playlist.segments.size)

        val segment = playlist.segments[0]
        assertEquals(10.0f, segment.duration)
        assertEquals("http://example.com/segmentNoOffset.ts", segment.uri)
        assertEquals(45000, segment.byteRange?.length)
        assertNull(segment.byteRange?.offset)
    }

    @Test
    fun `parseResolvedMediaPlaylist - should preserve raw content and resolved media playlist`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-PLAYLIST-TYPE:VOD

            #EXTINF:4.0,
            segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val resolved = parser.parseResolvedMediaPlaylist(content, "https://example.com/path/playlist.m3u8")

        assertEquals("https://example.com/path/playlist.m3u8", resolved.sourceUrl)
        assertEquals(content, resolved.rawContent)
        assertEquals(6, resolved.playlist.version)
        assertEquals(10, resolved.playlist.mediaSequence)
        assertEquals("https://example.com/path/segment1.ts", resolved.playlist.segments.single().uri)
    }

    @Test
    fun `export - should preserve metadata while rewriting segment and key URIs`() {
        val content = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x01020304

            #EXTINF:4.0,
            segment1.ts
            #EXTINF:4.0,
            https://cdn.example.com/segment2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val resolved = parser.parseResolvedMediaPlaylist(content, "https://example.com/path/playlist.m3u8")
        val exported = resolved.export(
            resolveSegmentUri = { _, index -> "local/$index.ts" },
            resolveKeyUri = { "keys/local.key" },
        )

        val expected = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-KEY:METHOD=AES-128,URI="keys/local.key",IV=0x01020304

            #EXTINF:4.0,
            local/0.ts
            #EXTINF:4.0,
            local/1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals(expected, exported.trimEnd())
    }
}

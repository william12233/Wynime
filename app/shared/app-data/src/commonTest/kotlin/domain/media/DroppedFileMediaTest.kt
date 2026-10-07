package com.wynime.app.domain.media

import kotlinx.io.files.Path
import com.wynime.app.domain.media.resolver.LocalFileMediaResolver
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DroppedFileMediaTest {
    @Test
    fun `recognizes video files by extension ignoring case`() {
        assertTrue(DroppedFileMedia.isVideoFile(Path("/videos/episode-01.mkv")))
        assertTrue(DroppedFileMedia.isVideoFile(Path("/videos/EPISODE-01.MP4")))
        assertFalse(DroppedFileMedia.isVideoFile(Path("/videos/episode-01.ass")))
        assertFalse(DroppedFileMedia.isVideoFile(Path("/videos/mkv")))
        assertFalse(DroppedFileMedia.isVideoFile(Path("/downloads/Wynime-4.12.0.dmg")))
    }

    @Test
    fun `finds the first video file among dropped files`() {
        val video = Path("/videos/episode-01.mkv")
        assertEquals(
            video.inSystem,
            DroppedFileMedia.findVideoFile(listOf(Path("/videos/episode-01.ass"), video, Path("/videos/episode-02.mkv"))),
        )
        assertNull(DroppedFileMedia.findVideoFile(listOf(Path("/videos/episode-01.ass"))))
        assertNull(DroppedFileMedia.findVideoFile(emptyList()))
    }

    @Test
    fun `creates a local media that the local file resolver can play`() {
        val file = Path("/videos/episode-01.mkv").inSystem
        val media = DroppedFileMedia.create(file)

        assertTrue(DroppedFileMedia.isDroppedFile(media))
        assertEquals("episode-01.mkv", media.originalTitle)
        assertEquals(MediaSourceLocation.Local, media.location)
        val download = assertIs<ResourceLocation.LocalFile>(media.download)
        assertEquals(file.absolutePath, download.filePath)
        assertNull(download.fileType)
        assertTrue(LocalFileMediaResolver().supports(media))
    }

    @Test
    fun `hints the player about MPEG-TS files`() {
        val download = assertIs<ResourceLocation.LocalFile>(
            DroppedFileMedia.create(Path("/videos/episode-01.TS").inSystem).download,
        )
        assertEquals(ResourceLocation.LocalFile.FileType.MPTS, download.fileType)
    }

    @Test
    fun `different files are different media and the same file is the same media`() {
        val first = DroppedFileMedia.create(Path("/videos/episode-01.mkv").inSystem)
        assertEquals(first, DroppedFileMedia.create(Path("/videos/episode-01.mkv").inSystem))
        assertNotEquals(first.mediaId, DroppedFileMedia.create(Path("/videos/episode-02.mkv").inSystem).mediaId)
    }
}

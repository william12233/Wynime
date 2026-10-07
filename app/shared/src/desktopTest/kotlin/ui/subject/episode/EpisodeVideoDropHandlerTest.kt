package com.wynime.app.ui.subject.episode

import kotlinx.io.files.Path
import com.wynime.app.ui.foundation.DragAndDropContent
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeVideoDropHandlerTest {
    private val played = mutableListOf<SystemPath>()
    private val handler = EpisodeVideoDropHandler { played += it }

    private fun files(vararg names: String) = DragAndDropContent.FileList(names.map { Path("/videos/$it") })

    @Test
    fun `takes over only file lists with a video file or unknown content`() {
        assertNotNull(handler.onDragStarted(files("episode-01.ass", "episode-01.mkv")))
        assertNotNull(handler.onDragStarted(null))

        assertNull(handler.onDragStarted(files("Wynime-4.12.0.dmg")))
        assertNull(handler.onDragStarted(files()))
        assertNull(handler.onDragStarted(DragAndDropContent.PlainText("/videos/episode-01.mkv")))
        assertNull(handler.onDragStarted(DragAndDropContent.Unsupported))
    }

    @Test
    fun `plays the first video file on drop`() {
        assertTrue(handler.onDrop(files("episode-01.ass", "episode-01.mkv", "episode-02.mkv")))
        assertEquals(listOf(Path("/videos/episode-01.mkv").inSystem), played)
    }

    @Test
    fun `ignores drops without a video file`() {
        assertFalse(handler.onDrop(files("Wynime-4.12.0.dmg")))
        assertFalse(handler.onDrop(DragAndDropContent.PlainText("/videos/episode-01.mkv")))
        assertFalse(handler.onDrop(DragAndDropContent.Unsupported))
        assertTrue(played.isEmpty())
    }
}

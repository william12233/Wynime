package com.wynime.app.ui.foundation

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WindowDropHostStateTest {
    private class FakeHandler(
        private val accepts: (DragAndDropContent?) -> Boolean,
        private val handles: Boolean = true,
    ) : WindowDropHandler {
        val dropped = mutableListOf<DragAndDropContent>()
        val preview = WindowDropPreview {}

        override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? =
            if (accepts(content)) preview else null

        override fun onDrop(content: DragAndDropContent): Boolean {
            dropped += content
            return handles
        }
    }

    private fun files(vararg names: String) = DragAndDropContent.FileList(names.map { Path("/downloads/$it") })

    @Test
    fun `first accepting handler takes over the session`() {
        val video = FakeHandler({ it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".mp4") } })
        val installer = FakeHandler({ it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".dmg") } })
        val state = WindowDropHostState()

        state.onDragStarted(files("wynime.dmg"), listOf(video, installer))
        val session = assertIs<WindowDropSession.Accepted>(state.session)
        assertSame(installer, session.handler)
        assertSame(installer.preview, session.preview)

        assertTrue(state.onDrop(files("wynime.dmg"), listOf(video, installer)))
        assertEquals(1, installer.dropped.size)
        assertEquals(0, video.dropped.size)

        state.onDragEnded()
        assertNull(state.session)
    }

    @Test
    fun `files nobody accepts are rejected with the first file name`() {
        val installer = FakeHandler({ false })
        val state = WindowDropHostState()

        state.onDragStarted(files("notes.txt", "wynime.dmg"), listOf(installer))
        assertEquals("notes.txt", assertIs<WindowDropSession.Rejected>(state.session).fileName)

        assertFalse(state.onDrop(files("notes.txt"), listOf(installer)))
        assertEquals(0, installer.dropped.size)
    }

    @Test
    fun `non-file content and empty handler list show nothing`() {
        val state = WindowDropHostState()

        state.onDragStarted(DragAndDropContent.PlainText("hello"), listOf(FakeHandler({ false })))
        assertNull(state.session)

        state.onDragStarted(files("wynime.dmg"), emptyList())
        assertNull(state.session)

        state.onDragStarted(null, listOf(FakeHandler({ false })))
        assertNull(state.session)
    }

    @Test
    fun `drop re-asks handlers when nothing took over during the drag`() {

        val installer = FakeHandler({ it is DragAndDropContent.FileList })
        val state = WindowDropHostState()

        state.onDragStarted(null, listOf(installer))
        assertNull(state.session)

        assertTrue(state.onDrop(files("wynime.dmg"), listOf(installer)))
        assertEquals(1, installer.dropped.size)
    }

    @Test
    fun `a handler that took over without content yields to the one matching the dropped content`() {

        val video = FakeHandler({ it == null || it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".mp4") } })
        val installer = FakeHandler({ it == null || it is DragAndDropContent.FileList && it.files.any { f -> f.name.endsWith(".dmg") } })
        val state = WindowDropHostState()

        state.onDragStarted(null, listOf(video, installer))
        assertSame(video, assertIs<WindowDropSession.Accepted>(state.session).handler)

        assertTrue(state.onDrop(files("wynime.dmg"), listOf(video, installer)))
        assertEquals(0, video.dropped.size)
        assertEquals(1, installer.dropped.size)
    }

    @Test
    fun `a handler that took over without content still receives content nobody matches`() {

        val installer = FakeHandler({ it == null }, handles = false)
        val state = WindowDropHostState()

        state.onDragStarted(null, listOf(installer))
        assertIs<WindowDropSession.Accepted>(state.session)

        assertFalse(state.onDrop(files("notes.txt"), listOf(installer)))
        assertEquals(1, installer.dropped.size)
    }

    @Test
    fun `a handler that took over by content keeps the session on drop`() {

        val first = FakeHandler({ it is DragAndDropContent.FileList })
        val state = WindowDropHostState()

        state.onDragStarted(files("episode-01.mp4"), listOf(first))
        val later = FakeHandler({ true })
        assertTrue(state.onDrop(files("episode-01.mp4"), listOf(later, first)))
        assertEquals(1, first.dropped.size)
        assertEquals(0, later.dropped.size)
    }
}

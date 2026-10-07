package com.wynime.app.ui.foundation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.io.files.Path
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class WindowDropHostTest {
    private class PlayHandler : WindowDropHandler {
        override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? {
            val file = (content as? DragAndDropContent.FileList)?.files?.firstOrNull { it.name.endsWith(".mp4") }
                ?: return null
            return WindowDropPreview {
                WindowDropCardContent(icon = Icons.Rounded.PlayArrow, title = "Release to play", subtitle = file.name)
            }
        }

        override fun onDrop(content: DragAndDropContent): Boolean = true

        @Composable
        override fun supportedHint(): String = "video files"
    }

    @Test
    fun `shows the handler preview while dragging and hides it afterwards`() = runWynimeComposeUiTest {
        val state = WindowDropHostState()
        val handlers = listOf(PlayHandler())
        setContent {
            ProvideCompositionLocalsForPreview {
                WindowDropHost(handlers, Modifier.fillMaxSize(), state) {
                    Text("content")
                }
            }
        }
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertDoesNotExist()

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/videos/episode-01.mp4"))), handlers)
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertIsDisplayed()
        onNodeWithText("Release to play").assertIsDisplayed()
        onNodeWithText("episode-01.mp4").assertIsDisplayed()
        onNodeWithText("content").assertIsDisplayed()

        state.onDragEnded()
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertDoesNotExist()
    }

    @Test
    fun `rejected files show the file name and the handlers' supported hints`() = runWynimeComposeUiTest {
        val state = WindowDropHostState()
        val handlers = listOf(PlayHandler())
        setContent {
            ProvideCompositionLocalsForPreview {
                WindowDropHost(handlers, Modifier.fillMaxSize(), state) {
                    Text("content")
                }
            }
        }

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/notes.txt"))), handlers)
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertIsDisplayed()
        onNodeWithText("notes.txt").assertIsDisplayed()
        onNodeWithText("video files", substring = true).assertIsDisplayed()
    }

    @Test
    fun `handlers registered by the content take part while composed`() = runWynimeComposeUiTest {
        val state = WindowDropHostState()
        val pageHandler = PlayHandler()
        var showPage by mutableStateOf(true)
        var registry: WindowDropHandlerRegistry? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                WindowDropHost(emptyList(), Modifier.fillMaxSize(), state) {
                    registry = LocalWindowDropHandlerRegistry.current
                    if (showPage) {
                        WindowDropHandlerEffect(pageHandler)
                    }
                    Text("content")
                }
            }
        }
        waitForIdle()
        assertEquals(listOf<WindowDropHandler>(pageHandler), registry!!.handlers)

        state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/notes.txt"))), registry!!.handlers)
        waitForIdle()
        onNodeWithText("video files", substring = true).assertIsDisplayed()
        state.onDragEnded()

        showPage = false
        waitForIdle()
        assertTrue(registry!!.handlers.isEmpty())
    }

    @Test
    fun `registering a handler outside a host has no effect`() = runWynimeComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                WindowDropHandlerEffect(PlayHandler())
                Text("content")
            }
        }
        onNodeWithText("content").assertIsDisplayed()
    }
}

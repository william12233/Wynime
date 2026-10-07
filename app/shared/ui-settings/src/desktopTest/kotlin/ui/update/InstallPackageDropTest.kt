package com.wynime.app.ui.update

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.io.files.Path
import com.wynime.app.platform.Context
import com.wynime.app.tools.update.InstallationFailureReason
import com.wynime.app.tools.update.InstallationResult
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.ui.foundation.DragAndDropContent
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.WindowDropHost
import com.wynime.app.ui.foundation.WindowDropHostState
import com.wynime.app.ui.foundation.WindowDropTestTags
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeInstaller(
    private val result: InstallationResult = InstallationResult.Succeed,
) : UpdateInstaller {
    override val installablePackageExtensions: Set<String> = setOf("dmg")
    val installed = mutableListOf<SystemPath>()

    override fun install(file: SystemPath, context: Context): InstallationResult {
        installed += file
        return result
    }
}

private val droppedPackage = Path("/downloads/Wynime-test.dmg")

class InstallPackageDropHandlerTest {
    @Test
    fun `takes over only file lists with an installable package or unknown content`() {
        val state = DropInstallPackageState(FakeInstaller())
        val handler = InstallPackageDropHandler(state, onUnsupported = {})

        assertNotNull(handler.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/notes.txt"), droppedPackage))))
        assertNotNull(handler.onDragStarted(null))
        assertNull(handler.onDragStarted(DragAndDropContent.FileList(listOf(Path("/downloads/notes.txt")))))
        assertNull(handler.onDragStarted(DragAndDropContent.PlainText("hello")))
        assertNull(handler.onDragStarted(DragAndDropContent.Unsupported))
    }

    @Test
    fun `drop records the pending package or reports unsupported files`() {
        val state = DropInstallPackageState(FakeInstaller())
        var unsupported = 0
        val handler = InstallPackageDropHandler(state, onUnsupported = { unsupported++ })

        assertTrue(handler.onDrop(DragAndDropContent.FileList(listOf(droppedPackage))))
        assertEquals(droppedPackage.inSystem, state.pendingPackage)

        state.dismissPending()
        assertFalse(handler.onDrop(DragAndDropContent.FileList(listOf(Path("/downloads/notes.txt")))))
        assertEquals(1, unsupported)
        assertNull(state.pendingPackage)

        assertFalse(handler.onDrop(DragAndDropContent.PlainText("hello")))
        assertEquals(1, unsupported)
    }
}

@OptIn(TestOnly::class)
class InstallPackageDropUiTest {
    @Test
    fun `overlay shows the dragged package and disappears when the drag ends`() = runWynimeComposeUiTest {
        val state = DropInstallPackageState(FakeInstaller())
        val host = WindowDropHostState()
        lateinit var handler: InstallPackageDropHandler
        setContent {
            ProvideCompositionLocalsForPreview {
                handler = rememberInstallPackageDropHandler(state)
                WindowDropHost(listOf(handler), Modifier.fillMaxSize(), host) {
                    Text("content")
                }
                InstallPackageDropDialogs(state)
            }
        }
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertDoesNotExist()

        host.onDragStarted(DragAndDropContent.FileList(listOf(droppedPackage)), listOf(handler))
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertIsDisplayed()
        onNodeWithText("Wynime-test.dmg").assertIsDisplayed()
        onNodeWithText("content").assertIsDisplayed()

        host.onDragEnded()
        waitForIdle()
        onNodeWithTag(WindowDropTestTags.OVERLAY).assertDoesNotExist()
        assertNull(state.pendingPackage)
    }

    @Test
    fun `confirming the dialog installs the dropped package`() = runWynimeComposeUiTest {
        val installer = FakeInstaller()
        val state = DropInstallPackageState(installer)
        setContent {
            ProvideCompositionLocalsForPreview {
                InstallPackageDropDialogs(state)
            }
        }

        state.offer(DragAndDropContent.FileList(listOf(droppedPackage)))
        waitForIdle()

        onNodeWithTag(DropInstallPackageTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

        waitUntil { installer.installed == listOf(droppedPackage.inSystem) }
        assertNull(state.pendingPackage)
    }

    @Test
    fun `cancelling the dialog does not install`() = runWynimeComposeUiTest {
        val installer = FakeInstaller()
        val state = DropInstallPackageState(installer)
        setContent {
            ProvideCompositionLocalsForPreview {
                InstallPackageDropDialogs(state)
            }
        }

        state.offer(DragAndDropContent.FileList(listOf(droppedPackage)))
        waitForIdle()

        onNodeWithTag(DropInstallPackageTestTags.CANCEL_BUTTON).performClick()
        waitForIdle()

        assertNull(state.pendingPackage)
        assertEquals(emptyList(), installer.installed)
        onNodeWithTag(DropInstallPackageTestTags.CONFIRM_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `installation failure is shown and can be dismissed`() = runWynimeComposeUiTest {
        val installer = FakeInstaller(
            result = InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE, "Not an app bundle"),
        )
        val state = DropInstallPackageState(installer)
        setContent {
            ProvideCompositionLocalsForPreview {
                InstallPackageDropDialogs(state)
            }
        }

        state.offer(DragAndDropContent.FileList(listOf(droppedPackage)))
        waitForIdle()
        onNodeWithTag(DropInstallPackageTestTags.CONFIRM_BUTTON).performClick()
        waitForIdle()

        onNodeWithText("Not an app bundle").assertIsDisplayed()
        onNodeWithTag(FailedToInstallDialogTestTags.DISMISS_BUTTON).performClick()
        waitForIdle()

        onNodeWithText("Not an app bundle").assertDoesNotExist()
    }
}

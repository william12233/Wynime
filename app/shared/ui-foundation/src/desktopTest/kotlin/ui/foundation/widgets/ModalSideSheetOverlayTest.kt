package com.wynime.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModalSideSheetOverlayTest {
    @Test
    fun `overlay fills the whole window instead of the sheet`() = runWynimeComposeUiTest {
        setContent {
            ModalSideSheet(
                onDismiss = {},
                modifier = Modifier.width(100.dp),
                overlay = { Box(Modifier.fillMaxSize().testTag("overlay")) },
            ) {
                Box(Modifier.fillMaxSize().testTag("content"))
            }
        }
        waitForIdle()

        val content = onNodeWithTag("content").getUnclippedBoundsInRoot()
        val overlay = onNodeWithTag("overlay").getUnclippedBoundsInRoot()
        assertEquals(100.dp, content.right - content.left)
        assertTrue(overlay.right - overlay.left > 100.dp, "overlay width = ${overlay.right - overlay.left}")
    }
}

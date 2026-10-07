package com.wynime.app.ui.foundation.interaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearFocusOnTapTest {
    @Test
    fun `does not merge a descendant pane title`() = runWynimeComposeUiTest {
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .clearFocusOnUnhandledTap()
                    .testTag("root"),
            ) {
                Box(Modifier.semantics { paneTitle = "Reminder" })
            }
        }

        onNodeWithTag("root").fetchSemanticsNode()
    }

    @Test
    fun `handled descendant tap keeps focus while background tap clears it`() = runWynimeComposeUiTest {
        val focusRequester = FocusRequester()
        val keyboard = RecordingSoftwareKeyboardController()
        setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                Box(
                    Modifier
                        .size(100.dp)
                        .clearFocusOnUnhandledTap()
                        .testTag("root"),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .focusRequester(focusRequester)
                            .clickable {}
                            .testTag("child"),
                    )
                }
            }
        }

        runOnIdle { focusRequester.requestFocus() }
        onNodeWithTag("child")
            .assertIsFocused()
            .performTouchInput { click() }
            .assertIsFocused()
        runOnIdle { assertEquals(0, keyboard.hideCount) }

        onNodeWithTag("root", useUnmergedTree = true).performTouchInput {
            click(bottomRight - Offset(1f, 1f))
        }
        onNodeWithTag("child").assertIsNotFocused()
        runOnIdle { assertEquals(1, keyboard.hideCount) }
    }

    private class RecordingSoftwareKeyboardController : SoftwareKeyboardController {
        var hideCount = 0
            private set

        override fun show() = Unit

        override fun hide() {
            hideCount++
        }
    }
}

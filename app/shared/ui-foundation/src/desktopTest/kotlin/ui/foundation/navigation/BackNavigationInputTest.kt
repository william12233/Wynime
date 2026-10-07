package com.wynime.app.ui.foundation.navigation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BackNavigationInputTest {
    @Test
    fun `back side button invokes callback once`() = runWynimeComposeUiTest {
        var backCount = 0
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ }
                    .testTag("target"),
            )
        }

        onNodeWithTag("target").performMouseInput {
            click(button = MouseButton(PointerButton.Back.index))
        }

        runOnIdle {
            assertEquals(1, backCount)
        }
    }

    @Test
    fun `escape invokes callback once`() = runWynimeComposeUiTest {
        var backCount = 0
        val focusRequester = FocusRequester()
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ }
                    .focusRequester(focusRequester)
                    .focusable()
                    .testTag("target"),
            )
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        onNodeWithTag("target")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Escape) }

        runOnIdle {
            assertEquals(1, backCount)
        }
    }

    @Test
    fun `escape key up without seeing key down does not invoke callback`() = runWynimeComposeUiTest {
        var backCount = 0
        val focusRequester = FocusRequester()
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ },
            ) {
                Box(
                    Modifier
                        .size(50.dp)
                        .onKeyEvent { it.key == Key.Escape && it.type == KeyEventType.KeyDown }
                        .focusRequester(focusRequester)
                        .focusable()
                        .testTag("child"),
                )
            }
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        onNodeWithTag("child")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Escape) }

        runOnIdle {
            assertEquals(0, backCount)
        }
    }

    @Test
    fun `other mouse buttons do not invoke callback`() = runWynimeComposeUiTest {
        var backCount = 0
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ }
                    .testTag("target"),
            )
        }

        onNodeWithTag("target").performMouseInput {
            click(button = MouseButton.Primary)
            click(button = MouseButton.Secondary)
            click(button = MouseButton.Tertiary)
            click(button = MouseButton(PointerButton.Forward.index))
        }

        runOnIdle {
            assertEquals(0, backCount)
        }
    }
}

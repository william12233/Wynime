package com.wynime.app.videoplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.animation.ProvideWynimeMotionCompositionLocals
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.utils.platform.Arch
import com.wynime.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerControlFocusTest {
    @Test
    fun `desktop control click restores player keyboard focus`() = runWynimeComposeUiTest {
        val controllerState = PlayerControllerState(ControllerVisibility.Visible)
        var shortcutCount = 0

        setContent {
            CompositionLocalProvider(LocalPlatform provides Platform.Windows(Arch.X86_64)) {
                ProvideWynimeMotionCompositionLocals {
                    MaterialTheme {
                        LaunchedEffect(Unit) {
                            controllerState.requestPlayerFocus()
                        }

                        VideoScaffold(
                            expanded = true,
                            maintainAspectRatio = false,
                            controllerState = controllerState,
                            modifier = Modifier.fillMaxSize(),
                            video = {},
                            gestureHost = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .focusRequester(controllerState.focusState.requester)
                                        .focusable()
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyUp && event.key == Key.Spacebar) {
                                                shortcutCount++
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .testTag("playerGestureHost"),
                                )
                            },
                            bottomBar = {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clickable {}
                                        .testTag("playerControl"),
                                )
                            },
                        )
                    }
                }
            }
        }

        onNodeWithTag("playerGestureHost").assertIsFocused()

        onNodeWithTag("playerControl").performMouseInput {
            click()
        }

        onNodeWithTag("playerGestureHost")
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.Spacebar)
            }

        runOnIdle {
            assertEquals(1, shortcutCount)
        }
    }
}

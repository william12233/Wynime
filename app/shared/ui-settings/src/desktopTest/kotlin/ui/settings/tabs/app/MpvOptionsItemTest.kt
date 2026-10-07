package com.wynime.app.ui.settings.tabs.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.wynime.app.data.models.preference.PlayerKernelConfig
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.settings.SettingsTab
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.framework.rememberTestSettingsState
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class MpvOptionsItemTest {
    @Test
    fun `confirming the dialog saves all entered lines`() = runWynimeComposeUiTest {
        lateinit var state: SettingsState<PlayerKernelConfig>
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    state = rememberTestSettingsState(PlayerKernelConfig.Default)
                    PlayerGroupPlatform(rememberTestSettingsState(VideoScaffoldConfig.Default), state)
                }
            }
        }

        onNodeWithTag(MpvOptionsItemTestTags.ITEM).performClick()
        waitForIdle()

        onNodeWithTag(MpvOptionsItemTestTags.TEXT_FIELD).performTextInput("hwdec=auto\nprofile=fast")
        onNodeWithText("确认").performClick()

        waitUntil { state.value.mpvOptions == listOf("hwdec=auto", "profile=fast") }
    }

    @Test
    fun `dialog shows the saved options and does not write before confirming`() = runWynimeComposeUiTest {
        lateinit var state: SettingsState<PlayerKernelConfig>
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab {
                    state = rememberTestSettingsState(
                        PlayerKernelConfig.Default.copy(mpvOptions = listOf("hwdec=auto")),
                    )
                    PlayerGroupPlatform(rememberTestSettingsState(VideoScaffoldConfig.Default), state)
                }
            }
        }

        onNodeWithTag(MpvOptionsItemTestTags.ITEM).performClick()
        waitForIdle()

        onNodeWithTag(MpvOptionsItemTestTags.TEXT_FIELD).assertTextEquals("hwdec=auto")

        onNodeWithTag(MpvOptionsItemTestTags.TEXT_FIELD).performTextInput("\nprofile=fast")
        waitForIdle()

        assertEquals(listOf("hwdec=auto"), state.value.mpvOptions)
    }
}

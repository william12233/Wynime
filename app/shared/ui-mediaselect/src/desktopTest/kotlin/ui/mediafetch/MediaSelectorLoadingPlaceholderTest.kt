package com.wynime.app.ui.mediafetch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithText
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MediaSelectorLoadingPlaceholderTest {
    @Test
    fun `loading selector does not present fixture source names`() = runWynimeComposeUiTest {
        setContent {
            MediaSelectorLoadingPlaceholder(Modifier.fillMaxSize())
        }

        onNodeWithText("source1").assertDoesNotExist()
        onNodeWithText("source4").assertDoesNotExist()
    }
}

package com.wynime.app.ui.mediafetch

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaSelectorLocalCacheChoiceTest {
    @Test
    fun `local cache remains selectable until online query is requested`() = runWynimeComposeUiTest {
        var selectedCacheId: String? = null
        var queriedOnlineSources = false

        setContent {
            var onlineQueryEnabled by remember { mutableStateOf(false) }
            MediaSelectorView(
                state = null,
                fetchRequest = null,
                onFetchRequestChange = {},
                onRestartSource = {},
                localCacheOptions = listOf(
                    MediaSelectorLocalCacheOption(
                        id = "cache-9",
                        title = "Series episode 9",
                        description = "Group · 1080P",
                    ),
                ),
                selectedLocalCacheId = "cache-9",
                onlineQueryEnabled = onlineQueryEnabled,
                onSelectLocalCache = { selectedCacheId = it },
                onQueryOnlineSources = {
                    queriedOnlineSources = true
                    onlineQueryEnabled = true
                },
            )
        }

        onNodeWithTag("media-selector-local-cache-cache-9").assertExists()
        onNodeWithText("source1").assertDoesNotExist()
        onNodeWithText("source4").assertDoesNotExist()
        onNodeWithTag("media-selector-query-online").performClick()
        assertTrue(queriedOnlineSources)
        onNodeWithTag("media-selector-local-cache-cache-9").assertExists()

        onNodeWithTag("media-selector-local-cache-cache-9").performClick()
        assertEquals("cache-9", selectedCacheId)
    }
}

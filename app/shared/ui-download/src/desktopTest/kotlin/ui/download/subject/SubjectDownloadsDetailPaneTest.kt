package com.wynime.app.ui.download.subject

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import com.wynime.app.ui.download.components.rememberDownloadSelectionState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.downloads_empty
import org.jetbrains.compose.resources.getString

class SubjectDownloadsDetailPaneTest {
    @Test
    fun `pane without presenter shows the loading state`() = runWynimeComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsDetailPane(
                    presenter = null,
                    loadingTitle = "Loading subject",
                    selectionState = rememberDownloadSelectionState(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithTag(SubjectDownloadsTestTags.LOADING).assertExists()
        onNodeWithText("Loading subject").assertExists()
        onNodeWithText(runBlocking { getString(Lang.downloads_empty) }).assertDoesNotExist()
    }
}

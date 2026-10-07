package com.wynime.app.ui.download.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import com.wynime.app.ui.download.DownloadManagementTestTags
import com.wynime.app.tools.toProgress
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.lang.cache_episode_pause_download
import com.wynime.app.ui.lang.cache_management_more_actions
import com.wynime.app.ui.lang.cache_subject_delete
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_episode_watched_progress
import com.wynime.app.ui.lang.cache_filter_status_finished
import com.wynime.datasources.api.topic.FileSize
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class DownloadRowUiTest {
    @Test
    fun `busy state disables row actions and an open delete confirmation`() = runWynimeComposeUiTest {
        var episode by mutableStateOf(createTestDownloadItem(1, initialState = DownloadStatus.IN_PROGRESS).copy(isBusy = true))
        var deletions = 0
        val pause = runBlocking { getString(Lang.cache_episode_pause_download) }
        val delete = runBlocking { getString(Lang.cache_subject_delete) }
        setContent {
            ProvideCompositionLocalsForPreview {
                DownloadRow(
                    episode = episode,
                    mediaSourceInfoProvider = null,
                    selectionMode = false,
                    selected = false,
                    onToggleSelected = {},
                    onEnterSelection = {},
                    onPlay = {},
                    onResume = {},
                    onPause = {},
                    onDelete = { deletions++ },
                    onViewDetail = null,
                )
            }
        }
        onNodeWithContentDescription(pause).assertIsNotEnabled()
        onNodeWithContentDescription(runBlocking { getString(Lang.cache_management_more_actions) }).performClick()
        onNodeWithText(pause).assertIsNotEnabled()
        onNodeWithText(delete).assertIsNotEnabled()
        runOnIdle { episode = episode.copy(isBusy = false) }
        onNodeWithText(delete).assertIsEnabled().performClick()
        runOnIdle { episode = episode.copy(isBusy = true) }
        onNodeWithTag(DownloadManagementTestTags.DELETE_CONFIRM_BUTTON).assertIsNotEnabled()
        runOnIdle { episode = episode.copy(isBusy = false) }
        onNodeWithTag(DownloadManagementTestTags.DELETE_CONFIRM_BUTTON).assertIsEnabled().performClick()
        runOnIdle { assertEquals(1, deletions) }
    }

    @Test
    fun `completed cache shows watched progress beside finished without progress semantics`() = runWynimeComposeUiTest {
        val episode = createTestDownloadItem(
            sort = 1,
            initialState = DownloadStatus.COMPLETED,
            progress = 1f.toProgress(),
            downloadSpeed = FileSize.Unspecified,
            playbackProgress = 0.5f.toProgress(),
        )
        val metadataText = runBlocking {
            "${episode.detailedSizeText} · ${getString(Lang.cache_filter_status_finished)} · " +
                    getString(Lang.cache_episode_watched_progress, "50.0%")
        }

        setContent {
            ProvideCompositionLocalsForPreview {
                DownloadRow(
                    episode = episode,
                    mediaSourceInfoProvider = null,
                    selectionMode = false,
                    selected = false,
                    onToggleSelected = {},
                    onEnterSelection = {},
                    onPlay = {},
                    onResume = {},
                    onPause = {},
                    onDelete = {},
                    onViewDetail = null,
                )
            }
        }

        onNodeWithText(metadataText, useUnmergedTree = true).assertExists()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
    }
}

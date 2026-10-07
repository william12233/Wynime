package com.wynime.app.ui.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.tools.toProgress
import com.wynime.app.ui.download.components.DownloadSelectionToolbarTestTags
import com.wynime.app.ui.download.components.DownloadStatus
import com.wynime.app.ui.download.components.SubjectDownloadGroup
import com.wynime.app.ui.download.components.createTestDownloadItem
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_management_enter_selection_mode
import com.wynime.app.ui.lang.cache_management_select_all
import com.wynime.app.ui.lang.cache_management_selected_count
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class DownloadManagementSelectionTest {
    private val inProgress = createTestDownloadItem(1, initialState = DownloadStatus.IN_PROGRESS)
    private val paused = createTestDownloadItem(2, initialState = DownloadStatus.PAUSED)
    private val finished = createTestDownloadItem(
        3,
        initialState = DownloadStatus.COMPLETED,
        progress = 1f.toProgress(),
    )
    private val failed = createTestDownloadItem(4, initialState = DownloadStatus.FAILED)
    private val episodes = listOf(inProgress, paused, finished, failed)

    private val state = DownloadManagementUiState(
        MediaStats.Unspecified,
        listOf(
            SubjectDownloadGroup(
                subjectId = 1,
                subjectName = "孤独摇滚",
                entries = episodes,
                collectionType = UnifiedCollectionType.DOING,
            ),
        ),
    )

    @Test
    fun `global selection disables actions while any selected download is busy`() = runWynimeComposeUiTest {
        var currentState by mutableStateOf(state.copy(groups = state.groups.map { group ->
            group.copy(entries = group.entries.map { it.copy(isBusy = it.id == inProgress.id) })
        }))
        setContent {
            ProvideCompositionLocalsForPreview {
                DownloadManagementScreen(
                    state = currentState,
                    selfInfo = null,
                    onPlay = {}, onResume = {}, onPause = {}, onViewDetail = {}, onDelete = {}, onClickLogin = {},
                )
            }
        }
        onNodeWithContentDescription(runBlocking { getString(Lang.cache_management_enter_selection_mode) }).performClick()
        onNodeWithContentDescription(runBlocking { getString(Lang.cache_management_select_all) }).performClick()
        onNodeWithTag(DownloadSelectionToolbarTestTags.PAUSE).assertIsNotEnabled()
        onNodeWithTag(DownloadSelectionToolbarTestTags.RESUME).assertIsNotEnabled()
        onNodeWithTag(DownloadSelectionToolbarTestTags.DELETE).assertIsNotEnabled()
        runOnIdle { currentState = state }
        onNodeWithTag(DownloadSelectionToolbarTestTags.PAUSE).assertIsEnabled()
        onNodeWithTag(DownloadSelectionToolbarTestTags.RESUME).assertIsEnabled()
        onNodeWithTag(DownloadSelectionToolbarTestTags.DELETE).assertIsEnabled()
    }

    @Test
    fun `batch actions keep all selected ids until download state catches up`() = runWynimeComposeUiTest {
        val enterSelectionText = runBlocking { getString(Lang.cache_management_enter_selection_mode) }
        val selectAllText = runBlocking { getString(Lang.cache_management_select_all) }
        val selectedCountText = runBlocking { getString(Lang.cache_management_selected_count, episodes.size) }

        val resumedIds = mutableSetOf<String>()
        val pausedIds = mutableSetOf<String>()
        val deletedIds = mutableSetOf<String>()

        var currentState by mutableStateOf(state)
        setContent {
            ProvideCompositionLocalsForPreview {
                DownloadManagementScreen(
                    state = currentState,
                    selfInfo = null,
                    onPlay = {},
                    onResume = { resumedIds += it.id },
                    onPause = { pausedIds += it.id },
                    onViewDetail = {},
                    onDelete = { item ->
                        deletedIds += item.id
                        currentState = currentState.copy(groups = currentState.groups.map { group ->
                            group.copy(entries = group.entries.filterNot { it.id == item.id })
                        }.filter { it.entries.isNotEmpty() })
                    },
                    onClickLogin = {},
                )
            }
        }

        onNodeWithContentDescription(enterSelectionText).performClick()
        onNodeWithContentDescription(selectAllText).performClick()
        onNodeWithText(selectedCountText).assertExists()

        onNodeWithTag(DownloadSelectionToolbarTestTags.RESUME).performClick()
        runOnIdle {
            assertEquals(episodes.map { it.id }.toSet(), resumedIds)
        }

        onNodeWithTag(DownloadSelectionToolbarTestTags.PAUSE).performClick()
        runOnIdle {
            assertEquals(episodes.map { it.id }.toSet(), pausedIds)
        }

        onNodeWithTag(DownloadSelectionToolbarTestTags.DELETE).performClick()
        onNodeWithTag(DownloadManagementTestTags.DELETE_CONFIRM_BUTTON).performClick()
        runOnIdle {
            assertEquals(episodes.map { it.id }.toSet(), deletedIds)
        }
        onNodeWithText(selectedCountText).assertDoesNotExist()
    }
}

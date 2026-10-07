package com.wynime.app.ui.download.subject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import com.wynime.app.tools.toProgress
import com.wynime.app.ui.download.DownloadManagementTestTags
import com.wynime.app.ui.download.components.DownloadSelectionToolbarTestTags
import com.wynime.app.ui.download.components.DownloadStatus
import com.wynime.app.ui.download.components.createTestDownloadItem
import com.wynime.app.ui.download.components.rememberDownloadSelectionState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_management_episode_label
import com.wynime.app.ui.lang.cache_management_select_all_action
import com.wynime.app.ui.lang.cache_management_selected_count
import com.wynime.app.ui.lang.cache_subject_cache
import com.wynime.app.ui.lang.cache_subject_cancel
import com.wynime.app.ui.lang.cache_subject_pause_all
import com.wynime.app.ui.mediafetch.createTestMediaSourceInfoProvider
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class SubjectDownloadsPageTest {
    private val finished = createTestDownloadItem(1, progress = 1f.toProgress(), initialState = DownloadStatus.COMPLETED)
    private val downloading = createTestDownloadItem(2, initialState = DownloadStatus.IN_PROGRESS)
    private val paused = createTestDownloadItem(3, initialState = DownloadStatus.PAUSED)
    private val downloads = listOf(finished, downloading, paused)
    private val episode = EpisodeDownloadItem(4, EpisodeSort(4), "Episode 4", UnifiedCollectionType.DOING, true)

    @Test
    fun `shows downloads and new episode and dispatches download and pause all`() = runWynimeComposeUiTest {
        var requested: Int? = null
        var pauseAll = false
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsPage(
                    state = SubjectDownloadsUiState(
                        title = "Subject",
                        items = buildSubjectDownloadItems(listOf(episode), downloads),
                        downloads = downloads,
                        episodesLoading = false,
                        downloadsLoading = false,
                    ),
                    selection = rememberDownloadSelectionState(),
                    actions = actions(download = { requested = it }, pauseAll = { pauseAll = true }),
                    sourceInfoProvider = createTestMediaSourceInfoProvider(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_management_episode_label, episode.sort, "Episode 4") }).assertExists()
        onNodeWithContentDescription(runBlocking { getString(Lang.cache_subject_cache) }).performClick()
        onNodeWithText(runBlocking { getString(Lang.cache_subject_pause_all) }).performClick()
        runOnIdle { assertEquals(4, requested); assertEquals(true, pauseAll) }
    }

    @Test
    fun `busy request disables download buttons of other episodes`() = runWynimeComposeUiTest {
        val other = EpisodeDownloadItem(5, EpisodeSort(5), "Episode 5", UnifiedCollectionType.DOING, true)
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsPage(
                    state = SubjectDownloadsUiState(
                        title = "Subject",
                        items = buildSubjectDownloadItems(listOf(episode, other), emptyList()),
                        episodesLoading = false,
                        downloadsLoading = false,
                        request = DownloadRequestUiState(episodeIds = setOf(episode.episodeId), busy = true, canCancel = true),
                    ),
                    selection = rememberDownloadSelectionState(),
                    actions = actions(),
                    sourceInfoProvider = createTestMediaSourceInfoProvider(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }

        onNodeWithContentDescription(runBlocking { getString(Lang.cache_subject_cancel) }).assertIsEnabled()
        onNodeWithContentDescription(runBlocking { getString(Lang.cache_subject_cache) }).assertIsNotEnabled()
    }

    @Test
    fun `busy batch disables actions until the operation finishes`() = runWynimeComposeUiTest {
        var currentDownloads by mutableStateOf(downloads)
        val commands = mutableListOf<Pair<String, Set<String>>>()
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsPage(
                    state = SubjectDownloadsUiState(
                        title = "Subject",
                        items = buildSubjectDownloadItems(emptyList(), currentDownloads),
                        downloads = currentDownloads,
                        episodesLoading = false,
                        downloadsLoading = false,
                    ),
                    selection = rememberDownloadSelectionState(),
                    actions = actions(
                        pause = { ids ->
                            commands += "pause" to ids
                            currentDownloads = currentDownloads.map { it.copy(isBusy = it.id in ids) }
                        },
                        resume = { commands += "resume" to it },
                    ),
                    sourceInfoProvider = createTestMediaSourceInfoProvider(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_management_episode_label, finished.sort, finished.displayName) })
            .performTouchInput { longClick() }
        onNodeWithText(runBlocking { getString(Lang.cache_management_select_all_action) }).performClick()
        onNodeWithTag(DownloadSelectionToolbarTestTags.PAUSE).performClick()
        onNodeWithTag(DownloadSelectionToolbarTestTags.PAUSE).assertIsNotEnabled()
        onNodeWithTag(DownloadSelectionToolbarTestTags.RESUME).assertIsNotEnabled()
        onNodeWithTag(DownloadSelectionToolbarTestTags.DELETE).assertIsNotEnabled()
        runOnIdle { currentDownloads = currentDownloads.map { it.copy(isBusy = false) } }
        onNodeWithTag(DownloadSelectionToolbarTestTags.RESUME).assertIsEnabled().performClick()
        runOnIdle {
            val ids: Set<String> = downloads.mapTo(hashSetOf()) { it.id }
            assertEquals(listOf("pause" to ids, "resume" to ids), commands)
        }
    }

    @Test
    fun `partial deletion keeps failed item selected and completed deletion exits selection`() = runWynimeComposeUiTest {
        var currentDownloads by mutableStateOf(downloads)
        var requestedIds: Set<String>? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDownloadsPage(
                    state = SubjectDownloadsUiState(
                        title = "Subject",
                        items = buildSubjectDownloadItems(emptyList(), currentDownloads),
                        downloads = currentDownloads,
                        episodesLoading = false,
                        downloadsLoading = false,
                    ),
                    selection = rememberDownloadSelectionState(),
                    actions = actions(delete = { requestedIds = it; currentDownloads = listOf(paused) }),
                    sourceInfoProvider = createTestMediaSourceInfoProvider(),
                    onPlay = {},
                    onViewDetail = null,
                )
            }
        }
        onNodeWithText(runBlocking { getString(Lang.cache_management_episode_label, finished.sort, finished.displayName) })
            .performTouchInput { longClick() }
        onNodeWithText(runBlocking { getString(Lang.cache_management_select_all_action) }).performClick()
        onNodeWithTag(DownloadSelectionToolbarTestTags.DELETE).performClick()
        onNodeWithTag(DownloadManagementTestTags.DELETE_CONFIRM_BUTTON).performClick()
        runOnIdle { assertEquals(downloads.mapTo(hashSetOf()) { it.id }, requestedIds) }
        onNodeWithText(runBlocking { getString(Lang.cache_management_selected_count, 1) }).assertExists()
        runOnIdle { currentDownloads = emptyList() }
        onNodeWithText(runBlocking { getString(Lang.cache_management_selected_count, 1) }).assertDoesNotExist()
    }

    private fun actions(
        download: (Int) -> Unit = {},
        pause: (Set<String>) -> Unit = {},
        resume: (Set<String>) -> Unit = {},
        delete: (Set<String>) -> Unit = {},
        pauseAll: () -> Unit = {},
    ) = SubjectDownloadActions(download, {}, pause, resume, delete, pauseAll, {}, {})
}

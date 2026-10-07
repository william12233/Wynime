package com.wynime.app.ui.download.subject

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.download.DownloadEpisodeOption
import com.wynime.app.domain.media.download.DownloadEpisodeOption.Availability
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.downloads_episode_picker_downloaded
import com.wynime.app.ui.lang.downloads_episode_picker_line
import com.wynime.app.ui.lang.downloads_episode_picker_selected_count
import com.wynime.app.ui.lang.downloads_episode_picker_unmatched
import com.wynime.datasources.api.EpisodeSort
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class DownloadEpisodePickerTest {
    private val options = listOf(
        DownloadEpisodeOption(1, EpisodeSort(1), "Episode 1", Availability.AVAILABLE, "[Pack] 01-02", isCurrent = true),
        DownloadEpisodeOption(2, EpisodeSort(2), "Episode 2", Availability.AVAILABLE, "[Pack] 01-02", isCurrent = false),
        DownloadEpisodeOption(3, EpisodeSort(3), "Episode 3", Availability.ALREADY_DOWNLOADED, null, isCurrent = false),
        DownloadEpisodeOption(4, EpisodeSort(4), "Episode 4", Availability.UNMATCHED, null, isCurrent = false),
    )
    private val state = DownloadEpisodePickerState(episodeId = 1, chosen = TestMediaList.first(), options = options)

    private fun selectedCount(count: Int) = runBlocking { getString(Lang.downloads_episode_picker_selected_count, count) }

    @Test
    fun `defaults to the current episode onwards and confirms the toggled selection`() = runWynimeComposeUiTest {
        var confirmed: Set<Int>? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                DownloadEpisodePicker(state, onBack = {}, onConfirm = { confirmed = it })
            }
        }
        onNodeWithText(runBlocking { getString(Lang.downloads_episode_picker_line, "桜都字幕组") }).assertExists()
        onNodeWithText(runBlocking { getString(Lang.downloads_episode_picker_downloaded) }).assertExists()
        onNodeWithText(runBlocking { getString(Lang.downloads_episode_picker_unmatched) }).assertExists()
        onNodeWithTag(DownloadEpisodePickerTestTags.SELECTED_COUNT).assertTextEquals(selectedCount(2))
        onNodeWithTag(DownloadEpisodePickerTestTags.row(3)).assertIsNotEnabled()
        onNodeWithTag(DownloadEpisodePickerTestTags.row(4)).assertIsNotEnabled()

        onNodeWithTag(DownloadEpisodePickerTestTags.ONLY_CURRENT).performClick()
        onNodeWithTag(DownloadEpisodePickerTestTags.SELECTED_COUNT).assertTextEquals(selectedCount(1))
        onNodeWithTag(DownloadEpisodePickerTestTags.row(2)).performClick()
        onNodeWithTag(DownloadEpisodePickerTestTags.SELECTED_COUNT).assertTextEquals(selectedCount(2))
        onNodeWithTag(DownloadEpisodePickerTestTags.row(1)).performClick()
        onNodeWithTag(DownloadEpisodePickerTestTags.SELECTED_COUNT).assertTextEquals(selectedCount(1))

        onNodeWithTag(DownloadEpisodePickerTestTags.CONFIRM).assertIsEnabled().performClick()
        runOnIdle { assertEquals(setOf(2), confirmed) }
    }

    @Test
    fun `all selects every available episode and empty selection disables confirm`() = runWynimeComposeUiTest {
        var back = false
        setContent {
            ProvideCompositionLocalsForPreview {
                DownloadEpisodePicker(state, onBack = { back = true }, onConfirm = {})
            }
        }
        onNodeWithTag(DownloadEpisodePickerTestTags.ONLY_CURRENT).performClick()
        onNodeWithTag(DownloadEpisodePickerTestTags.row(1)).performClick()
        onNodeWithTag(DownloadEpisodePickerTestTags.SELECTED_COUNT).assertTextEquals(selectedCount(0))
        onNodeWithTag(DownloadEpisodePickerTestTags.CONFIRM).assertIsNotEnabled()

        onNodeWithTag(DownloadEpisodePickerTestTags.ALL).performClick()
        onNodeWithTag(DownloadEpisodePickerTestTags.SELECTED_COUNT).assertTextEquals(selectedCount(2))

        onNodeWithTag(DownloadEpisodePickerTestTags.BACK).performClick()
        runOnIdle { assertTrue(back) }
    }
}

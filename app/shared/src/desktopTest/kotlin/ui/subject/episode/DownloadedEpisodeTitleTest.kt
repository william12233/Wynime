package com.wynime.app.ui.subject.episode

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.MediaCacheMetadata
import kotlin.test.Test

class DownloadedEpisodeTitleTest {
    @Test
    fun `renders locally stored titles and episode numbers without remote metadata`() = runWynimeComposeUiTest {
        val metadata = MediaCacheMetadata(
            subjectId = "21",
            episodeId = "123",
            subjectNames = listOf("Original title"),
            subjectNameCN = "離線作品",
            episodeName = "下載集數",
            episodeSort = EpisodeSort(2),
        )
        val subject = metadata.toSubjectInfo(21).toPresentation()
        val episode = metadata.toEpisodePresentation()
        setContent {
            MaterialTheme {
                EpisodePlayerTitle(
                    ep = episode.ep,
                    episodeTitle = episode.title,
                    subjectTitle = subject.title,
                    isEpisodeLoading = episode.isPlaceholder,
                    isSubjectLoading = subject.isPlaceholder,
                )
            }
        }
        onAllNodesWithText("離線作品").onFirst().assertIsDisplayed()
        onAllNodesWithText("02  下載集數").onFirst().assertIsDisplayed()
    }
}

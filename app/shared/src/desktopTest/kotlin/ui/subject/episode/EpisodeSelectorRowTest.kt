package com.wynime.app.ui.subject.episode

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import kotlinx.coroutines.runBlocking
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_episode_watched
import com.wynime.app.ui.subject.episode.video.sidesheet.EpisodeSelectorRow
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeSelectorRowTest {
    @Test
    fun longPressChangesWatchedStateWithoutSelectingTheEpisode() = runWynimeComposeUiTest {
        val status = mutableStateOf(UnifiedCollectionType.NOT_COLLECTED)
        var selected = 0
        setContent {
            MaterialTheme {
                EpisodeSelectorRow(
                    item = EpisodePresentation(
                        episodeId = 1,
                        title = "Episode one",
                        ep = "01",
                        sort = "01",
                        collectionType = status.value,
                        isKnownBroadcast = true,
                        isDownloaded = true,
                    ),
                    selected = false,
                    onClick = { selected++ },
                    onToggleWatched = {
                        status.value = if (status.value == UnifiedCollectionType.DONE) {
                            UnifiedCollectionType.NOT_COLLECTED
                        } else {
                            UnifiedCollectionType.DONE
                        }
                    },
                )
            }
        }

        val row = onNodeWithText("Episode one")
        val watchedLabel = runBlocking { getString(Lang.subject_episode_watched) }
        val rowHeight = row.fetchSemanticsNode().boundsInRoot.height
        row.performTouchInput { longClick() }
        runOnIdle {
            assertEquals(UnifiedCollectionType.DONE, status.value)
            assertEquals(0, selected)
        }
        onNodeWithText(watchedLabel, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(rowHeight, row.fetchSemanticsNode().boundsInRoot.height)
        row.performTouchInput { longClick() }
        runOnIdle {
            assertEquals(UnifiedCollectionType.NOT_COLLECTED, status.value)
            assertEquals(0, selected)
        }
        row.performClick()
        runOnIdle { assertEquals(1, selected) }
    }

    @Test
    fun localWatchStatusIsKeptWhenAddingPublicAndDownloadedEpisodes() {
        val first = EpisodeInfo(1, EpisodeType.MainStory, sort = EpisodeSort(1))
        val public = EpisodeInfo(3, EpisodeType.MainStory, sort = EpisodeSort(3))
        val special = EpisodeInfo(4, EpisodeType.SP, sort = EpisodeSort("SP1"))
        val downloaded = MediaCacheMetadata(
            subjectId = "10",
            episodeId = "2",
            subjectNames = listOf("Series"),
            episodeName = "Downloaded second episode",
            episodeSort = EpisodeSort(2),
        )
        val result = mergeEpisodeCatalog(
            local = listOf(EpisodeCollectionInfo(first, UnifiedCollectionType.DONE)),
            catalog = listOf(public, special, first),
            downloads = listOf(downloaded),
            types = listOf(EpisodeType.MainStory),
        )
        assertEquals(listOf(1, 2, 3), result.map { it.episodeId })
        assertEquals(UnifiedCollectionType.DONE, result.first().collectionType)
        assertEquals("Downloaded second episode", result[1].episodeInfo.name)
    }
}

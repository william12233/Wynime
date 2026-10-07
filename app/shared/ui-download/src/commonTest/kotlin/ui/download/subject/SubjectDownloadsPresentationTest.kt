package com.wynime.app.ui.download.subject

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.createTestSubjectCollection
import com.wynime.app.ui.download.components.createTestDownloadItem
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class SubjectDownloadsPresentationTest {
    @Test
    fun `downloadEpisodes_carries_both_the_Chinese_and_original_episode_name`() {
        val collection = createTestSubjectCollection(
            1,
            listOf(
                EpisodeCollectionInfo(
                    episodeInfo = EpisodeInfo(
                        episodeId = 1,
                        type = EpisodeType.MainStory,
                        sort = EpisodeSort(1),
                        name = "転がる岩、君に朝が降る",
                        nameCn = "滚石与朝阳",
                    ),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            ),
            UnifiedCollectionType.DOING,
        )
        val item = collection.downloadEpisodes().single()
        assertEquals("滚石与朝阳", item.title)
        assertEquals("転がる岩、君に朝が降る", item.originalTitle)
    }

    @Test
    fun `multiple_downloads_of_one_episode_retain_separate_identities_and_are_deduplicated`() {
        val first = createTestDownloadItem(1).copy(id = "first")
        val second = createTestDownloadItem(1).copy(id = "second")
        val items = buildSubjectDownloadItems(listOf(episode(1), episode(2)), listOf(first, second, first))
        assertEquals(3, items.size)
        assertEquals(3, items.map { it.key }.distinct().size)
        assertEquals(2, items.filterIsInstance<SubjectDownloadListItem.Download>().size)
        assertEquals(2, assertIs<SubjectDownloadListItem.Episode>(items.last()).episode.episodeId)
    }

    @Test
    fun `downloads_remain_visible_without_episode_metadata`() {
        val download = createTestDownloadItem(2)
        assertEquals(listOf(SubjectDownloadListItem.Download(download)), buildSubjectDownloadItems(emptyList(), listOf(download)))
    }

    @Test
    fun `new_episodes_and_changed_watch_status_are_reflected_without_changing_download_identity`() {
        val download = createTestDownloadItem(1)
        val initial = buildSubjectDownloadItems(listOf(episode(1)), listOf(download))
        val updated = buildSubjectDownloadItems(listOf(episode(1), episode(2).copy(watchStatus = UnifiedCollectionType.DONE)), listOf(download))
        assertEquals(initial.first(), updated.first())
        assertEquals(UnifiedCollectionType.DONE, assertIs<SubjectDownloadListItem.Episode>(updated.last()).episode.watchStatus)
    }

    private fun episode(id: Int) = EpisodeDownloadItem(id, EpisodeSort(id), "Episode $id", UnifiedCollectionType.DOING, true)
}

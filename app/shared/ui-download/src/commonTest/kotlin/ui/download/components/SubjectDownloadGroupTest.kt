package com.wynime.app.ui.download.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.wynime.app.tools.toProgress
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class SubjectDownloadGroupTest {
    private val finished = createTestDownloadItem(
        1, episodeId = 1,
        initialState = DownloadStatus.COMPLETED,
        progress = 1f.toProgress(),
        totalSize = 100.megaBytes,
        downloadSpeed = FileSize.Unspecified,
    )
    private val downloading = createTestDownloadItem(
        2, episodeId = 2,
        initialState = DownloadStatus.IN_PROGRESS,
        totalSize = 200.megaBytes,
        downloadSpeed = 10.megaBytes,
    )
    private val paused = createTestDownloadItem(
        3, episodeId = 3,
        initialState = DownloadStatus.PAUSED,
        totalSize = 300.megaBytes,
        downloadSpeed = 5.megaBytes,
    )

    private fun group(
        entries: List<DownloadItem>,
        totalEpisodeCount: Int? = null,
    ) = SubjectDownloadGroup(
        subjectId = 1,
        subjectName = "孤独摇滚",
        entries = entries,
        collectionType = UnifiedCollectionType.DOING,
        totalEpisodeCount = totalEpisodeCount,
    )

    @Test
    fun `displayTotalCount uses totalEpisodeCount when known`() {
        assertEquals(12, group(listOf(finished, downloading), totalEpisodeCount = 12).displayTotalCount)
    }

    @Test
    fun `displayTotalCount falls back to entries size`() {
        assertEquals(2, group(listOf(finished, downloading)).displayTotalCount)
    }

    @Test
    fun `displayTotalCount is at least entries size`() {
        assertEquals(3, group(listOf(finished, downloading, paused), totalEpisodeCount = 1).displayTotalCount)
    }

    @Test
    fun `totalSize sums specified sizes`() {
        assertEquals(600.megaBytes, group(listOf(finished, downloading, paused)).totalSize)
    }

    @Test
    fun `totalSize is unspecified when no entry has size`() {
        val entry = createTestDownloadItem(1, totalSize = FileSize.Unspecified)
        assertEquals(FileSize.Unspecified, group(listOf(entry)).totalSize)
    }

    @Test
    fun `downloadSpeed only counts actively downloading entries`() {

        assertEquals(10.megaBytes, group(listOf(finished, downloading, paused)).downloadSpeed)
    }

    @Test
    fun `downloadSpeedText is null when nothing is downloading`() {
        assertNull(group(listOf(finished, paused)).downloadSpeedText)
        assertEquals(FileSize.Unspecified, group(listOf(finished, paused)).downloadSpeed)
    }

    @Test
    fun `activeDownloadCount counts only in progress`() {
        assertEquals(1, group(listOf(finished, downloading, paused)).activeDownloadCount)
    }

    @Test
    fun `hasUnfinished reflects unfinished entries`() {
        assertTrue(group(listOf(finished, downloading)).hasUnfinished)
        assertFalse(group(listOf(finished)).hasUnfinished)
    }
}

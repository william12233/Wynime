package com.wynime.app.ui.download.components

import kotlinx.coroutines.DelicateCoroutinesApi
import com.wynime.app.tools.Progress
import com.wynime.app.tools.toProgress
import com.wynime.app.ui.framework.runComposeStateTest
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.Unspecified
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadItemTest {
    class CalculateSizeTextTest {
        @Test
        fun `all unavailable`() {
            check(null, Unspecified, null)
        }

        @Test
        fun `progress unavailable - total size available`() =
            check("200.0 MB", 200.megaBytes, null)

        @Test
        fun `progress available - total size unavailable`() =
            check(null, Unspecified, 0.5f)

        @Test
        fun `all available`() =
            check("100.0 MB / 200.0 MB", 200.megaBytes, 0.5f)

        private fun check(expected: String?, total: FileSize, progress: Float?) {
            assertEquals(
                expected,
                DownloadItem.calculateSizeText(
                    totalSize = total,
                    progress = progress,
                ),
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("SameParameterValue")
    private fun downloadItem(
        sort: Int = 1,
        subjectName: String = "翻转孤独",
        displayName: String = "翻转孤独",
        subjectId: Int = 1,
        episodeId: Int = 1,
        initialStatus: DownloadStatus = when (sort % 2) {
            0 -> DownloadStatus.PAUSED
            else -> DownloadStatus.IN_PROGRESS
        },
        downloadSpeed: FileSize = 100.megaBytes,
        progress: Progress = 0.9f.toProgress(),
        totalSize: FileSize = 200.megaBytes,
    ): DownloadItem {
        return DownloadItem(
            subjectId = subjectId,
            episodeId = episodeId,
            id = "1",
            sort = EpisodeSort(sort),
            subjectName = subjectName,
            displayName = displayName,
            creationTime = 100,
            stats = DownloadItem.Stats(
                downloadSpeed = downloadSpeed,
                progress = progress,
                totalSize = totalSize,
            ),
            status = initialStatus,
                engineKey = null,
            subjectCollectionType = null,
        )
    }

    @Test
    fun `progress not available`() = runComposeStateTest {
        downloadItem(
            initialStatus = DownloadStatus.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = Progress.Unspecified,
        ).run {
            assertEquals(false, isPaused)
            assertEquals(false, isFinished)
            assertEquals("200.0 MB", sizeText)
            assertEquals(null, progressText)
            assertEquals(Progress.Unspecified, progress)
            assertEquals(true, isProgressUnspecified)
        }
    }

    @Test
    fun `in progress and not finished`() = runComposeStateTest {
        downloadItem(
            initialStatus = DownloadStatus.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 0.1f.toProgress(),
        ).run {
            assertEquals(false, isPaused)
            assertEquals(false, isFinished)
            assertEquals("200.0 MB", sizeText)
            assertEquals("10.0%", progressText)
            assertEquals(0.1f, progress.getOrNull())
            assertEquals(false, isProgressUnspecified)
        }
    }

    @Test
    fun `in progress and finished`() = runComposeStateTest {
        downloadItem(
            initialStatus = DownloadStatus.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 1f.toProgress(),
        ).run {
            assertEquals(false, isPaused)
            assertEquals(false, isFinished)
            assertEquals("200.0 MB", sizeText)
            assertEquals("100.0%", progressText)
            assertEquals(1f, progress.getOrNull())
            assertEquals(false, isProgressUnspecified)
        }
    }

    @Test
    fun `show speed if not finished`() = runComposeStateTest {
        downloadItem(
            initialStatus = DownloadStatus.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 0.1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals("10.0%", progressText)
            assertEquals("100.0 MB/s", speedText)
        }
        downloadItem(
            initialStatus = DownloadStatus.PAUSED,
            downloadSpeed = 100.megaBytes,
            progress = 0.1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals("10.0%", progressText)
            assertEquals("100.0 MB/s", speedText)
        }
    }

    @Test
    fun `always show speed and progress if not finished`() = runComposeStateTest {
        downloadItem(
            initialStatus = DownloadStatus.IN_PROGRESS,
            downloadSpeed = 100.megaBytes,
            progress = 1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals("100.0%", progressText)
            assertEquals("100.0 MB/s", speedText)
        }
        downloadItem(
            initialStatus = DownloadStatus.PAUSED,
            downloadSpeed = 100.megaBytes,
            progress = 1f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals("100.0%", progressText)
            assertEquals("100.0 MB/s", speedText)
        }
        downloadItem(
            initialStatus = DownloadStatus.PAUSED,
            downloadSpeed = 100.megaBytes,
            progress = 2f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals("100.0%", progressText)
            assertEquals("100.0 MB/s", speedText)
        }
    }

    @Test
    fun `do not show speed if finished`() = runComposeStateTest {
        downloadItem(
            initialStatus = DownloadStatus.COMPLETED,
            downloadSpeed = 100.megaBytes,
            progress = 2f.toProgress(),
        ).run {
            assertEquals("200.0 MB", sizeText)
            assertEquals(null, progressText)
            assertEquals(null, speedText)
        }
    }
}

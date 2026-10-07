package com.wynime.app.domain.media.cache.engine

import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.tools.Progress
import com.wynime.app.tools.toProgress
import com.wynime.utils.httpdownloader.DownloadId
import com.wynime.utils.httpdownloader.DownloadProgress
import com.wynime.utils.httpdownloader.DownloadStatus
import com.wynime.utils.httpdownloader.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpMediaCacheEngineProgressTest {
    @Test
    fun `uses segment progress for hls downloads`() {
        val progress = testProgress(
            mediaType = MediaType.M3U8,
            totalSegments = 8,
            downloadedSegments = 3,
            downloadedBytes = 1200,
            totalBytes = -1,
        ).toHttpCacheProgress()

        assertEquals((3f / 8f).toProgress(), progress)
    }

    @Test
    fun `uses byte progress for mp4 downloads`() {
        val progress = testProgress(
            mediaType = MediaType.MP4,
            totalSegments = 1,
            downloadedSegments = 0,
            downloadedBytes = 25,
            totalBytes = 100,
        ).toHttpCacheProgress()

        assertEquals(0.25f.toProgress(), progress)
    }

    @Test
    fun `returns unspecified when total bytes are unknown for file downloads`() {
        val progress = testProgress(
            mediaType = MediaType.MP4,
            totalSegments = 1,
            downloadedSegments = 0,
            downloadedBytes = 25,
            totalBytes = -1,
        ).toHttpCacheProgress()

        assertEquals(Progress.Unspecified, progress)
    }

    @Test
    fun `returns complete when downloader reports completed`() {
        val progress = testProgress(
            mediaType = MediaType.M3U8,
            totalSegments = 8,
            downloadedSegments = 7,
            downloadedBytes = 1200,
            totalBytes = -1,
            status = DownloadStatus.COMPLETED,
        ).toHttpCacheProgress()

        assertEquals(1f.toProgress(), progress)
    }

    @Test
    fun `initializing and merging count as in progress`() {
        assertEquals(MediaCacheState.IN_PROGRESS, DownloadStatus.INITIALIZING.toMediaCacheState())
        assertEquals(MediaCacheState.IN_PROGRESS, DownloadStatus.DOWNLOADING.toMediaCacheState())
        assertEquals(MediaCacheState.IN_PROGRESS, DownloadStatus.MERGING.toMediaCacheState())
        assertEquals(MediaCacheState.PAUSED, DownloadStatus.PAUSED.toMediaCacheState())
        assertEquals(MediaCacheState.FAILED, DownloadStatus.FAILED.toMediaCacheState())
        assertEquals(MediaCacheState.FAILED, DownloadStatus.CANCELED.toMediaCacheState())
        assertEquals(MediaCacheState.COMPLETED, DownloadStatus.COMPLETED.toMediaCacheState())
    }

    private fun testProgress(
        mediaType: MediaType,
        totalSegments: Int,
        downloadedSegments: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        status: DownloadStatus = DownloadStatus.DOWNLOADING,
    ) = DownloadProgress(
        downloadId = DownloadId("test"),
        url = "https://example.com/test",
        mediaType = mediaType,
        totalSegments = totalSegments,
        downloadedSegments = downloadedSegments,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        status = status,
    )
}

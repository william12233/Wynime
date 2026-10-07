package com.wynime.app.domain.media.cache

import com.wynime.utils.httpdownloader.DownloadError
import com.wynime.utils.httpdownloader.DownloadStatus
import com.wynime.utils.httpdownloader.SegmentFailure

sealed interface DownloaderStatus {

    data object Resolving : DownloaderStatus

    data class Http(
        val status: DownloadStatus,
        val error: DownloadError?,
        val downloadedSegments: Int,
        val totalSegments: Int,

        val lastSegmentFailure: SegmentFailure? = null,
    ) : DownloaderStatus
}

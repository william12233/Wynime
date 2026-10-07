package com.wynime.app.ui.download.details

import androidx.compose.runtime.Immutable
import com.wynime.app.domain.media.cache.DownloaderStatus
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.MediaCacheState

@Immutable
data class DownloaderDetails(
    val cacheState: MediaCacheState,
    val stats: MediaCache.SessionStats,
    val status: DownloaderStatus?,
)

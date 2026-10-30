package com.wynime.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class MediaCacheSettings(
    val enabled: Boolean = false,
    val maxCountPerSubject: Int = 1,

    val maxConcurrentDownloads: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS,

    val mostRecentOnly: Boolean = false,
    val mostRecentCount: Int = 8,

    val saveDir: String? = null,

    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    val effectiveMaxConcurrentDownloads: Int
        get() = maxConcurrentDownloads.takeIf {
            it in MIN_MAX_CONCURRENT_DOWNLOADS..MAX_MAX_CONCURRENT_DOWNLOADS
        } ?: DEFAULT_MAX_CONCURRENT_DOWNLOADS

    companion object {
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2
        const val MIN_MAX_CONCURRENT_DOWNLOADS = 1
        const val MAX_MAX_CONCURRENT_DOWNLOADS = 16

        val Default = MediaCacheSettings()
    }
}


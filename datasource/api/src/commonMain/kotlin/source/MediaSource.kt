package com.wynime.datasources.api.source

import kotlinx.serialization.Serializable
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.paging.SizedSource
import kotlin.jvm.JvmInline

interface MediaSource : AutoCloseable {

    val mediaSourceId: String

    val location: MediaSourceLocation
        get() = MediaSourceLocation.Online

    val kind: MediaSourceKind

    val info: MediaSourceInfo

    suspend fun checkConnection(): ConnectionStatus

    suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch>

    suspend fun searchSubjects(keyword: String): List<BrowseSubject> = emptyList()

    suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> = emptyList()

    fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): Media = throw UnsupportedOperationException("MediaSource '$mediaSourceId' does not support browsing")

    override fun close() {}
}

class MediaSourceInfo(
    val displayName: String,
    val description: String? = null,
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
    val iconResourceId: String? = null,

    val isSpecial: Boolean = false,
    val tier: MediaSourceTier? = null,
)

@JvmInline
@Serializable
value class MediaSourceTier(val value: UInt) : Comparable<MediaSourceTier> {
    override fun compareTo(other: MediaSourceTier): Int = this.value.compareTo(other.value)

    companion object {

        val Fallback = MediaSourceTier(2u)

        val MaximumValue = MediaSourceTier(UInt.MAX_VALUE)
    }
}

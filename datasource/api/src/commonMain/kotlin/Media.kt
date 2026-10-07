package com.wynime.datasources.api

import kotlinx.serialization.SerialName

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaMatch
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.source.matches
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.platform.annotations.SerializationOnly

@Serializable
sealed interface Media {

    val mediaId: String

    val mediaSourceId: String

    val originalUrl: String

    val download: ResourceLocation

    val episodeRange: EpisodeRange?

    val originalTitle: String

    val publishedTime: Long

    val properties: MediaProperties

    val extraFiles: MediaExtraFiles

    val location: MediaSourceLocation

    val kind: MediaSourceKind
}

tailrec fun Media.unwrapCached(): DefaultMedia = when (this) {
    is CachedMedia -> origin.unwrapCached()
    is DefaultMedia -> this
}

@SerialName("me.him188.ani.datasources.api.DefaultMedia")
@Serializable
data class DefaultMedia
@SerializationOnly
constructor(
    override val mediaId: String,
    override val mediaSourceId: String,
    override val originalUrl: String,
    override val download: ResourceLocation,
    override val originalTitle: String,
    override val publishedTime: Long,
    override val properties: MediaProperties,
    override val episodeRange: EpisodeRange? = null,
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
    override val location: MediaSourceLocation = MediaSourceLocation.Online,
    override val kind: MediaSourceKind = MediaSourceKind.WEB,
    @Transient private val _primaryConstructorMarker: Unit = Unit,
) : Media {
    @OptIn(SerializationOnly::class)
    constructor(
        mediaId: String,
        mediaSourceId: String,
        originalUrl: String,
        download: ResourceLocation,
        originalTitle: String,
        publishedTime: Long,
        properties: MediaProperties,
        episodeRange: EpisodeRange?,
        extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
        location: MediaSourceLocation,
        kind: MediaSourceKind,
    ) : this(
        mediaId,
        mediaSourceId,
        originalUrl,
        download,
        originalTitle,
        publishedTime,
        properties,
        episodeRange,
        extraFiles = extraFiles,
        location,
        kind,
        _primaryConstructorMarker = Unit,
    )
}

class CachedMedia(

    val origin: Media,
    cacheMediaSourceId: String,
    override val download: ResourceLocation,
    override val location: MediaSourceLocation = MediaSourceLocation.Local,
    override val kind: MediaSourceKind = MediaSourceKind.LocalCache,
    override val properties: MediaProperties = origin.properties,
    val cacheProperties: MediaCacheProperties? = null,

    override val episodeRange: EpisodeRange? = origin.episodeRange,

    val cacheEpisodeId: String? = null,
) : Media by origin {
    override val mediaId: String = buildString {
        append(cacheMediaSourceId).append(':').append(origin.mediaId)
        if (cacheEpisodeId != null) append(':').append(cacheEpisodeId)
    }
    override val mediaSourceId: String = cacheMediaSourceId
}

data class MediaCacheProperties(
    val totalSegments: Int? = null,
    val httpDownloaderStatus: String? = null,
)

@Serializable
data class MediaProperties @SerializationOnly constructor(

    val subjectName: String? = null,

    val episodeName: String? = null,

    val subtitleLanguageIds: List<String>,

    val resolution: String,

    val alliance: String,

    val size: FileSize = 0.bytes,

    val subtitleKind: SubtitleKind? = null,
    @Suppress("unused")
    @Transient private val _primaryConstructorMarker: Unit = Unit,
) {
    @OptIn(SerializationOnly::class)
    constructor(

        subjectName: String?,
        episodeName: String?,
        subtitleLanguageIds: List<String>,
        resolution: String,
        alliance: String,
        size: FileSize,
        subtitleKind: SubtitleKind?,
    ) : this(
        subjectName, episodeName, subtitleLanguageIds, resolution, alliance, size, subtitleKind,
        _primaryConstructorMarker = Unit,
    )

    override fun toString(): String {
        return "MediaProperties(subtitleLanguageIds=$subtitleLanguageIds, resolution='$resolution', alliance='$alliance', size=$size)"
    }
}

@Serializable
enum class SubtitleKind {

    EMBEDDED,

    CLOSED,

    EXTERNAL_PROVIDED,

    EXTERNAL_DISCOVER,

    CLOSED_OR_EXTERNAL_DISCOVER,
}

fun Media.isLocalCache(): Boolean {
    return kind == MediaSourceKind.LocalCache
}

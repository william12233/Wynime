package com.wynime.datasources.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.datasources.api.source.MediaFetchRequest
import kotlin.time.Clock

@Serializable
data class MediaCacheMetadata(

    val subjectId: String,

    val episodeId: String,

    val subjectNameCN: String? = null,

    val subjectNames: List<String>,

    val episodeSort: EpisodeSort,

    val episodeEp: EpisodeSort? = episodeSort,

    val episodeName: String,

    val creationTime: Long = Clock.System.now().toEpochMilliseconds(),

    val autoCached: Boolean = false,

    @Transient @Suppress("unused") private val _primaryConstructorMarker: Byte = 0,
) {
    constructor(
        request: MediaFetchRequest,
        autoCached: Boolean = false
    ) : this(
        subjectId = request.subjectId,
        episodeId = request.episodeId,
        subjectNameCN = request.subjectNameCN,
        subjectNames = request.subjectNames,
        episodeSort = request.episodeSort,
        episodeEp = request.episodeEp,
        episodeName = request.episodeName,
        creationTime = Clock.System.now().toEpochMilliseconds(),
        autoCached = autoCached,
    )
}
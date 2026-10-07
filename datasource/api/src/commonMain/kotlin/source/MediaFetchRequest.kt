package com.wynime.datasources.api.source

import kotlinx.serialization.Serializable
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.PackedDate

@Serializable
data class MediaFetchRequest(

    val subjectId: String,

    val episodeId: String,

    val subjectNameCN: String? = null,

    val subjectNames: List<String>,

    val episodeSort: EpisodeSort,

    val episodeName: String,

    val episodeEp: EpisodeSort? = episodeSort,

    val episodes: List<Episode> = emptyList(),

    val traceId: String = "",
) {

    fun isSameSubjectQuery(other: MediaFetchRequest): Boolean =
        subjectId == other.subjectId &&
                subjectNameCN == other.subjectNameCN &&
                subjectNames == other.subjectNames &&
                episodes == other.episodes

    @Serializable
    data class Episode(

        val episodeId: String,

        val sort: EpisodeSort,

        val ep: EpisodeSort? = sort,

        val name: String = "",

        val airDate: PackedDate = PackedDate.Invalid,
    )

    companion object
}

fun MediaFetchRequest.toStringMultiline() = buildString {
    append("subjectId").append(": ").append(subjectId).appendLine()
    append("episodeId").append(": ").append(episodeId).appendLine()
    append("subjectNameCn").append(": ").append(subjectNameCN).appendLine()
    append("subjectNames:").appendLine()
    subjectNames.forEach { append("- ").appendLine(it) }
    append("episodeSort").append(": ").append(episodeSort).appendLine()
    append("episodeName").append(": ").append(episodeName).appendLine()
    append("episodeEp").append(": ").append(episodeEp).appendLine()
    append("episodes").append(": ").append(episodes.size).appendLine()
}

infix fun MediaFetchRequest.matchesSubject(cache: MediaCacheMetadata): MatchKind? {
    if (subjectId.isNotEmpty() && cache.subjectId.isNotEmpty()) {

        return if (cache.subjectId == subjectId) MatchKind.EXACT else null
    }

    if (subjectNames.any { cache.subjectNames.contains(it) }) return MatchKind.FUZZY
    return null
}

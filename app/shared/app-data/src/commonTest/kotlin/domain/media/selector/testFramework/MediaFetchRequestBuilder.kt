package com.wynime.app.domain.media.selector.testFramework

import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.source.MediaFetchRequest

class MediaFetchRequestBuilder(
    var subjectId: String = "1",
    var episodeId: String = "1",
    var subjectNameCN: String? = null,
    var subjectNames: List<String> = listOf(),
    var episodeSort: EpisodeSort = EpisodeSort(1),
    var episodeName: String = "",
    var episodeEp: EpisodeSort? = null,
) {
    fun build(): MediaFetchRequest = MediaFetchRequest(
        subjectId,
        episodeId,
        subjectNameCN,
        subjectNames,
        episodeSort,
        episodeName,
        episodeEp,
    )

    fun takeFrom(other: MediaFetchRequest) {
        subjectId = other.subjectId
        episodeId = other.episodeId
        subjectNameCN = other.subjectNameCN
        subjectNames = other.subjectNames
        episodeSort = other.episodeSort
        episodeName = other.episodeName
        episodeEp = other.episodeEp
    }
}

inline fun buildMediaFetchRequest(
    action: MediaFetchRequestBuilder.() -> Unit,
): MediaFetchRequest {
    return MediaFetchRequestBuilder().apply(action).build()
}

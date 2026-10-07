package com.wynime.datasources.api.source

import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.contains

data class MediaMatch(
    val media: Media,
    val kind: MatchKind,
)

fun MediaMatch.matches(request: MediaFetchRequest): Boolean? {
    val actualEpRange = this.media.episodeRange ?: return null
    val expectedEp = request.episodeEp
    return !(request.episodeSort !in actualEpRange && (expectedEp == null || expectedEp !in actualEpRange))
}

fun MediaMatch.definitelyMatches(request: MediaFetchRequest): Boolean = matches(request) == true

enum class MatchKind {

    EXACT,

    FUZZY,
}

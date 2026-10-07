package com.wynime.app.domain.mediasource

import com.wynime.app.domain.mediasource.MediaListFilter.Candidate
import com.wynime.datasources.api.EpisodeSort

open class MediaListFilterContext(
    val subjectNames: Set<String>,
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String?,
) {
    val subjectNamesWithoutSpecial: Set<String> by lazy {
        subjectNames.mapTo(HashSet(subjectNames.size)) {
            MediaListFilters.removeSpecials(it, removeWhitespace = true, replaceNumbers = true)
        }
    }

    val episodeNameForCompare: String? by lazy {
        episodeName?.let {
            MediaListFilters.removeSpecials(it, removeWhitespace = true, replaceNumbers = true)
        }
    }

    fun BasicMediaListFilter.applyOn(candidate: Candidate): Boolean =
        this@MediaListFilterContext.applyOn(candidate)

    fun Iterable<BasicMediaListFilter>.applyOn(candidate: Candidate): Boolean =
        this.all { it.applyOn(candidate) }

    fun Sequence<BasicMediaListFilter>.applyOn(candidate: Candidate): Boolean =
        this.all { it.applyOn(candidate) }
}

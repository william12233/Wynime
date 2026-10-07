package com.wynime.app.domain.mediasource

import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.EpisodeRange

fun interface MediaListFilter<in Ctx : MediaListFilterContext> {

    interface Candidate {
        val originalTitle: String
        val subjectName: String get() = originalTitle
        val episodeRange: EpisodeRange?
    }

    fun Ctx.applyOn(media: Candidate): Boolean
}

infix fun <C : MediaListFilterContext> MediaListFilter<C>.or(cond: MediaListFilter<C>): MediaListFilter<C> {
    val self = this
    return MediaListFilter<C> { candidate ->
        with(self) { applyOn(candidate) } || with(cond) { applyOn(candidate) }
    }
}

typealias BasicMediaListFilter = MediaListFilter<MediaListFilterContext>

fun Media.asCandidate(): MediaListFilter.Candidate {
    val media = this
    return object : MediaListFilter.Candidate {
        override val originalTitle: String get() = media.originalTitle
        override val episodeRange: EpisodeRange? get() = media.episodeRange
        override fun toString(): String {
            return "Candidate(media=$media)"
        }
    }
}

package com.wynime.app.domain.media.selector

import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.mediasource.MediaListFilters
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.utils.platform.annotations.Range
import com.wynime.utils.platform.annotations.TestOnly

sealed class MaybeExcludedMedia {

    abstract val result: Media?

    @UnsafeOriginalMediaAccess
    abstract val original: Media

    abstract val exclusionReason: MediaExclusionReason?

    data class Included(
        override val result: Media,
        val metadata: MatchMetadata,
    ) : MaybeExcludedMedia() {
        @UnsafeOriginalMediaAccess
        override val original: Media get() = result
        override val exclusionReason: Nothing? get() = null

        val similarity: @Range(from = 0L, to = 100L) Int get() = metadata.similarity
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    data class Excluded(
        override val original: Media,
        override val exclusionReason: MediaExclusionReason
    ) : MaybeExcludedMedia() {
        override val result: Nothing? get() = null
    }
}

@RequiresOptIn
annotation class UnsafeOriginalMediaAccess

sealed class MediaExclusionReason {

    data class EpisodeMismatch(val episodeRange: EpisodeRange?) : MediaExclusionReason()

    data class SingleEpisodeForCompleteSubject(val episodeRange: EpisodeRange?) : MediaExclusionReason()

    data object MediaWithoutSubtitle : MediaExclusionReason()

    data object UnsupportedByPlatformPlayer : MediaExclusionReason()

    data object FromSequelSeason : MediaExclusionReason()

    data object FromSeriesSeason : MediaExclusionReason()

    data object SubjectNameMismatch : MediaExclusionReason()
}

data class MatchMetadata(
    val subjectMatchKind: SubjectMatchKind,
    val episodeMatchKind: EpisodeMatchKind,

    val similarity: @Range(from = 0L, to = 100L) Int,
) {
    enum class SubjectMatchKind {

        FUZZY,

        EXACT,
    }

    enum class EpisodeMatchKind {
        NONE,

        EP,

        SORT,
    }
}

@TestOnly
val TestMatchMetadata
    get() = MatchMetadata(
        MatchMetadata.SubjectMatchKind.EXACT,
        MatchMetadata.EpisodeMatchKind.EP,
        90,
    )

fun MaybeExcludedMedia.isPerfectMatch() = when (this) {
    is MaybeExcludedMedia.Excluded -> false
    is MaybeExcludedMedia.Included -> {
        metadata.subjectMatchKind == MatchMetadata.SubjectMatchKind.EXACT
                && metadata.episodeMatchKind >= MatchMetadata.EpisodeMatchKind.EP
    }
}

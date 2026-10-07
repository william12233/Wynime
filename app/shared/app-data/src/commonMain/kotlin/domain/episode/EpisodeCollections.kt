package com.wynime.app.domain.episode

import androidx.compose.ui.util.fastAll
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.minus
import kotlin.time.Duration.Companion.days

object EpisodeCollections {
    fun isSubjectCompleted(
        episodes: List<EpisodeInfo>,
        recurrence: SubjectRecurrence?,
        now: PackedDate = PackedDate.now(),
    ): Boolean {
        val allEpisodesFinished = episodes.fastAll { it.isKnownCompleted(recurrence) }
        if (!allEpisodesFinished) return false
        return isSubjectCompleted(episodes.asSequence().map { it.airDate }, now)
    }

    fun isSubjectCompleted(dates: Sequence<PackedDate>, now: PackedDate = PackedDate.now()): Boolean {
        val maxAirDate = dates
            .filter { it.isValid }
            .maxOrNull()

        return maxAirDate != null && now - maxAirDate >= 365.days
    }
}
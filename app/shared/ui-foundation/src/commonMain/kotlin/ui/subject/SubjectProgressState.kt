package com.wynime.app.ui.subject

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.wynime.app.data.models.subject.ContinueWatchingStatus
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.tools.WeekFormatter
import com.wynime.app.ui.foundation.stateOf
import com.wynime.datasources.api.toLocalDateOrNull

@Stable
class SubjectProgressState(
    info: State<SubjectProgressInfo?>,
    private val weekFormatter: WeekFormatter = WeekFormatter.System,
) {

    constructor(
        info: SubjectProgressInfo?,
        weekFormatter: WeekFormatter = WeekFormatter.System,
    ) : this(stateOf(info), weekFormatter)

    private val continueWatchingStatus by derivedStateOf {
        info.value?.continueWatchingStatus
    }

    val isLatestEpisodeWatched by derivedStateOf {
        continueWatchingStatus is ContinueWatchingStatus.Watched
    }

    val isDone by derivedStateOf {
        continueWatchingStatus == ContinueWatchingStatus.Done
    }

    val episodeIdToPlay: Int? by derivedStateOf {
        info.value?.nextEpisodeIdToPlay
    }

    fun buttonText(strings: SubjectStatusStrings): String {
        return when (val s = continueWatchingStatus) {
            is ContinueWatchingStatus.Continue -> strings.continueWatching(renderEpAndSort(s.episodeEp, s.episodeSort))
            ContinueWatchingStatus.Done -> strings.done
            is ContinueWatchingStatus.NotOnAir -> {
                val date = s.airDate.toLocalDateOrNull()
                if (date != null) {
                    val week = weekFormatter.format(date)
                    strings.startsOn(week)
                } else {
                    strings.notOnAir
                }
            }

            ContinueWatchingStatus.Start -> strings.startWatching
            is ContinueWatchingStatus.Watched -> {
                val date = s.nextEpisodeAirDate.toLocalDateOrNull()
                if (date != null) {
                    val week = weekFormatter.format(date)
                    strings.updatesOn(week)
                } else {
                    strings.watched(renderEpAndSort(s.episodeEp, s.episodeSort))
                }
            }

            null -> strings.unknown
        }
    }

    val buttonIsPrimary by derivedStateOf {
        when (continueWatchingStatus) {
            is ContinueWatchingStatus.Start,
            is ContinueWatchingStatus.Continue -> true

            else -> false
        }
    }
}

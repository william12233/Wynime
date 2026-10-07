package com.wynime.app.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.subject.ContinueWatchingStatus
import com.wynime.app.data.models.subject.SubjectAiringInfo
import com.wynime.app.data.models.subject.SubjectAiringKind
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.models.subject.TestSubjectProgressInfos
import com.wynime.app.data.models.subject.isOnAir
import com.wynime.app.ui.foundation.stateOf
import com.wynime.datasources.api.EpisodeSort
import com.wynime.utils.platform.annotations.TestOnly

@Stable
class AiringLabelState(
    airingInfoState: State<SubjectAiringInfo?>,
    progressInfoState: State<SubjectProgressInfo?>,
) {

    constructor(
        airingInfo: SubjectAiringInfo?,
        progressInfo: SubjectProgressInfo?,
    ) : this(stateOf(airingInfo), stateOf(progressInfo))

    private val airingInfo by airingInfoState
    private val progressInfo by progressInfoState

    val isLoading by derivedStateOf {
        airingInfo == null || progressInfo == null
    }

    fun progressText(strings: SubjectStatusStrings): String? {

        val airingInfo = airingInfo
        val progressInfo = progressInfo
        if (airingInfo == null || progressInfo == null) {
            return null
        }
        return when (airingInfo.kind) {
            SubjectAiringKind.UPCOMING -> {
                strings.upcoming

            }

            SubjectAiringKind.ON_AIR -> {
                when (val s = progressInfo.continueWatchingStatus) {
                    ContinueWatchingStatus.Done -> strings.done
                    is ContinueWatchingStatus.Watched -> strings.watched(renderEpAndSort(s.episodeEp, s.episodeSort))

                    is ContinueWatchingStatus.Continue,
                    is ContinueWatchingStatus.NotOnAir,
                    is ContinueWatchingStatus.Start,
                        ->
                        if (airingInfo.latestSort == null) {
                            strings.onAir
                        } else {
                            strings.onAirTo(renderEpAndSort(airingInfo.latestEp, airingInfo.latestSort))
                        }
                }
            }

            SubjectAiringKind.COMPLETED -> {
                when (val s = progressInfo.continueWatchingStatus) {
                    ContinueWatchingStatus.Done -> strings.done

                    is ContinueWatchingStatus.Watched -> strings.watched(renderEpAndSort(s.episodeEp, s.episodeSort))
                    is ContinueWatchingStatus.Continue ->
                        strings.watched(renderEpAndSort(s.watchedEpisodeEp, s.watchedEpisodeSort))

                    is ContinueWatchingStatus.NotOnAir,
                    ContinueWatchingStatus.Start,
                        -> strings.completed
                }
            }
        }
    }

    val highlightProgress by derivedStateOf {
        val continueWatchingStatus = progressInfo?.continueWatchingStatus
        airingInfo?.isOnAir == true
                &&
                when (continueWatchingStatus) {
                    is ContinueWatchingStatus.Continue -> true

                    ContinueWatchingStatus.Start,
                    ContinueWatchingStatus.Done,
                    is ContinueWatchingStatus.NotOnAir,
                    is ContinueWatchingStatus.Watched,
                    null,
                        -> false
                }
    }

    fun totalEpisodesText(strings: SubjectStatusStrings): String? {
        val airingInfo = airingInfo ?: return null
        return renderTotalEpisodeText(airingInfo, strings)
    }
}

@Composable
fun AiringLabel(
    state: AiringLabelState,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    progressColor: Color = if (state.highlightProgress) MaterialTheme.colorScheme.primary else Color.Unspecified,
) {
    val strings = rememberSubjectStatusStrings()
    ProvideTextStyle(style) {
        FlowRow(
            modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            state.progressText(strings)?.let {
                Text(
                    it,
                    color = progressColor,
                    softWrap = false,
                )
            }
            state.totalEpisodesText(strings)?.let {
                Text(" · ", softWrap = false)
                Text(it, softWrap = false)
            }
        }
    }
}

fun renderEpAndSort(ep: EpisodeSort?, sort: EpisodeSort?) = when {
    ep == null -> sort.toString()
    sort == null -> ep.toString()
    ep.number == 0f -> sort.toString()
    ep != sort -> "$ep ($sort)"
    else -> sort.toString()
}

@TestOnly
fun createTestAiringLabelState(
    airingInfo: SubjectAiringInfo = TestSubjectAiringInfo,
    progressInfo: SubjectProgressInfo = TestSubjectProgressInfos.ContinueWatching2,
): AiringLabelState = AiringLabelState(stateOf(airingInfo), stateOf(progressInfo))

@TestOnly
val TestSubjectAiringInfo get() = SubjectAiringInfo.EmptyCompleted

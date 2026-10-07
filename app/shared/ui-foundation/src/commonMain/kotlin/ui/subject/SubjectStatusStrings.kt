package com.wynime.app.ui.subject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_airing_completed
import com.wynime.app.ui.lang.subject_airing_on_air
import com.wynime.app.ui.lang.subject_airing_on_air_to
import com.wynime.app.ui.lang.subject_airing_total_episodes_completed
import com.wynime.app.ui.lang.subject_airing_total_episodes_scheduled
import com.wynime.app.ui.lang.subject_airing_upcoming
import com.wynime.app.ui.lang.subject_progress_continue_watching
import com.wynime.app.ui.lang.subject_progress_done
import com.wynime.app.ui.lang.subject_progress_not_on_air
import com.wynime.app.ui.lang.subject_progress_start_watching
import com.wynime.app.ui.lang.subject_progress_starts_on
import com.wynime.app.ui.lang.subject_progress_unknown
import com.wynime.app.ui.lang.subject_progress_updates_on
import com.wynime.app.ui.lang.subject_progress_watched
import org.jetbrains.compose.resources.stringResource

@Stable
data class SubjectStatusStrings(
    val continueWatchingFormat: String,
    val done: String,
    val notOnAir: String,
    val startsOnFormat: String,
    val startWatching: String,
    val updatesOnFormat: String,
    val watchedFormat: String,
    val unknown: String,
    val upcoming: String,
    val onAir: String,
    val onAirToFormat: String,
    val completed: String,
    val totalEpisodesCompletedFormat: String,
    val totalEpisodesScheduledFormat: String,
) {
    fun continueWatching(episode: String): String = continueWatchingFormat.replacePlaceholder(episode)
    fun startsOn(whenText: String): String = startsOnFormat.replacePlaceholder(whenText)
    fun updatesOn(whenText: String): String = updatesOnFormat.replacePlaceholder(whenText)
    fun watched(episode: String): String = watchedFormat.replacePlaceholder(episode)
    fun onAirTo(episode: String): String = onAirToFormat.replacePlaceholder(episode)
    fun totalEpisodesCompleted(count: Int): String = totalEpisodesCompletedFormat.replacePlaceholder(count.toString())
    fun totalEpisodesScheduled(count: Int): String = totalEpisodesScheduledFormat.replacePlaceholder(count.toString())
}

@Composable
fun rememberSubjectStatusStrings(): SubjectStatusStrings = SubjectStatusStrings(
    continueWatchingFormat = stringResource(Lang.subject_progress_continue_watching),
    done = stringResource(Lang.subject_progress_done),
    notOnAir = stringResource(Lang.subject_progress_not_on_air),
    startsOnFormat = stringResource(Lang.subject_progress_starts_on),
    startWatching = stringResource(Lang.subject_progress_start_watching),
    updatesOnFormat = stringResource(Lang.subject_progress_updates_on),
    watchedFormat = stringResource(Lang.subject_progress_watched),
    unknown = stringResource(Lang.subject_progress_unknown),
    upcoming = stringResource(Lang.subject_airing_upcoming),
    onAir = stringResource(Lang.subject_airing_on_air),
    onAirToFormat = stringResource(Lang.subject_airing_on_air_to),
    completed = stringResource(Lang.subject_airing_completed),
    totalEpisodesCompletedFormat = stringResource(Lang.subject_airing_total_episodes_completed),
    totalEpisodesScheduledFormat = stringResource(Lang.subject_airing_total_episodes_scheduled),
)

fun renderTotalEpisodeText(
    airingInfo: com.wynime.app.data.models.subject.SubjectAiringInfo,
    strings: SubjectStatusStrings,
): String? {
    return if (
        airingInfo.kind == com.wynime.app.data.models.subject.SubjectAiringKind.UPCOMING &&
        airingInfo.mainEpisodeCount == 0
    ) {
        null
    } else {
        when (airingInfo.kind) {
            com.wynime.app.data.models.subject.SubjectAiringKind.COMPLETED ->
                strings.totalEpisodesCompleted(airingInfo.mainEpisodeCount)

            com.wynime.app.data.models.subject.SubjectAiringKind.UPCOMING,
            com.wynime.app.data.models.subject.SubjectAiringKind.ON_AIR,
                ->
                strings.totalEpisodesScheduled(airingInfo.mainEpisodeCount)
        }
    }
}

private fun String.replacePlaceholder(value: String): String {
    return replace("%1\$s", value).replace("%s", value)
}

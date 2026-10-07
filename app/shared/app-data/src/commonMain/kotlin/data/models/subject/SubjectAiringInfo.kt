package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownOnAir
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.ifInvalid

@Immutable
data class SubjectAiringInfo(
    val kind: SubjectAiringKind,

    val mainEpisodeCount: Int,

    val airDate: PackedDate,

    val firstSort: EpisodeSort?,

    val latestEp: EpisodeSort?,
    val latestSort: EpisodeSort?,

    val upcomingSort: EpisodeSort?,
) {
    companion object {
        @Stable
        val EmptyCompleted = SubjectAiringInfo(
            SubjectAiringKind.COMPLETED,
            mainEpisodeCount = 0,
            airDate = PackedDate.Invalid,
            firstSort = null,
            latestEp = null,
            latestSort = null,
            upcomingSort = null,
        )

        fun computeFromEpisodeList(
            list: List<EpisodeInfo>,
            airDate: PackedDate,
            recurrence: SubjectRecurrence?,
        ): SubjectAiringInfo {

            val mainStoryEpisodes = list.filter { it.type == EpisodeType.MainStory }

            val kind = when {
                mainStoryEpisodes.isEmpty() -> SubjectAiringKind.UPCOMING
                mainStoryEpisodes.all { it.isKnownCompleted(recurrence) } -> SubjectAiringKind.COMPLETED
                mainStoryEpisodes.all { it.isKnownOnAir(recurrence) } -> SubjectAiringKind.UPCOMING
                mainStoryEpisodes.any { it.isKnownCompleted(recurrence) } -> SubjectAiringKind.ON_AIR

                airDate.isValid && airDate <= PackedDate.now() -> SubjectAiringKind.COMPLETED

                else -> SubjectAiringKind.UPCOMING
            }

            return SubjectAiringInfo(
                kind = kind,
                mainEpisodeCount = mainStoryEpisodes.size,
                airDate = airDate.ifInvalid { mainStoryEpisodes.firstOrNull()?.airDate ?: PackedDate.Invalid },
                firstSort = mainStoryEpisodes.firstOrNull()?.sort,
                latestEp = mainStoryEpisodes.lastOrNull { it.isKnownCompleted(recurrence) }?.sort,
                latestSort = mainStoryEpisodes.lastOrNull { it.isKnownCompleted(recurrence) }?.sort,
                upcomingSort = if (kind == SubjectAiringKind.COMPLETED) {
                    null
                } else {
                    mainStoryEpisodes.firstOrNull { it.isKnownOnAir(recurrence) }?.sort
                        ?: mainStoryEpisodes.firstOrNull { it.airDate.isInvalid }?.sort
                },
            )
        }

        fun computeFromSubjectInfo(
            info: SubjectInfo,
            mainEpisodeCount: Int,
        ): SubjectAiringInfo {
            val kind = when {
                info.completeDate.isValid -> SubjectAiringKind.COMPLETED
                info.airDate < PackedDate.now() -> SubjectAiringKind.ON_AIR
                else -> SubjectAiringKind.UPCOMING
            }
            return SubjectAiringInfo(
                kind = kind,
                mainEpisodeCount = mainEpisodeCount,
                airDate = info.airDate,
                firstSort = null,
                latestEp = null,
                latestSort = null,
                upcomingSort = null,
            )
        }
    }
}

object TestSubjectAiringInfos {
    val OnAir12Eps = SubjectAiringInfo(
        SubjectAiringKind.ON_AIR,
        mainEpisodeCount = 12,
        airDate = PackedDate(2023, 10, 1),
        firstSort = EpisodeSort(1),
        latestEp = EpisodeSort(2),
        latestSort = EpisodeSort(2),
        upcomingSort = EpisodeSort(3),
    )

    val Upcoming24Eps = SubjectAiringInfo(
        SubjectAiringKind.UPCOMING,
        mainEpisodeCount = 24,
        airDate = PackedDate(2023, 10, 1),
        firstSort = EpisodeSort(1),
        latestEp = null,
        latestSort = null,
        upcomingSort = EpisodeSort(1),
    )

    val Completed12Eps = SubjectAiringInfo(
        SubjectAiringKind.COMPLETED,
        mainEpisodeCount = 12,
        airDate = PackedDate(2023, 10, 1),
        firstSort = EpisodeSort(1),
        latestEp = EpisodeSort(12),
        latestSort = EpisodeSort(12),
        upcomingSort = null,
    )
}

@Stable
val SubjectAiringInfo.isOnAir: Boolean
    get() = kind == SubjectAiringKind.ON_AIR

@Stable
val SubjectAiringInfo.hasStarted: Boolean
    get() = isOnAir || isCompleted

@Stable
val SubjectAiringInfo.isUpcoming: Boolean
    get() = kind == SubjectAiringKind.UPCOMING

@Stable
val SubjectAiringInfo.isCompleted: Boolean
    get() = kind == SubjectAiringKind.COMPLETED

@Immutable
enum class SubjectAiringKind {

    UPCOMING,

    ON_AIR,

    COMPLETED,
}

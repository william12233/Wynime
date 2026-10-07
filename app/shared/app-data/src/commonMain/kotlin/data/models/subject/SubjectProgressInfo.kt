package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo.Companion.compute
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.ifInvalid
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

@Immutable
data class SubjectProgressInfo(
    val continueWatchingStatus: ContinueWatchingStatus,

    val nextEpisodeIdToPlay: Int?,
) {

    class Episode(
        val id: Int,
        val type: UnifiedCollectionType,
        val ep: EpisodeSort?,
        val sort: EpisodeSort,

        val airDate: PackedDate,

        val isKnownCompleted: Boolean,
    )

    companion object {
        @Stable
        val Done = SubjectProgressInfo(
            ContinueWatchingStatus.Done,
            null,
        )

        fun compute(
            subjectInfo: SubjectInfo,
            episodes: List<EpisodeCollectionInfo>,
            currentDate: PackedDate,
            recurrence: SubjectRecurrence?,
        ): SubjectProgressInfo {
            return compute(
                subjectStarted = currentDate > subjectInfo.airDate,
                episodes = episodes.map {
                    Episode(
                        it.episodeId,
                        it.collectionType,
                        it.episodeInfo.ep,
                        it.episodeInfo.sort,
                        it.episodeInfo.airDate,
                        it.episodeInfo.isKnownCompleted(recurrence),
                    )
                },
                subjectAirDate = subjectInfo.airDate,
            )
        }

        fun compute(
            subjectStarted: Boolean,
            episodes: List<Episode>,
            subjectAirDate: PackedDate,
        ): SubjectProgressInfo {

            val sortedNormalEpisodes = episodes
                .filter { it.sort is EpisodeSort.Normal }
                .sortedBy { it.sort }

            val lastWatchedEpIndex = sortedNormalEpisodes.indexOfLast {
                it.type == UnifiedCollectionType.DONE || it.type == UnifiedCollectionType.DROPPED
            }

            val continueWatchingStatus = kotlin.run {
                val latestEp = kotlin.run {
                    sortedNormalEpisodes.lastOrNull { it.isKnownCompleted }
                }

                val actualSubjectStarted = latestEp != null || subjectStarted

                val latestEpIndex: Int? =
                    sortedNormalEpisodes.indexOfFirst { it == latestEp }
                        .takeIf { it != -1 }
                        ?: sortedNormalEpisodes.lastIndex.takeIf { it != -1 }

                when (lastWatchedEpIndex) {

                    -1 -> {
                        if (actualSubjectStarted) {
                            ContinueWatchingStatus.Start
                        } else {
                            ContinueWatchingStatus.NotOnAir(
                                subjectAirDate.ifInvalid {
                                    sortedNormalEpisodes.firstOrNull()?.airDate ?: PackedDate.Invalid
                                },
                            )
                        }
                    }

                    in 0..<sortedNormalEpisodes.size - 1 -> {
                        if (latestEpIndex != null && lastWatchedEpIndex < latestEpIndex && actualSubjectStarted) {

                            ContinueWatchingStatus.Continue(
                                episodeEp = sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.ep,
                                episodeSort = sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.sort,
                                watchedEpisodeEp = sortedNormalEpisodes[lastWatchedEpIndex].ep,
                                watchedEpisodeSort = sortedNormalEpisodes[lastWatchedEpIndex].sort,
                            )
                        } else {

                            ContinueWatchingStatus.Watched(
                                sortedNormalEpisodes.getOrNull(lastWatchedEpIndex)?.ep,
                                sortedNormalEpisodes.getOrNull(lastWatchedEpIndex)?.sort,
                                sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.airDate
                                    ?: PackedDate.Invalid,
                            )
                        }
                    }

                    else -> {
                        ContinueWatchingStatus.Done
                    }
                }
            }

            val episodeToPlay = kotlin.run {
                if (continueWatchingStatus is ContinueWatchingStatus.Watched) {
                    return@run sortedNormalEpisodes[lastWatchedEpIndex]
                } else {
                    if (lastWatchedEpIndex != -1) {
                        sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.let { return@run it }
                        sortedNormalEpisodes.getOrNull(lastWatchedEpIndex)?.let { return@run it }
                    }

                    sortedNormalEpisodes.firstOrNull()?.let {
                        return@run it
                    }
                }

                null
            }

            return SubjectProgressInfo(
                continueWatchingStatus,
                episodeToPlay?.id,
            )
        }
    }
}

@Stable
inline val SubjectProgressInfo.hasNewEpisodeToPlay: Boolean
    get() = continueWatchingStatus is ContinueWatchingStatus.Start || continueWatchingStatus is ContinueWatchingStatus.Continue

sealed class ContinueWatchingStatus {
    data object Start : ContinueWatchingStatus()

    data class NotOnAir(
        val airDate: PackedDate,
    ) : ContinueWatchingStatus()

    data class Continue(
        val episodeEp: EpisodeSort?,
        val episodeSort: EpisodeSort?,
        val watchedEpisodeEp: EpisodeSort?,
        val watchedEpisodeSort: EpisodeSort,
    ) : ContinueWatchingStatus()

    data class Watched(
        val episodeEp: EpisodeSort?,
        val episodeSort: EpisodeSort?,

        val nextEpisodeAirDate: PackedDate,
    ) : ContinueWatchingStatus()

    data object Done : ContinueWatchingStatus()
}

@Stable
@TestOnly
object TestSubjectProgressInfos {
    @Stable
    val NotOnAir = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.NotOnAir(PackedDate.Invalid),
        nextEpisodeIdToPlay = null,
    )

    @Stable
    val ContinueWatching2 = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.Continue(
            episodeEp = EpisodeSort(2),
            episodeSort = EpisodeSort(2),
            watchedEpisodeEp = EpisodeSort(1),
            watchedEpisodeSort = EpisodeSort(1),
        ),
        nextEpisodeIdToPlay = null,
    )

    @Stable
    val Watched2 = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.Watched(EpisodeSort(2), EpisodeSort(2), PackedDate.Invalid),
        nextEpisodeIdToPlay = null,
    )

    @Stable
    val Done = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.Done,
        nextEpisodeIdToPlay = null,
    )
}

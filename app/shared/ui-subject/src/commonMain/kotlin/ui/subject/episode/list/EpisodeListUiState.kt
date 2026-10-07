package com.wynime.app.ui.subject.episode.list

import androidx.compose.runtime.Immutable
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.app.data.models.subject.nameOrNameCn
import com.wynime.app.domain.episode.EpisodeCompletionContext
import com.wynime.app.domain.episode.EpisodeCompletionContext.mapAirDate
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.serialization.BigNum
import kotlin.time.Instant

@Immutable
data class EpisodeListUiState(
    val subjectTitle: String,
    val mainEpisodes: List<EpisodeListItem>,
    val otherEpisodes: List<EpisodeListItem>,
    val isPlaceholder: Boolean = false,

    val subjectOriginalTitle: String = subjectTitle,
) {
    companion object {

        fun from(
            collection: SubjectCollectionInfo,
            currentTime: Instant,
            playProgress: Map<Int, Float> = emptyMap(),
        ): EpisodeListUiState {
            val (mainEpisodes, otherEpisodes) = collection.episodes.map { episode ->
                EpisodeListItem.from(
                    episode,
                    isBroadcast = isEpisodeBroadcast(collection.recurrence, episode.episodeInfo.airDate, currentTime),
                    playProgress = playProgress[episode.episodeId],
                )
            }.partition {
                it.sort is EpisodeSort.Normal
            }

            return EpisodeListUiState(
                subjectTitle = collection.subjectInfo.displayName,
                mainEpisodes = mainEpisodes.sortedBy { it.sort },
                otherEpisodes = otherEpisodes.sortedBy { it.sort },
                subjectOriginalTitle = collection.subjectInfo.nameOrNameCn,
            )
        }

        fun isEpisodeBroadcast(
            recurrence: SubjectRecurrence?,
            airDate: PackedDate,
            currentTime: Instant,
        ): Boolean = recurrence.mapAirDate(airDate)?.let { it <= currentTime } ?: (recurrence == null)

        val Placeholder = EpisodeListUiState(
            subjectTitle = "",
            mainEpisodes = emptyList(),
            otherEpisodes = emptyList(),
            isPlaceholder = true,
        )
    }
}

@TestOnly
val TestEpisodeListUiState
    get() = EpisodeListUiState(
        subjectTitle = "测试标题",
        mainEpisodes = TestEpisodeListItems,
        otherEpisodes = TestEpisodeListItems.take(2)
            .map { it.copy(sort = EpisodeSort(BigNum(it.sort.number!!), EpisodeType.SP)) },
    )

@TestOnly
val TestEpisodeListUiStateVeryLong
    get() = EpisodeListUiState(
        subjectTitle = "测试标题",
        mainEpisodes = buildList {
            repeat(100) {
                add(createTestEpisodeListItem(EpisodeSort(it + 1), imageMedium = testEpisodeStillUrlOrNull(it)))
            }
        },
        otherEpisodes = TestEpisodeListItems.take(2)
            .map { it.copy(sort = EpisodeSort(BigNum(it.sort.number!!), EpisodeType.SP)) },
    )

@TestOnly
val TestEpisodeListItems
    get() = buildList {
        repeat(12) {
            add(createTestEpisodeListItem(EpisodeSort(it + 1), imageMedium = testEpisodeStillUrlOrNull(it)))
        }
    }

@TestOnly
private fun testEpisodeStillUrlOrNull(index: Int): String? = if (index % 3 != 2) TestEpisodeStillUrl else null

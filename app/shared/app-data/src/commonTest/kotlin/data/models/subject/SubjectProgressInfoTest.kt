package com.wynime.app.data.models.subject

import com.wynime.app.data.models.subject.SubjectProgressInfo.Episode
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.PackedDate.Companion.Invalid
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.UnifiedCollectionType.DONE
import com.wynime.datasources.api.topic.UnifiedCollectionType.DROPPED
import com.wynime.datasources.api.topic.UnifiedCollectionType.WISH
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectProgressInfoTest {
    private fun ep(
        type: UnifiedCollectionType,
        sort: Int,
        isKnownCompleted: Boolean,
        airDate: PackedDate = Invalid,
        id: Int = sort,
        episodeType: EpisodeType? = EpisodeType.MainStory,
    ): Episode = Episode(
        id, type, EpisodeSort(sort, episodeType), EpisodeSort(sort, episodeType),
        airDate,
        isKnownCompleted,
    )

    private fun calculate(
        subjectStarted: Boolean,
        episodes: List<Episode>,
        subjectAirDate: PackedDate = Invalid,
    ): SubjectProgressInfo {
        return SubjectProgressInfo.compute(
            subjectStarted,
            episodes,
            subjectAirDate,
        )
    }

    @Test
    fun `subject not started - no ep`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(Invalid), continueWatchingStatus)
            assertEquals(null, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - no ep - with time`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(),
            subjectAirDate = PackedDate(2024, 8, 24),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(PackedDate(2024, 8, 24)), continueWatchingStatus)
            assertEquals(null, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - one ep`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(WISH, 1, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(Invalid), continueWatchingStatus)
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first episode wish`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(WISH, 1, isKnownCompleted = false),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(Invalid), continueWatchingStatus)
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first ep done - second ep not completed`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(
                ContinueWatchingStatus.Watched(EpisodeSort(1), EpisodeSort(1), Invalid),
                continueWatchingStatus,
            )
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first ep done - first ep not completed`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = false),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(
                ContinueWatchingStatus.Watched(EpisodeSort(1), EpisodeSort(1), Invalid),
                continueWatchingStatus,
            )
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first ep done - second ep completed`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(continue2_1(), continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first episode wish`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(WISH, 1, isKnownCompleted = false),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.Start, continueWatchingStatus)
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first ep done - second ep not completed`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(
                ContinueWatchingStatus.Watched(EpisodeSort(1), EpisodeSort(1), Invalid),
                continueWatchingStatus,
            )
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first ep done - second ep completed`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(continue2_1(), continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first ep dropped - second ep completed`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DROPPED, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(continue2_1(), continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    private fun continue2_1() =
        ContinueWatchingStatus.Continue(EpisodeSort(2), EpisodeSort(2), EpisodeSort(1), EpisodeSort(1))

    @Test
    fun `all ep done`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(DONE, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.Done, continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `episodes with 00 at end`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
                ep(WISH, 3, isKnownCompleted = true),
                ep(DONE, 0, isKnownCompleted = true),
            ),
        ).run {

            assertEquals(
                ContinueWatchingStatus.Continue(EpisodeSort(2), EpisodeSort(2), EpisodeSort(1), EpisodeSort(1)),
                continueWatchingStatus,
            )
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `episodes with sp done`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
                ep(WISH, 3, isKnownCompleted = true),
                ep(DONE, 4, isKnownCompleted = true, episodeType = EpisodeType.SP),
            ),
        ).run {

            assertEquals(
                ContinueWatchingStatus.Continue(EpisodeSort(2), EpisodeSort(2), EpisodeSort(1), EpisodeSort(1)),
                continueWatchingStatus,
            )
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }
}


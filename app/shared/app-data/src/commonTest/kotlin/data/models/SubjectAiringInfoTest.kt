package com.wynime.app.data.models

import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectAiringInfo
import com.wynime.app.data.models.subject.SubjectAiringKind
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectAiringInfoTest {
    private var idCounter = 0

    private fun ep(
        sort: Int,
        airDate: PackedDate = PackedDate.Invalid,
        type: EpisodeType = EpisodeType.MainStory,
    ): EpisodeInfo = EpisodeInfo(
        episodeId = ++idCounter,
        type = type,
        sort = EpisodeSort(sort),
        airDate = airDate,
    )

    @Test
    fun `empty episode list is upcoming`() {
        val info = SubjectAiringInfo.computeFromEpisodeList(emptyList(), PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.UPCOMING, info.kind)
        assertEquals(0, info.mainEpisodeCount)
        assertEquals(PackedDate.Invalid, info.airDate)
        assertEquals(null, info.firstSort)
        assertEquals(null, info.latestSort)
        assertEquals(null, info.upcomingSort)
    }

    @Test
    fun `single episode upcoming`() {
        val eps = listOf(
            ep(3, PackedDate(8888, 1, 8 + 7 * 2)),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.UPCOMING, info.kind)
        assertEquals(1, info.mainEpisodeCount)
        assertEquals(PackedDate(8888, 1, 8 + 7 * 2), info.airDate)
        assertEquals(EpisodeSort(3), info.firstSort)
        assertEquals(null, info.latestSort)
        assertEquals(EpisodeSort(3), info.upcomingSort)
    }

    @Test
    fun `all episodes are completed`() {
        val eps = listOf(
            ep(1, PackedDate(2023, 1, 8)),
            ep(2, PackedDate(2023, 1, 8 + 7)),
            ep(3, PackedDate(2023, 1, 8 + 7 * 2)),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.COMPLETED, info.kind)
        assertEquals(3, info.mainEpisodeCount)
        assertEquals(PackedDate(2023, 1, 8), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(EpisodeSort(3), info.latestSort)
        assertEquals(null, info.upcomingSort)
    }

    @Test
    fun `some episodes completed - some upcoming`() {
        val eps = listOf(
            ep(1, PackedDate(2023, 1, 8)),
            ep(2, PackedDate(2023, 1, 8 + 7)),
            ep(3, PackedDate(8888, 1, 8 + 7 * 2)),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.ON_AIR, info.kind)
        assertEquals(3, info.mainEpisodeCount)
        assertEquals(PackedDate(2023, 1, 8), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(EpisodeSort(2), info.latestSort)
        assertEquals(EpisodeSort(3), info.upcomingSort)
    }

    @Test
    fun `one episode but has invalid date - when subject has invalid air date`() {
        val eps = listOf(
            ep(1, PackedDate.Invalid),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.UPCOMING, info.kind)
        assertEquals(1, info.mainEpisodeCount)
        assertEquals(PackedDate.Invalid, info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(null, info.latestSort)
        assertEquals(EpisodeSort(1), info.upcomingSort)
    }

    @Test
    fun `one episode but has invalid date - when subject is known broadcast`() {
        val eps = listOf(
            ep(1, PackedDate.Invalid),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate(2023, 1, 1), null)
        assertEquals(SubjectAiringKind.COMPLETED, info.kind)
        assertEquals(1, info.mainEpisodeCount)
        assertEquals(PackedDate(2023, 1, 1), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(null, info.latestSort)
        assertEquals(null, info.upcomingSort)
    }

    @Test
    fun `one episode but has invalid date - long-past air date is completed`() {

        val eps = listOf(
            ep(1, PackedDate.Invalid),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate(2000, 1, 1), null)
        assertEquals(SubjectAiringKind.COMPLETED, info.kind)
        assertEquals(1, info.mainEpisodeCount)
        assertEquals(PackedDate(2000, 1, 1), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(null, info.latestSort)
        assertEquals(null, info.upcomingSort)
    }

    @Test
    fun `one episode but has invalid date - recently-past air date is completed`() {

        val eps = listOf(
            ep(1, PackedDate.Invalid),
        )
        val recentlyPast = PackedDate(2024, 1, 1)
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, recentlyPast, null)
        assertEquals(SubjectAiringKind.COMPLETED, info.kind)
    }

    @Test
    fun `one episode has invalid date - when subject is known upcoming`() {
        val eps = listOf(
            ep(1, PackedDate.Invalid),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate(8888, 1, 1), null)
        assertEquals(SubjectAiringKind.UPCOMING, info.kind)
        assertEquals(1, info.mainEpisodeCount)
        assertEquals(PackedDate(8888, 1, 1), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(null, info.latestSort)
        assertEquals(EpisodeSort(1), info.upcomingSort)
    }

    @Test
    fun `one episode upcoming and one invalid`() {
        val eps = listOf(
            ep(1, PackedDate(8888, 1, 8)),
            ep(2, PackedDate.Invalid),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.UPCOMING, info.kind)
        assertEquals(2, info.mainEpisodeCount)
        assertEquals(PackedDate(8888, 1, 8), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(null, info.latestSort)
        assertEquals(EpisodeSort(1), info.upcomingSort)
    }

    @Test
    fun `one episode completed and one invalid`() {
        val eps = listOf(
            ep(1, PackedDate(1000, 1, 8)),
            ep(2, PackedDate.Invalid),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.ON_AIR, info.kind)
        assertEquals(2, info.mainEpisodeCount)
        assertEquals(PackedDate(1000, 1, 8), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(EpisodeSort(1), info.latestSort)
        assertEquals(EpisodeSort(2), info.upcomingSort)
    }

    @Test
    fun `on air with special episodes mixed in`() {
        val eps = listOf(
            ep(sort = 1, airDate = PackedDate(2023, 1, 8), type = EpisodeType.MainStory),
            ep(sort = 1, airDate = PackedDate(2023, 1, 10), type = EpisodeType.SP),
            ep(sort = 2, airDate = PackedDate(8888, 1, 15), type = EpisodeType.MainStory),
            ep(sort = 2, airDate = PackedDate(8888, 1, 20), type = EpisodeType.SP),
        )
        val info = SubjectAiringInfo.computeFromEpisodeList(eps, PackedDate.Invalid, null)
        assertEquals(SubjectAiringKind.ON_AIR, info.kind)
        assertEquals(2, info.mainEpisodeCount)
        assertEquals(PackedDate(2023, 1, 8), info.airDate)
        assertEquals(EpisodeSort(1), info.firstSort)
        assertEquals(EpisodeSort(1), info.latestSort)
        assertEquals(EpisodeSort(2), info.upcomingSort)
    }
}

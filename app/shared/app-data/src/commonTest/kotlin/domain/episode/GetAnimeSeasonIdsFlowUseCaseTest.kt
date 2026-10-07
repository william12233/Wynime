package com.wynime.app.domain.episode

import com.wynime.app.data.models.schedule.AnimeSeason
import com.wynime.app.data.models.schedule.AnimeSeasonId
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAnimeSeasonIdsFlowUseCaseTest {
    @Test
    fun `sorted puts latest season first`() {
        val seasons = listOf(
            AnimeSeasonId(2025, AnimeSeason.SPRING),
            AnimeSeasonId(2026, AnimeSeason.WINTER),
            AnimeSeasonId(2025, AnimeSeason.AUTUMN),
        )

        assertEquals(
            listOf(
                AnimeSeasonId(2026, AnimeSeason.WINTER),
                AnimeSeasonId(2025, AnimeSeason.AUTUMN),
                AnimeSeasonId(2025, AnimeSeason.SPRING),
            ),
            GetAnimeSeasonIdsFlowUseCase.sorted(seasons),
        )
    }

    @Test
    fun `sorted keeps empty list empty`() {
        assertEquals(emptyList(), GetAnimeSeasonIdsFlowUseCase.sorted(emptyList()))
    }
}

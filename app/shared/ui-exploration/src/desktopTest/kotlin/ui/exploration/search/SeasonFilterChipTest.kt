package com.wynime.app.ui.exploration.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import com.wynime.app.data.models.schedule.AnimeSeason
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_search_filter_season_all
import com.wynime.app.ui.lang.exploration_search_filter_year_all
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class YearFilterChipTest {
    @Test
    fun `shows all years label when nothing selected`() = runWynimeComposeUiTest {
        val allYearsText = runBlocking { getString(Lang.exploration_search_filter_year_all) }
        setContent {
            ProvideCompositionLocalsForPreview {
                YearFilterChip(
                    years = listOf(2026, 2025),
                    selectedYear = null,
                    onSelect = {},
                )
            }
        }

        onNodeWithText(allYearsText).assertIsDisplayed()
    }

    @Test
    fun `selecting a year from dropdown reports callback`() = runWynimeComposeUiTest {
        val allYearsText = runBlocking { getString(Lang.exploration_search_filter_year_all) }
        var selected: Int? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                YearFilterChip(
                    years = listOf(2026, 2025),
                    selectedYear = selected,
                    onSelect = { selected = it },
                )
            }
        }

        onAllNodesWithText(allYearsText).onFirst().performClick()
        onNodeWithText("2026").performClick()

        runOnIdle {
            assertEquals(2026, selected)
        }
    }
}

class SeasonFilterChipTest {
    @Test
    fun `shows all seasons label when nothing selected`() = runWynimeComposeUiTest {
        val allSeasonsText = runBlocking { getString(Lang.exploration_search_filter_season_all) }
        setContent {
            ProvideCompositionLocalsForPreview {
                SeasonFilterChip(
                    selectedSeason = null,
                    onSelect = {},
                    enabled = true,
                )
            }
        }

        onNodeWithText(allSeasonsText).assertIsDisplayed()
    }

    @Test
    fun `selecting a quarter from dropdown reports callback`() = runWynimeComposeUiTest {
        val allSeasonsText = runBlocking { getString(Lang.exploration_search_filter_season_all) }
        var selected: AnimeSeason? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                SeasonFilterChip(
                    selectedSeason = selected,
                    onSelect = { selected = it },
                    enabled = true,
                )
            }
        }

        onAllNodesWithText(allSeasonsText).onFirst().performClick()
        onNodeWithText("Q3").performClick()

        runOnIdle {

            assertEquals(AnimeSeason.SUMMER, selected)
        }
    }
}

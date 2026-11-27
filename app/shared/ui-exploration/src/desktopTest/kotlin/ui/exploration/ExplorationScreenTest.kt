package com.wynime.app.ui.exploration

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.paging.LoadState
import androidx.paging.PagingData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.data.repository.subject.FollowedSubjectsState
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test

class ExplorationScreenTest {
    @Test
    fun `startup progress waits for every section and stays hidden during retry`() = runWynimeComposeUiTest {
        val today = MutableStateFlow<TodayUpdatesUiState>(TodayUpdatesUiState.InitialLoading)
        val followed = MutableStateFlow<FollowedSubjectsState>(FollowedSubjectsState.Loading)
        val recommendations = MutableStateFlow<LoadState>(LoadState.Loading)
        val state = ExplorationPageState(
            todayUpdatesState = today,
            onRetryTodayUpdates = {},
            followedSubjectsState = followed,
            onRetryFollowedSubjects = {},
            recommendationPager = flowOf(PagingData.empty()),
            horizontalScrollTipFlow = flowOf(false),
            onSetDisableHorizontalScrollTip = {},
        )
        setContent {
            MaterialTheme {
                ExplorationTitle(
                    state,
                    today.collectAsState().value,
                    followed.collectAsState().value,
                    recommendations.collectAsState().value,
                    hasRecommendations = false,
                )
            }
        }
        val progress = onNodeWithTag("exploration_initial_load", useUnmergedTree = true)
        progress.assertIsDisplayed()

        runOnIdle { today.value = TodayUpdatesUiState.Content(emptyList()) }
        progress.assertIsDisplayed()

        runOnIdle { followed.value = FollowedSubjectsState.Error(LoadError.NetworkError) }
        progress.assertIsDisplayed()

        runOnIdle { recommendations.value = LoadState.NotLoading(true) }
        progress.assertDoesNotExist()

        runOnIdle { followed.value = FollowedSubjectsState.Loading }
        progress.assertDoesNotExist()
    }
}

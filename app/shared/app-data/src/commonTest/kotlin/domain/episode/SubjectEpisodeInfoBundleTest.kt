package com.wynime.app.domain.episode

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.domain.foundation.LoadError
import com.wynime.utils.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SubjectEpisodeInfoBundleTest {
    private val subjectId = 1

    private fun EpisodePlayerTestSuite.createState(episodeIdFlow: Flow<Int>): SubjectEpisodeInfoBundleLoader {
        return SubjectEpisodeInfoBundleLoader(subjectId, episodeIdFlow, koin)
    }

    private fun TestScope.createSuite(
        backgroundScopeForState: CoroutineScope = this.backgroundScope,
    ) = EpisodePlayerTestSuite(this, backgroundScopeForState)

    @Test
    fun `infoLoadErrorState initially null`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        assertEquals(null, state.infoLoadErrorState.value)
    }

    @Test
    fun `infoBundleFlow emits null first`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        assertEquals(null, state.infoBundleFlow.first())
    }

    @Test
    fun `infoBundleFlow load success`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        assertNotEquals(null, state.infoBundleFlow.drop(1).first())
        assertEquals(null, state.infoLoadErrorState.value)
    }

    @Test
    fun `infoBundleFlow does not complete when episodeId complete`() = runTest {
        val episodeIdFlow = flowOf(2)
        val suite = createSuite()
        val state = suite.createState(episodeIdFlow)
        state.infoBundleFlow.test {
            assertEquals(null, awaitItem())
            assertNotNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `infoBundleFlow load failure is captured in the background amd exposed via infoLoadErrorState`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope()
        val suite = createSuite(scope)
        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                idsFlow.map {
                    throw RepositoryNetworkException()
                }
            }
        }
        val state = suite.createState(episodeIdFlow)
        val job = state.infoBundleFlow.drop(1).launchIn(scope)
        assertEquals(LoadError.NetworkError, state.infoLoadErrorState.onEach { println(it) }.filterNotNull().first())
        job.cancel()
        scope.cancel()
        assertIs<RepositoryNetworkException>(backgroundException.await(), "should be a network error")
    }

    @Test
    fun `infoBundleFlow does NOT update infoLoadErrorState on CancellationException`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope()
        val suite = createSuite(scope)

        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                idsFlow.map {
                    throw CancellationException("Simulated cancellation")
                }
            }
        }

        val state = suite.createState(episodeIdFlow)
        val job = state.infoBundleFlow.drop(1).launchIn(scope)

        val firstError = state.infoLoadErrorState.first()

        assertEquals(null, firstError, "infoLoadErrorState should remain null on CancellationException")

        job.cancel()
        scope.cancel()

    }

    @Test
    fun `infoBundleFlow collector cancellation does NOT set infoLoadErrorState`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = createSuite()

        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->

                idsFlow.map {

                    createTestSubjectEpisodeInfoBundle(
                        subjectId = it.subjectId,
                        episodeId = it.episodeId,
                    )
                }
            }
        }

        val state = suite.createState(episodeIdFlow)

        val collectorJob = state.infoBundleFlow.drop(1).onEach {

            this.cancel()
        }.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(
            null, state.infoLoadErrorState.value,
            "Collector cancellation should not produce a LoadError",
        )

        collectorJob.cancel()
    }

}
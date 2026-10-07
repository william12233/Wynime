@file:OptIn(UnsafeEpisodeSessionApi::class)

package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.episode.mediaFetchSessionFlow
import com.wynime.app.domain.episode.mediaSelectorFlow
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.TestUniversalMediaResolver
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.mediasource.SetPreferredWebMediaSourceUseCase
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.coroutines.childScope
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveWebMediaSourcePreferenceExtensionTest : AbstractPlayerExtensionTest() {
    private val preferredWebMediaSource = MutableStateFlow<String?>(null)
    private val setPreferenceCalls = mutableListOf<Pair<Int, String>>()

    data class Context(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
    )

    private fun TestScope.createCase(
        config: (scope: CoroutineScope, suite: EpisodePlayerTestSuite) -> Unit = { _, _ -> },
    ): Context {
        contract {
            callsInPlace(config, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)

        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }
        suite.registerComponent<GetPreferredWebMediaSourceUseCase> {
            GetPreferredWebMediaSourceUseCase { preferredWebMediaSource }
        }
        suite.registerComponent<SetPreferredWebMediaSourceUseCase> {
            SetPreferredWebMediaSourceUseCase { subjectId, mediaSourceId ->
                if (mediaSourceId != null) {
                    setPreferenceCalls.add(subjectId to mediaSourceId)
                } else {
                    setPreferenceCalls.removeAll { it.first == subjectId }
                }
                preferredWebMediaSource.value = mediaSourceId
            }
        }

        preferredWebMediaSource.value = null
        setPreferenceCalls.clear()

        config(testScope, suite)

        val state = suite.createState(
            listOf(
                ObserveWebMediaSourcePreferenceExtension,
            ),
        )
        state.onUIReady()
        return Context(testScope, suite, state)
    }

    private fun startMediaFetcher(
        state: EpisodeFetchSelectPlayState,
        testScope: CoroutineScope
    ) {

        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
    }

    @Test
    fun `selecting web media updates preference`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
        }

        startMediaFetcher(state, testScope)

        val media = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media))
        advanceUntilIdle()

        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        assertEquals(1, setPreferenceCalls.size)
        assertEquals(subjectId to "web1", setPreferenceCalls.first())

        testScope.cancel()
    }

    @Test
    fun `selecting same web source does not update preference again`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
        }

        preferredWebMediaSource.value = "web1"

        startMediaFetcher(state, testScope)

        val media = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media))
        advanceUntilIdle()

        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        assertEquals(0, setPreferenceCalls.size)

        testScope.cancel()
    }

    @Test
    fun `selecting different web source updates preference`() = runTest {
        val web2: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase { _, suite ->
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
        }

        preferredWebMediaSource.value = "web1"

        startMediaFetcher(state, testScope)

        val media = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web2.complete(listOf(media))
        advanceUntilIdle()

        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        assertEquals(1, setPreferenceCalls.size)
        assertEquals(subjectId to "web2", setPreferenceCalls.first())

        testScope.cancel()
    }

    @Test
    fun `remove preference if preferred source fails`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
        }
        val (testScope, suite, state) = context

        preferredWebMediaSource.value = "web1"
        setPreferenceCalls.add(subjectId to "web1")

        startMediaFetcher(state, testScope)
        web1.completeExceptionally(IllegalStateException("constant failure"))
        advanceUntilIdle()

        assertEquals(0, setPreferenceCalls.size)

        val media = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web2.complete(listOf(media))
        advanceUntilIdle()

        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()

        assertEquals(1, setPreferenceCalls.size)
        assertEquals(subjectId to "web2", setPreferenceCalls.first())

        testScope.cancel()
    }
}

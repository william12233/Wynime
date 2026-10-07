@file:OptIn(UnsafeEpisodeSessionApi::class)

package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.episode.mediaFetchSessionFlow
import com.wynime.app.domain.episode.mediaSelectorFlow
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.TestUniversalMediaResolver
import com.wynime.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import com.wynime.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.coroutines.childScope
import org.openani.mediamp.source.UriMediaData
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AutoSelectExtensionTest : AbstractPlayerExtensionTest() {
    private val defaultSettings = MediaSelectorSettings.AllVisible.copy(
        preferKind = null,
        autoEnableLastSelected = false,
        fastSelectWebKind = false,
    )

    private val mediaSelectorSettings = MutableStateFlow(
        defaultSettings,
    )
    val preferredWebMediaSource = MutableStateFlow<String?>(null)

    data class Context(
        val scope: CoroutineScope,
        val suite: EpisodePlayerTestSuite,
        val state: EpisodeFetchSelectPlayState,
    )

    private fun TestScope.createCase(
        config: (scope: CoroutineScope, suite: EpisodePlayerTestSuite) -> Unit = { _, _ -> },
    ): Context {
        contract {
            callsInPlace(config, InvocationKind.EXACTLY_ONCE)
        }

        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<GetMediaSelectorSettingsFlowUseCase> {
            GetMediaSelectorSettingsFlowUseCase { mediaSelectorSettings }
        }
        suite.registerComponent<MediaSelectorAutoSelectUseCase> {
            MediaSelectorAutoSelectUseCaseImpl(koin)
        }
        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }
        suite.registerComponent<GetMediaSelectorSourceTiersUseCase> {
            GetMediaSelectorSourceTiersUseCase {
                flowOf(MediaSelectorSourceTiers.Empty)
            }
        }
        suite.registerComponent<GetPreferredWebMediaSourceUseCase> {
            GetPreferredWebMediaSourceUseCase { preferredWebMediaSource }
        }

        preferredWebMediaSource.value = null
        config(testScope, suite)

        val state = suite.createState(
            listOf(
                AutoSelectExtension,
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Context(testScope, suite, state)
    }

    @Test
    fun `auto select default`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }
        val (testScope, suite, state) = context

        initializeTest(suite)
        startMediaFetcher(state, testScope)

        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1")
        web1.complete(listOf(myMedia))
        advanceUntilIdle()

        state.assertSelected(myMedia, suite)

        testScope.cancel()
    }

    @Test
    fun `auto select cached - control group`() = runTest {
        val cached: CompletableDeferred<List<Media>>
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            cached = suite.mediaSelectorTestBuilder.delayedMediaSource("cached")
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }
        val (testScope, suite, state) = context

        initializeTest(suite, preference = MediaPreference.Any.copy(alliance = "alliance2"))
        startMediaFetcher(state, testScope)

        val cachedMedia = suite.mediaSelectorTestBuilder.createMedia(
            "cached",
            kind = MediaSourceKind.WEB,
            alliance = "alliance1",
        )
        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1", alliance = "alliance2")
        cached.complete(listOf(cachedMedia))
        web1.complete(listOf(myMedia))
        advanceUntilIdle()

        state.assertSelected(myMedia, suite)

        testScope.cancel()
    }

    @Test
    fun `auto select cached - test group`() = runTest {
        val cached: CompletableDeferred<List<Media>>
        val web1: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            cached = suite.mediaSelectorTestBuilder.delayedMediaSource("cached")
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }
        val (testScope, suite, state) = context

        initializeTest(suite, preference = MediaPreference.Any.copy(alliance = "alliance2"))
        startMediaFetcher(state, testScope)

        val cachedMedia = suite.mediaSelectorTestBuilder.createMedia(
            "cached",
            kind = MediaSourceKind.LocalCache,
            alliance = "alliance1",
        )
        val myMedia = suite.mediaSelectorTestBuilder.createMedia("web1", alliance = "alliance2")
        cached.complete(listOf(cachedMedia))
        web1.complete(listOf(myMedia))
        advanceUntilIdle()

        state.assertSelected(cachedMedia, suite)

        testScope.cancel()
    }

    @Test
    fun `select preferred web source - control group`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
        }
        val (testScope, suite, state) = context

        initializeTest(suite)
        startMediaFetcher(state, testScope)

        val media1 = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        val media2 = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media1))
        web2.complete(listOf(media2))
        advanceUntilIdle()

        state.assertSelected(media1, suite)

        testScope.cancel()
    }

    @Test
    fun `select preferred web source - test group`() = runTest {
        val web1: CompletableDeferred<List<Media>>
        val web2: CompletableDeferred<List<Media>>
        val context = createCase { _, suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1", kind = MediaSourceKind.WEB)
            web2 = suite.mediaSelectorTestBuilder.delayedMediaSource("web2", kind = MediaSourceKind.WEB)
            preferredWebMediaSource.value = "web2"
        }
        val (testScope, suite, state) = context

        initializeTest(suite)
        startMediaFetcher(state, testScope)

        val media1 = suite.mediaSelectorTestBuilder.createMedia("web1", kind = MediaSourceKind.WEB)
        val media2 = suite.mediaSelectorTestBuilder.createMedia("web2", kind = MediaSourceKind.WEB)
        web1.complete(listOf(media1))
        web2.complete(listOf(media2))
        advanceUntilIdle()

        state.assertSelected(media2, suite)

        testScope.cancel()
    }

    private suspend fun EpisodeFetchSelectPlayState.assertSelected(
        expected: DefaultMedia?,
        suite: EpisodePlayerTestSuite
    ) {
        val mediaSelector = mediaSelectorFlow.first()!!
        assertEquals(expected, mediaSelector.selected.first())
        if (expected == null) {
            assertEquals(null, suite.player.mediaData.first())
        } else {
            assertIs<UriMediaData>(suite.player.mediaData.filterNotNull().first())
            assertEquals(0, suite.player.currentPositionMillis.value)
        }
    }

    private fun startMediaFetcher(
        state: EpisodeFetchSelectPlayState,
        testScope: CoroutineScope
    ) {

        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
    }

    private suspend fun TestScope.initializeTest(
        suite: EpisodePlayerTestSuite,
        mediaSelectorSettings: MediaSelectorSettings =
            defaultSettings.copy(preferKind = null),
        preference: MediaPreference = MediaPreference.Any,
    ) {
        this@AutoSelectExtensionTest.mediaSelectorSettings.value = mediaSelectorSettings
        suite.mediaSelectorTestBuilder.savedUserPreference.value = preference

        advanceUntilIdle()
        assertEquals(null, suite.player.mediaData.first())
    }
}

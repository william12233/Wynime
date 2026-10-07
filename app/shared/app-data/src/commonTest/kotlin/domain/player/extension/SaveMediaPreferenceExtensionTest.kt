@file:OptIn(UnsafeEpisodeSessionApi::class)

package com.wynime.app.domain.player.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.episode.mediaFetchSessionFlow
import com.wynime.app.domain.episode.mediaSelectorFlow
import com.wynime.app.domain.media.createTestDefaultMedia
import com.wynime.app.domain.media.createTestMediaProperties
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.TestUniversalMediaResolver
import com.wynime.app.domain.media.selector.MediaAutoSelector
import com.wynime.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import com.wynime.app.domain.media.selector.eventHandling
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.SubtitleLanguage
import com.wynime.utils.coroutines.childScope

class SaveMediaPreferenceExtensionTest : AbstractPlayerExtensionTest() {
    private fun TestScope.createCase(
        saved: MutableList<Pair<Int, MediaPreference>>,
        setupSources: (suite: EpisodePlayerTestSuite) -> Unit = {},
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }
        suite.registerComponent<MediaSelectorEventSavePreferenceUseCase> {
            MediaSelectorEventSavePreferenceUseCase { mediaSelector, subjectId ->
                mediaSelector.eventHandling.run {
                    savePreferenceOnSelect { saved.add(subjectId to it) }
                }
            }
        }
        setupSources(suite)

        val state = suite.createState(
            listOf(
                SaveMediaPreferenceExtension,
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    private fun createSingleLanguageMedia(mediaSourceId: String): DefaultMedia = createTestDefaultMedia(
        mediaId = "$mediaSourceId.1",
        mediaSourceId = mediaSourceId,
        originalTitle = "[XX字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.HttpStreamingFile("https://example.com/1.m3u8"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(SubtitleLanguage.ChineseSimplified.id),
            resolution = "1080P",
            alliance = "XX字幕组",
            size = 122.megaBytes,
            subtitleKind = SubtitleKind.CLOSED,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
    )

    @Test
    fun `SAVE-04 fetch 完成后手动 select 经 debounce 1s 恰保存一次且载荷来自所选 media`() = runTest {
        val saved = mutableListOf<Pair<Int, MediaPreference>>()
        lateinit var web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase(saved) { suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }

        val myMedia = createSingleLanguageMedia("web1")
        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
        web1.complete(listOf(myMedia))
        advanceUntilIdle()

        val selector = state.mediaSelectorFlow.filterNotNull().first()
        assertTrue(selector.select(myMedia))
        runCurrent()

        advanceTimeBy(999)
        runCurrent()
        assertEquals(emptyList(), saved)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(
                subjectId to suite.mediaSelectorTestBuilder.savedUserPreference.value.copy(
                    alliance = "XX字幕组",
                    resolution = "1080P",
                    subtitleLanguageId = SubtitleLanguage.ChineseSimplified.id,
                    mediaSourceId = "web1",
                ),
            ),
            saved,
        )

        advanceUntilIdle()
        assertEquals(1, saved.size)

        testScope.cancel()
    }

    @Test
    fun `SAVE-04 SAVE-01 trySelectDefault 自动选择不触发保存`() = runTest {
        val saved = mutableListOf<Pair<Int, MediaPreference>>()
        lateinit var web1: CompletableDeferred<List<Media>>
        val (testScope, suite, state) = createCase(saved) { suite ->
            web1 = suite.mediaSelectorTestBuilder.delayedMediaSource("web1")
        }

        val myMedia = createSingleLanguageMedia("web1")
        state.mediaFetchSessionFlow.filterNotNull().flatMapLatest { it.cumulativeResults }.launchIn(testScope)
        web1.complete(listOf(myMedia))
        advanceUntilIdle()

        val session = state.mediaFetchSessionFlow.filterNotNull().first()
        val selector = state.mediaSelectorFlow.filterNotNull().first()
        val selected = MediaAutoSelector(selector).select(session)
        assertEquals(myMedia, selected)

        advanceTimeBy(1001)
        runCurrent()
        advanceUntilIdle()
        assertEquals(emptyList(), saved)

        testScope.cancel()
    }
}

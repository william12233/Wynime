@file:OptIn(UnsafeEpisodeSessionApi::class)

package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.episode.mediaSelectorFlow
import com.wynime.app.domain.media.DroppedFileMedia
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.player.data.WynimeSystemFileMediaData
import com.wynime.app.domain.media.resolver.LocalFileMediaResolver
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.TestUniversalMediaResolver
import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.io.delete
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.name
import com.wynime.utils.io.writeBytes
import org.openani.mediamp.source.UriMediaData
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class LoadMediaOnSelectExtensionTest : AbstractPlayerExtensionTest() {
    private fun TestScope.createCase(
        mediaResolver: MediaResolver = TestUniversalMediaResolver,
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true))
            }
        }
        suite.registerComponent<MediaResolver> {
            mediaResolver
        }

        val state = suite.createState(listOf())
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    @Test
    fun `can load media on select`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        val myMedia = TestMediaList[0]
        ms1.complete(listOf(myMedia))
        state.mediaSelectorFlow.filterNotNull().first().select(myMedia)
        advanceUntilIdle()

        assertIs<UriMediaData>(suite.player.mediaData.first())
        assertEquals(0, suite.player.currentPositionMillis.value)

        testScope.cancel()
    }

    @Test
    fun `plays a dropped file while media sources are still fetching`() = runTest {
        val (testScope, suite, state) =
            createCase(LocalFileMediaResolver())

        suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        val file = Path(SystemTemporaryDirectory, "ani-dropped-${Random.nextLong()}.mkv").inSystem
        file.writeBytes(byteArrayOf(0))
        try {
            state.mediaSelectorFlow.filterNotNull().first().selectTemporarily(DroppedFileMedia.create(file))
            advanceUntilIdle()

            assertIs<VideoLoadingState.Succeed>(state.playerSession.videoLoadingState.value)
            val data = assertIs<WynimeSystemFileMediaData>(suite.player.mediaData.first())
            assertEquals(file.name, data.filename)
        } finally {
            testScope.cancel()
            file.delete()
        }
    }

    @Test
    fun `can load media and reset player on select`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        suite.player.loadMedia(durationMs = 100_000L, playWhenReady = true, uri = "file://old.mp4")
        suite.player.injectPosition(1000)
        advanceUntilIdle()

        val myMedia = TestMediaList[0]
        ms1.complete(listOf(myMedia))
        state.mediaSelectorFlow.filterNotNull().first().select(myMedia)
        advanceUntilIdle()

        assertIs<UriMediaData>(suite.player.mediaData.first())
        assertEquals(0, suite.player.currentPositionMillis.value)

        testScope.cancel()
    }

    @Test
    fun `switch media resets player`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        suite.player.loadMedia(durationMs = 100_000L, playWhenReady = true, uri = "file://old.mp4")
        suite.player.injectPosition(1000)
        advanceUntilIdle()

        ms1.complete(TestMediaList.take(2))

        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[0])
        advanceUntilIdle()
        val previousData = suite.player.mediaData.first()
        assertIs<UriMediaData>(previousData)
        assertEquals(0, suite.player.currentPositionMillis.value)

        suite.player.seekTo(2000)

        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[1])
        advanceUntilIdle()
        assertNotSame(previousData, suite.player.mediaData.first())
        assertEquals(0, suite.player.currentPositionMillis.value)

        testScope.cancel()
    }

    @Test
    fun `noop when unselect`() = runTest {
        val (testScope, suite, state) =
            createCase()

        val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

        suite.player.loadMedia(durationMs = 100_000L, playWhenReady = true, uri = "file://old.mp4")
        suite.player.injectPosition(1000)
        advanceUntilIdle()

        ms1.complete(TestMediaList.take(2))

        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[0])
        advanceUntilIdle()
        val previousData = suite.player.mediaData.first()
        assertIs<UriMediaData>(previousData)
        assertEquals(0, suite.player.currentPositionMillis.value)

        suite.player.seekTo(2000)

        state.mediaSelectorFlow.filterNotNull().first().unselect()
        advanceUntilIdle()
        assertSame(previousData, suite.player.mediaData.first())
        assertEquals(2000, suite.player.currentPositionMillis.value)

        testScope.cancel()
    }
}
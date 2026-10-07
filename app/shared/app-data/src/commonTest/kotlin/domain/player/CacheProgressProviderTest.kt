package com.wynime.app.domain.player

import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.MediaCacheProgressInfo
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMediampApi::class)
class CacheProgressProviderTest {
    private fun TestScope.createPlayer(): TestMediampPlayer =
        TestMediampPlayer(StandardTestDispatcher(testScheduler)).apply {
            defaultMediaProperties = MediaProperties(title = "Test", durationMillis = 100_000L)
        }

    private fun TestScope.createProvider(player: TestMediampPlayer): CacheProgressProvider =
        CacheProgressProvider(player, backgroundScope)

    @Test
    fun `no media emits nothing`() = runTest {
        val player = createPlayer()
        createProvider(player).cacheProgressInfoFlow.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `uri media without buffered position emits empty`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        createProvider(player).cacheProgressInfoFlow.test {
            advanceUntilIdle()
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `uri media follows buffered position reported by player`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        createProvider(player).cacheProgressInfoFlow.test {
            advanceUntilIdle()
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())

            player.injectBufferedPosition(25_000L)
            advanceUntilIdle()
            val info = awaitItem()
            assertEquals(listOf(ChunkState.DONE, ChunkState.NONE), info.chunkStates)
            assertEquals(0.25f, info.chunkWeights[0])
            assertEquals(0.75f, info.chunkWeights[1])

            player.injectBufferedPosition(100_000L)
            advanceUntilIdle()
            assertEquals(1f, awaitItem().chunkWeights[0])
        }
    }

    @Test
    fun `buffered position resets to empty when media is reopened`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/first.m3u8"))
        advanceUntilIdle()

        createProvider(player).cacheProgressInfoFlow.test {
            advanceUntilIdle()
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
            player.injectBufferedPosition(50_000L)
            advanceUntilIdle()
            assertEquals(0.5f, awaitItem().chunkWeights[0])

            player.setMediaData(UriMediaData("https://example.com/second.m3u8"))
            advanceUntilIdle()

            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stopping playback emits empty`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        createProvider(player).cacheProgressInfoFlow.test {
            advanceUntilIdle()
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
            player.injectBufferedPosition(50_000L)
            advanceUntilIdle()
            assertEquals(0.5f, awaitItem().chunkWeights[0])

            player.stopPlayback()
            advanceUntilIdle()
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

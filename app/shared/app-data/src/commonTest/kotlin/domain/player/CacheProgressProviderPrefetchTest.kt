package com.wynime.app.domain.player

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.MediaCacheProgressInfo
import com.wynime.app.domain.media.player.prefetch.MediaTimeRange
import com.wynime.app.domain.media.player.prefetch.PrefetchSegmentInfo
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheProgressProviderPrefetchTest {
    private fun weights(info: MediaCacheProgressInfo): List<Float> = List(info.size) { (info.chunkWeights[it] * 1000).toInt() / 1000f }

    @Test
    fun `uri media merges buffered position with prefetched segments`() = runTest {
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler)).apply {
            defaultMediaProperties = MediaProperties(title = "Test", durationMillis = 100_000L)
        }
        val prefetch = MutableStateFlow<List<PrefetchSegmentInfo>>(emptyList())
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()
        CacheProgressProvider(player, backgroundScope, prefetchProgress = prefetch).cacheProgressInfoFlow.test {
            advanceUntilIdle()
            assertEquals(MediaCacheProgressInfo.Empty, awaitItem())

            player.injectBufferedPosition(20_000L)
            advanceUntilIdle()
            assertEquals(listOf(ChunkState.DONE, ChunkState.NONE), awaitItem().chunkStates)

            prefetch.value = listOf(
                PrefetchSegmentInfo(MediaTimeRange(50_000, 60_000), ChunkState.DOWNLOADING),
                PrefetchSegmentInfo(MediaTimeRange(60_000, 70_000), ChunkState.DONE),
            )
            advanceUntilIdle()
            val merged = awaitItem()
            assertEquals(
                listOf(ChunkState.DONE, ChunkState.NONE, ChunkState.DOWNLOADING, ChunkState.DONE, ChunkState.NONE),
                merged.chunkStates,
            )
            assertEquals(listOf(0.2f, 0.3f, 0.1f, 0.1f, 0.3f), weights(merged))

            prefetch.value = emptyList()
            advanceUntilIdle()
            assertEquals(listOf(ChunkState.DONE, ChunkState.NONE), awaitItem().chunkStates)
        }
    }
}

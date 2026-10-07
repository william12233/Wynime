package com.wynime.app.domain.media.player

import com.wynime.app.domain.media.player.prefetch.MediaTimeRange
import com.wynime.app.domain.media.player.prefetch.PrefetchSegmentInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class RangeCacheProgressTest {
    private fun weights(info: MediaCacheProgressInfo): List<Float> = List(info.size) { info.chunkWeights[it] }

    @Test
    fun `empty ranges produce single NONE chunk`() {
        val info = buildRangeCacheProgressInfo(100_000, emptyList())
        assertEquals(listOf(ChunkState.NONE), info.chunkStates)
        assertEquals(listOf(1f), weights(info))
    }

    @Test
    fun `unknown duration produces Empty`() {
        assertEquals(MediaCacheProgressInfo.Empty, buildRangeCacheProgressInfo(0, emptyList()))
    }

    @Test
    fun `disjoint ranges are separated by NONE gaps`() {
        val info = buildRangeCacheProgressInfo(
            100_000,
            listOf(
                PrefetchSegmentInfo(MediaTimeRange(0, 25_000), ChunkState.DONE),
                PrefetchSegmentInfo(MediaTimeRange(50_000, 60_000), ChunkState.DOWNLOADING),
                PrefetchSegmentInfo(MediaTimeRange(60_000, 70_000), ChunkState.DONE),
            ),
        )
        assertEquals(
            listOf(ChunkState.DONE, ChunkState.NONE, ChunkState.DOWNLOADING, ChunkState.DONE, ChunkState.NONE),
            info.chunkStates,
        )
        assertEquals(listOf(0.25f, 0.25f, 0.1f, 0.1f, 0.3f), weights(info))
    }

    @Test
    fun `overlapping ranges take the more complete state and merge neighbours`() {
        val info = buildRangeCacheProgressInfo(
            100_000,
            listOf(
                PrefetchSegmentInfo(MediaTimeRange(0, 30_000), ChunkState.DONE),
                PrefetchSegmentInfo(MediaTimeRange(20_000, 40_000), ChunkState.DOWNLOADING),
                PrefetchSegmentInfo(MediaTimeRange(30_000, 35_000), ChunkState.DONE),
            ),
        )
        assertEquals(listOf(ChunkState.DONE, ChunkState.DOWNLOADING, ChunkState.NONE), info.chunkStates)
        assertEquals(listOf(0.35f, 0.05f, 0.6f), weights(info).map { (it * 100).toInt() / 100f })
    }

    @Test
    fun `ranges outside duration are clamped`() {
        val info = buildRangeCacheProgressInfo(
            100_000,
            listOf(PrefetchSegmentInfo(MediaTimeRange(90_000, 130_000), ChunkState.DONE)),
        )
        assertEquals(listOf(ChunkState.NONE, ChunkState.DONE), info.chunkStates)
        assertEquals(listOf(0.9f, 0.1f), weights(info).map { (it * 100).toInt() / 100f })
    }
}

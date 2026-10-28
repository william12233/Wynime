package com.wynime.app.videoplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MpvPlaybackDetectorTest {
    @Test
    fun `ordinary playback cadence is stable`() {
        val detector = MpvPlaybackDetector()

        assertNull(detector.observe(sample(wall = 0, position = 0)))
        assertNull(detector.observe(sample(wall = 100, position = 100)))
        assertNull(detector.observe(sample(wall = 200, position = 200)))
    }

    @Test
    fun `timeline jump is reported with the last stable position`() {
        val detector = MpvPlaybackDetector()

        detector.observe(sample(wall = 0, position = 0))
        detector.observe(sample(wall = 100, position = 100))
        val discontinuity = detector.observe(sample(wall = 200, position = 2_100))

        assertEquals(MpvDiscontinuityReason.TIMELINE_JUMP, discontinuity?.reason)
        assertEquals(100L, discontinuity?.targetPositionMillis)
        assertEquals(2_000L, discontinuity?.observedDeltaMillis)
    }

    @Test
    fun `buffering does not become a timeline jump`() {
        val detector = MpvPlaybackDetector()

        detector.observe(sample(wall = 0, position = 0))
        assertNull(detector.observe(sample(wall = 100, position = 0, buffering = true)))
        assertNull(detector.observe(sample(wall = 200, position = 2_000)))
    }

    @Test
    fun `av sync drift requires consecutive samples`() {
        val detector = MpvPlaybackDetector()

        detector.observe(sample(wall = 0, position = 0, avSync = 300))
        assertNull(detector.observe(sample(wall = 100, position = 100, avSync = 300)))
        assertEquals(
            MpvDiscontinuityReason.AV_SYNC_DRIFT,
            detector.observe(sample(wall = 200, position = 200, avSync = 300))?.reason,
        )
    }

    private fun sample(
        wall: Long,
        position: Long,
        avSync: Long? = null,
        buffering: Boolean = false,
    ) = MpvPlaybackSample(
        wallTimeMillis = wall,
        timePosMillis = position,
        avSyncMillis = avSync,
        isBuffering = buffering,
    )
}

package com.wynime.app.videoplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvBufferOptionsTest {
    @Test
    fun `forward buffer is expressed in seconds`() {
        assertEquals("120", mpvStartupOptions()["cache-secs"])
    }

    @Test
    fun `byte caps are symmetric and match the policy`() {
        val options = mpvStartupOptions()
        assertEquals((128L * 1024 * 1024).toString(), options["demuxer-max-bytes"])
        assertEquals(options["demuxer-max-bytes"], options["demuxer-max-back-bytes"])
    }

    @Test
    fun `startup options use audio clock and preserve precise seek frames`() {
        val options = mpvStartupOptions()

        assertEquals("audio", options["video-sync"])
        assertEquals("vo", options["framedrop"])
        assertEquals("no", options["hr-seek-framedrop"])
        assertEquals("2", options["hr-seek-demuxer-offset"])
    }
}

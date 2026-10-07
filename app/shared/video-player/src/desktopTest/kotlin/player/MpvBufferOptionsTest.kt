package com.wynime.app.videoplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvBufferOptionsTest {
    @Test
    fun `forward buffer is expressed in seconds`() {
        assertEquals("60", mpvBufferOptions()["cache-secs"])
    }

    @Test
    fun `byte caps are symmetric and match the policy`() {
        val options = mpvBufferOptions()
        assertEquals((96L * 1024 * 1024).toString(), options["demuxer-max-bytes"])
        assertEquals(options["demuxer-max-bytes"], options["demuxer-max-back-bytes"])
    }
}

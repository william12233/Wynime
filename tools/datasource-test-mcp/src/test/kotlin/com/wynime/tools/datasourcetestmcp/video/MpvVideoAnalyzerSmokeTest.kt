package com.wynime.tools.datasourcetestmcp.video

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MpvVideoAnalyzerSmokeTest {
    private fun testVideo(): File? =
        File(System.getProperty("wynime.seekverify.video") ?: "/tmp/seek-verify.mp4")
            .takeIf { it.isFile }

    @Test
    fun `probe local file with window`() {
        if (!System.getProperty("os.name").contains("Mac")) return
        val video = testVideo() ?: return
        val framesDir = File(System.getProperty("java.io.tmpdir"), "mpv-probe-frames-window")

        val output = runBlocking {
            MpvVideoAnalyzer().analyze(
                ProbeVideoInput(
                    videoUrl = video.absolutePath,
                    playSeconds = 5,
                    showWindow = true,
                    captureFramesDir = framesDir.absolutePath,
                    captureAtSeconds = listOf(2),
                ),
            )
        }

        println("analysis = ${output.analysis}")
        println("frames = ${output.frames.map { it.path }}")

        assertTrue(output.analysis.available, "player must be available")
        val playback = assertNotNull(output.analysis.playback)
        assertTrue(playback.ok, "playback must reach target: $playback ${output.analysis.errors}")
        assertTrue(
            (playback.playedPositionMillis ?: 0) >= 5_000,
            "position must advance to >=5s, was ${playback.playedPositionMillis}",
        )
        val video0 = assertNotNull(output.analysis.video, "video stream info")
        assertEquals(1920, video0.width)
        assertTrue(output.frames.isNotEmpty(), "should capture frames")
    }

    @Test
    fun `probe local file headless - drain keeps playback advancing`() {
        if (!System.getProperty("os.name").contains("Mac")) return
        val video = testVideo() ?: return

        val output = runBlocking {
            MpvVideoAnalyzer().analyze(
                ProbeVideoInput(
                    videoUrl = video.absolutePath,
                    playSeconds = 5,
                    showWindow = false,
                ),
            )
        }

        println("analysis = ${output.analysis}")
        val playback = assertNotNull(output.analysis.playback)
        assertTrue(playback.ok, "headless playback must reach target: $playback ${output.analysis.errors}")
        assertTrue(
            (playback.playedPositionMillis ?: 0) >= 5_000,
            "position must advance to >=5s without any surface, was ${playback.playedPositionMillis}",
        )
    }
}

package com.wynime.app.data.models.preference

import com.wynime.app.data.persistent.DataStoreJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class VideoScaffoldConfigTest {
    @Test
    fun `missing video enhancement default uses performance`() {
        val config = DataStoreJson.decodeFromString(VideoScaffoldConfig.serializer(), "{}")

        assertEquals(VideoEnhancementDefaultMode.OFF, config.videoEnhancementDefaultMode)
    }

    @Test
    fun `video enhancement default survives serialization`() {
        val config = VideoScaffoldConfig.Default.copy(
            videoEnhancementDefaultMode = VideoEnhancementDefaultMode.QUALITY,
        )

        val decoded = DataStoreJson.decodeFromString(
            VideoScaffoldConfig.serializer(),
            DataStoreJson.encodeToString(VideoScaffoldConfig.serializer(), config),
        )

        assertEquals(VideoEnhancementDefaultMode.QUALITY, decoded.videoEnhancementDefaultMode)
    }

    @Test
    fun `missing OP ED skip duration uses 85 seconds`() {
        val config = DataStoreJson.decodeFromString(VideoScaffoldConfig.serializer(), "{}")

        assertEquals(85.seconds, config.opEdSkipDuration)
    }

    @Test
    fun `OP ED skip duration survives serialization`() {
        for (duration in listOf(80.seconds, 85.seconds, 90.seconds)) {
            val encoded = DataStoreJson.encodeToString(
                VideoScaffoldConfig.serializer(),
                VideoScaffoldConfig.Default.copy(opEdSkipDuration = duration),
            )

            val decoded = DataStoreJson.decodeFromString(VideoScaffoldConfig.serializer(), encoded)

            assertEquals(duration, decoded.opEdSkipDuration)
        }
    }

    @Test
    fun `missing playback speed fields use compatible defaults`() {
        val config = DataStoreJson.decodeFromString(VideoScaffoldConfig.serializer(), "{}")

        assertEquals(1f, config.playbackSpeed)
        assertEquals(0.5f, config.minPlaybackSpeed)
        assertEquals(2.5f, config.maxPlaybackSpeed)

        assertEquals(true, config.rememberPlaybackSpeed)
    }

    @Test
    fun `playback speed fields survive serialization`() {
        val config = VideoScaffoldConfig.Default.copy(
            playbackSpeed = 1.75f,
            minPlaybackSpeed = 1f,
            maxPlaybackSpeed = 2f,
        )

        val decoded = DataStoreJson.decodeFromString(
            VideoScaffoldConfig.serializer(),
            DataStoreJson.encodeToString(VideoScaffoldConfig.serializer(), config),
        )

        assertEquals(config, decoded)
    }

    @Test
    fun `changing playback speed range clamps dependent speeds`() {
        val config = VideoScaffoldConfig.Default.copy(playbackSpeed = 2f, fastForwardSpeed = 3f)

        val updated = config.withPlaybackSpeedRange(0.5f..1.5f)

        assertEquals(0.5f, updated.minPlaybackSpeed)
        assertEquals(1.5f, updated.maxPlaybackSpeed)
        assertEquals(1.5f, updated.playbackSpeed)
        assertEquals(1.5f, updated.fastForwardSpeed)
    }

    @Test
    fun `playback speed range must span at least one step`() {
        assertFailsWith<IllegalArgumentException> {
            VideoScaffoldConfig.Default.withPlaybackSpeedRange(1f..1f)
        }

        assertEquals(1f..1.25f, VideoScaffoldConfig.normalizePlaybackSpeedRange(1f..1f))
        assertEquals(3.75f..4f, VideoScaffoldConfig.normalizePlaybackSpeedRange(4f..4f))
    }

    @Test
    fun `colliding slider thumbs keep the stationary endpoint fixed`() {
        val previous = 1f..1.25f

        assertEquals(
            1f..1.25f,
            VideoScaffoldConfig.normalizePlaybackSpeedRange(1.25f..1.25f, previous),
        )
        assertEquals(
            1f..1.25f,
            VideoScaffoldConfig.normalizePlaybackSpeedRange(1f..1f, previous),
        )
    }
}

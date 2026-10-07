package com.wynime.app.data.models.preference

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoResolverSettingsTest {
    @Test
    fun `default timeout is 8 seconds`() {
        assertEquals(8, VideoResolverSettings.Default.effectiveResourceExtractionTimeoutSeconds)
        assertEquals(8_000L, VideoResolverSettings.Default.effectiveResourceExtractionTimeoutMillis)
    }

    @Test
    fun `invalid timeout falls back to default`() {
        val settings = VideoResolverSettings(resourceExtractionTimeoutSeconds = 9)

        assertEquals(8, settings.effectiveResourceExtractionTimeoutSeconds)
        assertEquals(8_000L, settings.effectiveResourceExtractionTimeoutMillis)
    }
}

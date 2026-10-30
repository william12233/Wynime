package com.wynime.app.data.models.preference

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaCacheSettingsTest {
    @Test
    fun `default concurrent download limit is two`() {
        assertEquals(2, MediaCacheSettings.Default.effectiveMaxConcurrentDownloads)
    }

    @Test
    fun `valid concurrent download limits are preserved`() {
        assertEquals(1, MediaCacheSettings(maxConcurrentDownloads = 1).effectiveMaxConcurrentDownloads)
        assertEquals(16, MediaCacheSettings(maxConcurrentDownloads = 16).effectiveMaxConcurrentDownloads)
    }

    @Test
    fun `invalid concurrent download limits fall back to default`() {
        assertEquals(2, MediaCacheSettings(maxConcurrentDownloads = 0).effectiveMaxConcurrentDownloads)
        assertEquals(2, MediaCacheSettings(maxConcurrentDownloads = 17).effectiveMaxConcurrentDownloads)
    }
}

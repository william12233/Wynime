package com.wynime.app.domain.sourceplugin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcePluginRepositoryModelsTest {
    @Test
    fun `semantic versions compare prereleases before stable releases`() {
        assertTrue(compareSourcePluginVersions("1.0.0-alpha", "1.0.0-beta") < 0)
        assertTrue(compareSourcePluginVersions("1.0.0-rc.1", "1.0.0") < 0)
        assertTrue(compareSourcePluginVersions("1.0.0", "1.0.0+build.1") == 0)
        assertTrue(compareSourcePluginVersions("1.0.10", "1.0.9") > 0)
    }

    @Test
    fun `invalid semantic versions are rejected`() {
        assertFalse(isValidSourcePluginVersion("1.0"))
        assertFalse(isValidSourcePluginVersion("1.0.0-01"))
        assertTrue(isValidSourcePluginVersion("2.4.0-preview.2"))
    }

}

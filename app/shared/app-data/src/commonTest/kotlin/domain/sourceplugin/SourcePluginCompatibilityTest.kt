package com.wynime.app.domain.sourceplugin

import com.wynime.source.plugin.api.SourcePluginPlatform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcePluginCompatibilityTest {
    @Test
    fun `new host accepts an older plugin when the API is unchanged`() {
        assertTrue(
            SourcePluginCompatibilityResolver.resolve(
                manifest = manifest(version = "1.0.26", minHostVersion = "0.1.3"),
                platform = SourcePluginPlatform.ANDROID,
                hostVersion = "1.0.12",
            ).compatible,
        )
    }

    @Test
    fun `compatible plugin can require a newer host without using plugin version ordering`() {
        assertTrue(
            SourcePluginCompatibilityResolver.resolve(
                manifest = manifest(version = "9.4.0", minHostVersion = "1.0.12"),
                platform = SourcePluginPlatform.DESKTOP,
                hostVersion = "1.0.12",
            ).compatible,
        )
        assertFalse(
            SourcePluginCompatibilityResolver.resolve(
                manifest = manifest(version = "1.0.0", minHostVersion = "1.0.12"),
                platform = SourcePluginPlatform.DESKTOP,
                hostVersion = "1.0.11",
            ).compatible,
        )
    }

    @Test
    fun `different API version is rejected explicitly`() {
        val result = SourcePluginCompatibilityResolver.resolve(
            manifest = manifest(pluginApiVersion = SOURCE_PLUGIN_API_VERSION + 1),
            platform = SourcePluginPlatform.ANDROID,
            hostVersion = "1.0.12",
        )

        assertFalse(result.compatible)
        assertTrue(result.reason.orEmpty().contains("API"))
    }

    @Test
    fun `unsupported platform is rejected explicitly`() {
        val result = SourcePluginCompatibilityResolver.resolve(
            manifest = manifest(platforms = setOf(SourcePluginPlatform.DESKTOP)),
            platform = SourcePluginPlatform.ANDROID,
            hostVersion = "1.0.12",
        )

        assertFalse(result.compatible)
        assertTrue(result.reason.orEmpty().contains("platform"))
    }

    private fun manifest(
        version: String = "1.0.0",
        pluginApiVersion: Int = SOURCE_PLUGIN_API_VERSION,
        minHostVersion: String = "0.1.3",
        platforms: Set<SourcePluginPlatform> = setOf(
            SourcePluginPlatform.ANDROID,
            SourcePluginPlatform.DESKTOP,
        ),
    ) = SourcePluginManifest(
        id = "demo",
        displayName = "Demo",
        version = version,
        pluginApiVersion = pluginApiVersion,
        minHostVersion = minHostVersion,
        entryClass = "demo.EntryPoint",
        website = "https://demo.example",
        platforms = platforms,
        artifacts = emptyMap(),
    )
}

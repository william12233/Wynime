package com.wynime.app.domain.sourceplugin

import com.wynime.source.plugin.api.SourcePluginPlatform

data class SourcePluginCompatibilityResult(
    val compatible: Boolean,
    val reason: String? = null,
)

object SourcePluginCompatibilityResolver {
    fun resolve(
        manifest: SourcePluginManifest,
        platform: SourcePluginPlatform,
        hostVersion: String,
    ): SourcePluginCompatibilityResult {
        if (platform !in manifest.platforms) {
            return SourcePluginCompatibilityResult(false, "Plugin does not support platform $platform")
        }
        if (manifest.pluginApiVersion != SOURCE_PLUGIN_API_VERSION) {
            return SourcePluginCompatibilityResult(
                false,
                "Plugin API ${manifest.pluginApiVersion} is incompatible with host API $SOURCE_PLUGIN_API_VERSION",
            )
        }
        if (compareSourcePluginVersions(hostVersion, manifest.minHostVersion) < 0) {
            return SourcePluginCompatibilityResult(
                false,
                "Plugin requires host version ${manifest.minHostVersion} or newer",
            )
        }
        return SourcePluginCompatibilityResult(true)
    }

    fun requireCompatible(
        manifest: SourcePluginManifest,
        platform: SourcePluginPlatform,
        hostVersion: String,
    ) {
        val result = resolve(manifest, platform, hostVersion)
        require(result.compatible) { result.reason ?: "Plugin is incompatible with this host" }
    }
}

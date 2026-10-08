package com.wynime.app.domain.sourceplugin

import kotlinx.serialization.json.Json
import com.wynime.app.data.Res
import com.wynime.source.plugin.api.SourcePluginPlatform

interface BundledSourcePluginPackages {
    val pluginIds: Set<String>
    suspend fun manifest(pluginId: String): SourcePluginManifest
    suspend fun artifact(pluginId: String, artifact: SourcePluginArtifact): ByteArray
}

class ResourceSourcePluginPackages(
    private val platform: SourcePluginPlatform,
) : BundledSourcePluginPackages {
    override val pluginIds = setOf("eacg", "dm1", "next", "girigiri", "2rk", "dida", "dmbus", "dyttzy")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun manifest(pluginId: String): SourcePluginManifest {
        require(pluginId in pluginIds)
        return json.decodeFromString(Res.readBytes("files/source-plugins/manifests/$pluginId.json").decodeToString())
    }

    override suspend fun artifact(pluginId: String, artifact: SourcePluginArtifact): ByteArray {
        val manifest = manifest(pluginId)
        check(manifest.artifacts[platform] == artifact)
        val suffix = if (platform == SourcePluginPlatform.ANDROID) "-android" else ""
        return Res.readBytes("files/source-plugins/artifacts/source-$pluginId$suffix.jar")
    }
}

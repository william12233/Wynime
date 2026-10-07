package com.wynime.app.domain.sourceplugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.wynime.source.plugin.api.SourcePluginMetadata
import com.wynime.source.plugin.api.SourcePluginPlatform

const val SOURCE_PLUGIN_REPOSITORY_SCHEMA_VERSION = 1
const val SOURCE_PLUGIN_API_VERSION = 3

object SourcePluginRepositoryDefaults {
    const val owner = "william12233"
    const val repository = "Wynime"
    const val branch = "main"
    const val indexPath = "index.json"
    const val rawBaseUrl = "https://raw.githubusercontent.com/william12233/Wynime/main/source/plugins"
}

@Serializable
data class SourcePluginRepositoryIndex(
    val schemaVersion: Int,
    val pluginApiVersion: Int,
    val plugins: List<SourcePluginIndexEntry> = emptyList(),
)

@Serializable
data class SourcePluginRepositoryCache(
    val etag: String? = null,
    val index: SourcePluginRepositoryIndex? = null,
)

@Serializable
data class SourcePluginIndexEntry(
    val id: String,
    @SerialName("name") val displayName: String,
    val version: String,
    val description: String = "",
    val website: String,
    val icon: String? = null,
    val platforms: Set<SourcePluginPlatform>,
    val manifest: String,
)

@Serializable
data class SourcePluginManifest(
    val id: String,
    @SerialName("name") val displayName: String,
    val version: String,
    val pluginApiVersion: Int,
    val minHostVersion: String,
    val entryClass: String,
    val website: String,
    val description: String = "",
    val icon: String? = null,
    val platforms: Set<SourcePluginPlatform>,
    val artifacts: Map<SourcePluginPlatform, SourcePluginArtifact>,
) {
    fun toMetadata(): SourcePluginMetadata = SourcePluginMetadata(
        id = id,
        displayName = displayName,
        version = version,
        website = website,
        description = description,
        iconUrl = icon,
        pluginApiVersion = pluginApiVersion,
        minHostVersion = minHostVersion,
        supportedPlatforms = platforms,
    )
}

@Serializable
data class SourcePluginArtifact(
    val url: String,
    val sha256: String,
    val format: SourcePluginArtifactFormat = SourcePluginArtifactFormat.ZIP,
)

@Serializable
enum class SourcePluginArtifactFormat {
    @SerialName("zip")
    ZIP,

    @SerialName("jar")
    JAR,
}

@Serializable
data class InstalledSourcePlugin(
    val id: String,
    val version: String,
    val manifest: SourcePluginManifest,

    val artifactPath: String,
    val enabled: Boolean = true,
)

@Serializable
data class InstalledSourcePlugins(
    val plugins: List<InstalledSourcePlugin> = emptyList(),
) {
    companion object {
        val Empty = InstalledSourcePlugins()
    }
}

class SourcePluginRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedSourcePluginException(message: String) : Exception(message)

fun compareSourcePluginVersions(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_')
    val rightParts = right.split('.', '-', '_')
    val size = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until size) {
        val leftPart = leftParts.getOrNull(index).orEmpty()
        val rightPart = rightParts.getOrNull(index).orEmpty()
        val numberComparison = (leftPart.toIntOrNull() ?: Int.MIN_VALUE)
            .compareTo(rightPart.toIntOrNull() ?: Int.MIN_VALUE)
        if (numberComparison != 0) return numberComparison
        val textComparison = leftPart.compareTo(rightPart)
        if (textComparison != 0) return textComparison
    }
    return 0
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.him188.ani.source.plugin.api.SourcePluginMetadata
import me.him188.ani.source.plugin.api.SourcePluginPlatform

const val SOURCE_PLUGIN_REPOSITORY_SCHEMA_VERSION = 1
const val SOURCE_PLUGIN_API_VERSION = 2

/** The single first-party repository configured by the host application. */
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

/** Last successfully validated first-party index used while the repository is unavailable. */
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
    /** Absolute path to the extracted JVM/DEX artifact. */
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

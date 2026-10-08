package com.wynime.app.domain.sourceplugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.wynime.source.plugin.api.SourcePluginMetadata
import com.wynime.source.plugin.api.SourcePluginPlatform

const val SOURCE_PLUGIN_REPOSITORY_SCHEMA_VERSION = 2
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
    val history: List<SourcePluginVersionEntry> = emptyList(),
)

@Serializable
data class SourcePluginVersionEntry(
    val version: String,
    val manifest: String,
)

fun SourcePluginIndexEntry.versionEntries(): List<SourcePluginVersionEntry> =
    (listOf(SourcePluginVersionEntry(version, manifest)) + history)
        .distinctBy { it.version }

fun SourcePluginIndexEntry.forVersion(version: String): SourcePluginIndexEntry? =
    versionEntries().firstOrNull { it.version == version }?.let { selected ->
        copy(
            version = selected.version,
            manifest = selected.manifest,
            history = emptyList(),
        )
    }

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
    val leftVersion = parseSourcePluginVersion(left)
    val rightVersion = parseSourcePluginVersion(right)
    val coreComparison = compareValuesBy(
        leftVersion,
        rightVersion,
        { it.major },
        { it.minor },
        { it.patch },
    )
    if (coreComparison != 0) return coreComparison

    val leftPreRelease = leftVersion.preRelease
    val rightPreRelease = rightVersion.preRelease
    if (leftPreRelease == null && rightPreRelease == null) return 0
    if (leftPreRelease == null) return 1
    if (rightPreRelease == null) return -1

    for (index in 0 until maxOf(leftPreRelease.size, rightPreRelease.size)) {
        val leftPart = leftPreRelease.getOrNull(index) ?: return -1
        val rightPart = rightPreRelease.getOrNull(index) ?: return 1
        if (leftPart == rightPart) continue
        val leftNumber = leftPart.toIntOrNull()
        val rightNumber = rightPart.toIntOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> leftPart.compareTo(rightPart)
        }
    }
    return 0
}

fun isValidSourcePluginVersion(value: String): Boolean = runCatching {
    parseSourcePluginVersion(value)
}.isSuccess

private data class ParsedSourcePluginVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>?,
)

private fun parseSourcePluginVersion(value: String): ParsedSourcePluginVersion {
    val match = Regex(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
    ).matchEntire(value) ?: throw IllegalArgumentException("Invalid semantic version: $value")
    val preRelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.')?.also { parts ->
        require(parts.none { it.length > 1 && it.startsWith('0') }) {
            "Numeric prerelease identifiers must not contain leading zeroes: $value"
        }
    }
    return ParsedSourcePluginVersion(
        major = match.groupValues[1].toInt(),
        minor = match.groupValues[2].toInt(),
        patch = match.groupValues[3].toInt(),
        preRelease = preRelease,
    )
}

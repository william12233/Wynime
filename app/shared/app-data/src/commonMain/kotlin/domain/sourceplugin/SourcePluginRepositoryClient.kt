/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import io.ktor.http.Url
import me.him188.ani.source.plugin.api.SourceHttpClient
import me.him188.ani.source.plugin.api.SourceHttpRequest

class SourcePluginRepositoryClient(
    private val http: SourceHttpClient,
    private val baseUrl: String = SourcePluginRepositoryDefaults.rawBaseUrl,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    },
) {
    suspend fun fetchIndex(): SourcePluginRepositoryIndex = fetchIndex(SourcePluginRepositoryCache()).index

    suspend fun fetchIndex(cache: SourcePluginRepositoryCache): SourcePluginIndexFetchResult {
        val response = get(
            SourcePluginRepositoryDefaults.indexPath,
            headers = cache.etag?.let { mapOf("If-None-Match" to it) }.orEmpty(),
            acceptedStatus = setOf(304) + (200..299).toSet(),
        )
        if (response.statusCode == 304) {
            return SourcePluginIndexFetchResult(
                index = cache.index ?: throw SourcePluginRepositoryException(
                    "Plugin repository returned 304 without a cached index",
                ),
                etag = cache.etag,
                fromCache = true,
            )
        }
        val index = parse<SourcePluginRepositoryIndex>(response.bodyAsText())
        if (index.schemaVersion > SOURCE_PLUGIN_REPOSITORY_SCHEMA_VERSION) {
            throw UnsupportedSourcePluginException(
                "Plugin repository schema ${index.schemaVersion} requires a newer host",
            )
        }
        if (index.pluginApiVersion > SOURCE_PLUGIN_API_VERSION) {
            throw UnsupportedSourcePluginException(
                "Plugin repository plugin API ${index.pluginApiVersion} requires a newer host",
            )
        }
        val ids = HashSet<String>()
        for (entry in index.plugins) {
            require(entry.id.isSafePluginId()) { "Invalid plugin id in repository index: ${entry.id}" }
            require(ids.add(entry.id)) { "Duplicate plugin id in repository index: ${entry.id}" }
            require(entry.version.isNotBlank()) { "Plugin ${entry.id} has no version" }
            require(entry.platforms.isNotEmpty()) { "Plugin ${entry.id} has no supported platform" }
            require(entry.website.isHttpsUrl()) { "Plugin ${entry.id} website must use HTTPS" }
            entry.icon?.let { require(it.isHttpsUrl()) { "Plugin ${entry.id} icon must use HTTPS" } }
            require(entry.manifest.isSafeRepositoryPath()) {
                "Manifest path must be relative and must not escape the repository"
            }
        }
        return SourcePluginIndexFetchResult(
            index = index,
            etag = response.headers.headerValue("ETag") ?: cache.etag,
            fromCache = false,
        )
    }

    suspend fun fetchManifest(entry: SourcePluginIndexEntry): SourcePluginManifest {
        require(entry.id.isNotBlank()) { "Plugin id must not be blank" }
        require(entry.manifest.isSafeRepositoryPath()) {
            "Manifest path must be relative and must not escape the repository"
        }
        val manifest = parse<SourcePluginManifest>(get(entry.manifest).bodyAsText())
        validateManifest(entry, manifest)
        return manifest
    }

    suspend fun downloadArtifact(artifact: SourcePluginArtifact): ByteArray {
        artifact.sha256.requireSha256()
        val response = get(artifact.url)
        return response.body
    }

    private suspend fun get(
        pathOrUrl: String,
        headers: Map<String, String> = emptyMap(),
        acceptedStatus: Set<Int> = (200..299).toSet(),
    ) = http.execute(
        SourceHttpRequest(
            method = "GET",
            url = resolveUrl(pathOrUrl),
            headers = mapOf("Accept" to "application/json, application/octet-stream") + headers,
        ),
    ).also { response ->
        if (response.statusCode !in acceptedStatus) {
            throw SourcePluginRepositoryException(
                "Plugin repository request failed with HTTP ${response.statusCode}",
            )
        }
    }

    private inline fun <reified T> parse(text: String): T = try {
        json.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        throw SourcePluginRepositoryException("Invalid plugin repository document", e)
    }

    private fun validateManifest(entry: SourcePluginIndexEntry, manifest: SourcePluginManifest) {
        require(manifest.id == entry.id) { "Manifest id does not match index entry ${entry.id}" }
        require(manifest.version == entry.version) { "Manifest version does not match index entry ${entry.id}" }
        require(manifest.id.isNotBlank()) { "Manifest id must not be blank" }
        require(manifest.id.isSafePluginId()) { "Invalid plugin id: ${manifest.id}" }
        require(manifest.version.isNotBlank()) { "Plugin ${manifest.id} has no version" }
        require(manifest.entryClass.isNotBlank()) { "Manifest entryClass must not be blank" }
        require(manifest.website.isHttpsUrl()) { "Plugin ${manifest.id} website must use HTTPS" }
        require(manifest.platforms.isNotEmpty()) { "Plugin ${manifest.id} has no supported platform" }
        require(manifest.platforms == entry.platforms) {
            "Manifest platforms do not match index entry ${entry.id}"
        }
        require(manifest.artifacts.keys.containsAll(manifest.platforms)) {
            "Plugin ${manifest.id} is missing an artifact for a declared platform"
        }
        manifest.artifacts.forEach { (platform, artifact) ->
            require(manifest.platforms.contains(platform)) {
                "Plugin ${manifest.id} contains an undeclared $platform artifact"
            }
            artifact.sha256.requireSha256()
            resolveUrl(artifact.url)
        }
        if (manifest.pluginApiVersion > SOURCE_PLUGIN_API_VERSION) {
            throw UnsupportedSourcePluginException(
                "Plugin ${manifest.id} requires plugin API ${manifest.pluginApiVersion}",
            )
        }
    }

    private fun resolveUrl(pathOrUrl: String): String {
        if (pathOrUrl.contains("://")) {
            val url = runCatching { Url(pathOrUrl) }.getOrElse {
                throw SourcePluginRepositoryException("Invalid repository URL", it)
            }
            val baseUrlObject = Url(baseUrl)
            require(url.protocol.name == "https") {
                "Repository URLs must use HTTPS"
            }
            require(url.host == baseUrlObject.host) {
                "Repository URL host is outside the configured repository"
            }
            return url.toString()
        }
        require(pathOrUrl.isSafeRepositoryPath()) {
            "Repository path must be relative and must not escape the repository"
        }
        return "${baseUrl.trimEnd('/')}/${pathOrUrl.trimStart('/')}"
    }
}

data class SourcePluginIndexFetchResult(
    val index: SourcePluginRepositoryIndex,
    val etag: String?,
    val fromCache: Boolean,
)

private fun Map<String, String>.headerValue(name: String): String? = entries
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value

private fun String.isSafeRepositoryPath(): Boolean {
    if (isBlank() || startsWith('/') || startsWith('\\') || contains(':')) return false
    return split('/', '\\').none { it == ".." || it == "." || it.isBlank() }
}

private fun String.isSafePluginId(): Boolean = matches(Regex("[a-z0-9][a-z0-9._-]{1,63}"))

private fun String.isHttpsUrl(): Boolean = runCatching { Url(this).protocol.name == "https" }.getOrDefault(false)

private fun String.requireSha256() {
    require(matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256 value" }
}

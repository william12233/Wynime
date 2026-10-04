/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import kotlinx.coroutines.CancellationException
import kotlinx.io.files.Path
import me.him188.ani.source.plugin.api.SourcePluginPlatform
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isRegularFile
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

class SourcePluginInstaller(
    private val repositoryClient: SourcePluginRepositoryClient,
    private val installedRepository: InstalledSourcePluginRepository,
    private val storage: SourcePluginStorage,
    private val platform: SourcePluginPlatform,
    private val hostVersion: String,
) {
    private val logger = logger<SourcePluginInstaller>()

    suspend fun install(
        entry: SourcePluginIndexEntry,
        validateLoaded: suspend (InstalledSourcePlugin) -> Unit = {},
    ): InstalledSourcePlugin {
        val manifest = repositoryClient.fetchManifest(entry)
        validateManifest(manifest)
        val artifact = manifest.artifacts[platform]
            ?: throw UnsupportedSourcePluginException("Plugin ${manifest.id} has no $platform artifact")

        val existing = installedRepository.snapshot().plugins.firstOrNull { it.id == manifest.id }
        if (existing?.version == manifest.version && existing.artifactPath.isUsableArtifact()) {
            return existing
        }

        storage.ensureRootDirectories()
        val staging = storage.newStagingDirectory(manifest.id, manifest.version)
        var committed: SystemPath? = null
        try {
            staging.createDirectories()
            val archiveBytes = repositoryClient.downloadArtifact(artifact)
            val expectedHash = artifact.sha256.lowercase()
            require(expectedHash.matches(HEX_SHA256)) { "Invalid SHA-256 for ${manifest.id}" }
            check(sha256Hex(archiveBytes).equals(expectedHash, ignoreCase = true)) {
                "SHA-256 mismatch for plugin ${manifest.id}"
            }

            val archive = staging.resolve("artifact.${artifact.format.name.lowercase()}")
            archive.writeBytes(archiveBytes)
            val extracted = extractSourcePluginPackage(
                archive = archive,
                format = artifact.format,
                destination = staging.resolve("package"),
            )
            check(extracted.exists() && extracted.isRegularFile()) {
                "Plugin ${manifest.id} package did not contain a loadable artifact"
            }

            committed = storage.commit(staging, manifest.id, manifest.version)
            val installed = InstalledSourcePlugin(
                id = manifest.id,
                version = manifest.version,
                manifest = manifest,
                artifactPath = extractedPathAfterCommit(extracted, staging, committed),
                enabled = existing?.enabled ?: true,
            )
            validateLoaded(installed)
            installedRepository.upsert(installed)
            if (existing != null && existing.version != installed.version) {
                runCatching {
                    storage.deleteInstalled(existing)
                }.onSuccess {
                    logger.info { "Removed replaced source plugin ${existing.id} ${existing.version}" }
                }.onFailure { throwable ->
                    // The new version is already committed and registered. A stale old
                    // directory is safe to clean up on a later install and must not make a
                    // validated update look failed or remove the new registry entry.
                    logger.warn(throwable) {
                        "Failed to remove replaced source plugin ${existing.id} ${existing.version}; keeping the new version"
                    }
                }
            }
            return installed
        } catch (e: CancellationException) {
            committed?.let {
                if (it.exists()) it.deleteRecursively()
            }
            storage.deleteStaging(staging)
            throw e
        } catch (e: Throwable) {
            committed?.let {
                if (it.exists()) it.deleteRecursively()
            }
            storage.deleteStaging(staging)
            throw e
        }
    }

    suspend fun uninstall(pluginId: String) {
        val removed = installedRepository.remove(pluginId) ?: return
        storage.deleteInstalled(removed)
    }

    private fun validateManifest(manifest: SourcePluginManifest) {
        require(manifest.id.isNotBlank()) { "Plugin id must not be blank" }
        require(manifest.platforms.contains(platform)) { "Plugin ${manifest.id} does not support $platform" }
        require(manifest.pluginApiVersion <= SOURCE_PLUGIN_API_VERSION) {
            "Plugin ${manifest.id} requires plugin API ${manifest.pluginApiVersion}"
        }
        require(compareSourcePluginVersions(hostVersion, manifest.minHostVersion) >= 0) {
            "Plugin ${manifest.id} requires a newer Wynime host"
        }
        require(manifest.entryClass.isNotBlank()) { "Plugin ${manifest.id} entryClass is blank" }
    }

    private fun extractedPathAfterCommit(
        extracted: SystemPath,
        staging: SystemPath,
        committed: SystemPath,
    ): String {
        val prefix = staging.absolutePath.trimEnd('\\', '/')
        val extractedPath = extracted.absolutePath
        require(extractedPath.startsWith(prefix)) { "Extracted artifact escaped staging directory" }
        return committed.resolve(extractedPath.removePrefix(prefix).trimStart('\\', '/')).absolutePath
    }

    private fun String.isUsableArtifact(): Boolean = isNotBlank() && runCatching {
        val path = Path(this).inSystem
        path.exists() && path.isRegularFile()
    }.getOrDefault(false)

    private companion object {
        val HEX_SHA256 = Regex("[0-9a-fA-F]{64}")
    }
}

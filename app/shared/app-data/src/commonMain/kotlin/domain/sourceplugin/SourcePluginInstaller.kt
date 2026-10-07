package com.wynime.app.domain.sourceplugin

import kotlinx.coroutines.CancellationException
import kotlinx.io.files.Path
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.isRegularFile
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.resolve
import com.wynime.utils.io.writeBytes
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn

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
        return installManifest(manifest, { artifact -> repositoryClient.downloadArtifact(artifact) }, validateLoaded)
    }

    suspend fun installBundled(
        manifest: SourcePluginManifest,
        readArtifact: suspend (SourcePluginArtifact) -> ByteArray,
        validateLoaded: suspend (InstalledSourcePlugin) -> Unit = {},
    ): InstalledSourcePlugin = installManifest(manifest, readArtifact, validateLoaded)

    private suspend fun installManifest(
        manifest: SourcePluginManifest,
        readArtifact: suspend (SourcePluginArtifact) -> ByteArray,
        validateLoaded: suspend (InstalledSourcePlugin) -> Unit,
    ): InstalledSourcePlugin {
        validateManifest(manifest)
        val artifact = manifest.artifacts[platform]
            ?: throw UnsupportedSourcePluginException("Plugin ${manifest.id} has no $platform artifact")

        val existing = installedRepository.snapshot().plugins.firstOrNull { it.id == manifest.id }
        val reusable = existing?.takeIf {
            it.version == manifest.version &&
                it.artifactPath.isUsableArtifact() &&
                it.manifest.artifacts[platform] == artifact
        }
        if (reusable != null) {
            try {
                validateLoaded(reusable)
                return reusable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) {
                    "Installed source plugin ${reusable.id} ${reusable.version} failed validation; reinstalling"
                }
            }
        }

        storage.ensureRootDirectories()
        val staging = storage.newStagingDirectory(manifest.id, manifest.version)
        var committed: SystemPath? = null
        try {
            staging.createDirectories()
            val archiveBytes = readArtifact(artifact)
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

            val staged = InstalledSourcePlugin(
                id = manifest.id,
                version = manifest.version,
                manifest = manifest,
                artifactPath = extracted.absolutePath,
                enabled = existing?.enabled ?: true,
            )
            validateLoaded(staged)

            committed = storage.commit(staging, manifest.id, manifest.version)
            val installed = staged.copy(
                artifactPath = extractedPathAfterCommit(extracted, staging, committed),
            )
            installedRepository.upsert(installed)
            if (existing != null && existing.version != installed.version) {
                runCatching {
                    storage.deleteInstalled(existing)
                }.onSuccess {
                    logger.info { "Removed replaced source plugin ${existing.id} ${existing.version}" }
                }.onFailure { throwable ->

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
        val installed = installedRepository.snapshot().plugins.firstOrNull { it.id == pluginId } ?: return

        storage.deleteInstalled(installed)
        installedRepository.remove(pluginId)
    }

    private fun validateManifest(manifest: SourcePluginManifest) {
        require(manifest.id.isNotBlank()) { "Plugin id must not be blank" }
        require(manifest.platforms.contains(platform)) { "Plugin ${manifest.id} does not support $platform" }
        require(manifest.pluginApiVersion == SOURCE_PLUGIN_API_VERSION) {
            "Plugin ${manifest.id} API ${manifest.pluginApiVersion} is incompatible with host API $SOURCE_PLUGIN_API_VERSION"
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

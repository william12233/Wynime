package com.wynime.app.domain.sourceplugin

import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.moveDirectoryRecursively
import com.wynime.utils.io.resolve
import kotlin.random.Random

class SourcePluginStorage(
    private val root: SystemPath,
) {
    private val installedRoot = root.resolve("installed")
    private val stagingRoot = root.resolve("staging")

    fun newStagingDirectory(pluginId: String, version: String): SystemPath {
        validatePathComponent(pluginId, "pluginId")
        validatePathComponent(version, "version")
        val suffix = Random.nextLong().toULong().toString(16)
        return stagingRoot.resolve("$pluginId-$version-$suffix")
    }

    fun installedDirectory(pluginId: String, version: String): SystemPath {
        validatePathComponent(pluginId, "pluginId")
        validatePathComponent(version, "version")
        return installedRoot.resolve(pluginId).resolve(version)
    }

    fun ensureRootDirectories() {
        root.createDirectories()
        installedRoot.createDirectories()
        stagingRoot.createDirectories()
    }

    fun deleteInstalled(plugin: InstalledSourcePlugin) {
        val target = installedDirectory(plugin.id, plugin.version)
        if (target.exists()) target.deleteRecursively()
    }

    fun deleteStaging(path: SystemPath) {
        if (path.exists()) path.deleteRecursively()
    }

    fun commit(staging: SystemPath, pluginId: String, version: String): SystemPath {
        val target = installedDirectory(pluginId, version)
        target.path.parent?.let { parent ->
            parent.inSystem.createDirectories()
        }
        staging.moveDirectoryRecursively(target)
        return target
    }

    companion object {
        fun defaultRoot(dataDir: SystemPath): SourcePluginStorage =
            SourcePluginStorage(dataDir.resolve("source-plugins"))

        private fun validatePathComponent(value: String, name: String) {
            require(value.isNotBlank()) { "$name must not be blank" }
            require(value != "." && value != "..") { "$name must not be a path marker" }
            require(value.none { it == '/' || it == '\\' || it == ':' }) {
                "$name must be a single path component"
            }
        }
    }
}

expect fun extractSourcePluginPackage(
    archive: SystemPath,
    format: SourcePluginArtifactFormat,
    destination: SystemPath,
): SystemPath

expect fun sha256Hex(bytes: ByteArray): String

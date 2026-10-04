/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.moveDirectoryRecursively
import me.him188.ani.utils.io.resolve
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

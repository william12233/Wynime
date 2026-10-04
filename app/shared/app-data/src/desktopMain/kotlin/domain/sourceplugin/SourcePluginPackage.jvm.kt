/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.io.files.Path
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.copyTo
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve

actual fun extractSourcePluginPackage(
    archive: SystemPath,
    format: SourcePluginArtifactFormat,
    destination: SystemPath,
): SystemPath {
    destination.createDirectories()
    return when (format) {
        SourcePluginArtifactFormat.JAR -> {
            val target = destination.resolve("plugin.jar")
            archive.copyTo(target)
            target
        }

        SourcePluginArtifactFormat.ZIP -> extractZip(archive, destination)
    }
}

actual fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }

private fun extractZip(archive: SystemPath, destination: SystemPath): SystemPath {
    val destinationFile = File(destination.absolutePath).canonicalFile
    destinationFile.mkdirs()
    var artifact: File? = null
    ZipInputStream(FileInputStream(File(archive.absolutePath))).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            val entryName = entry.name.replace('\\', '/')
            require(entryName.isNotBlank() && !entryName.startsWith('/') && !entryName.contains(':')) {
                "Unsafe plugin archive entry: ${entry.name}"
            }
            val target = File(destinationFile, entryName).canonicalFile
            require(target.path == destinationFile.path || target.path.startsWith(destinationFile.path + File.separator)) {
                "Plugin archive entry escapes destination: ${entry.name}"
            }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
                if (target.name.lowercase() in LOADABLE_ARTIFACT_NAMES) artifact = target
            }
            input.closeEntry()
        }
    }
    return requireNotNull(artifact) {
        "Plugin package must contain plugin.jar, plugin.dex, or plugin.apk"
    }.let { file ->
        Path(file.path).inSystem
    }
}

private val LOADABLE_ARTIFACT_NAMES = setOf("plugin.jar", "plugin.dex", "plugin.apk")

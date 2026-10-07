package com.wynime.app.domain.sourceplugin

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.io.files.Path
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.copyTo
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.resolve

actual fun extractSourcePluginPackage(
    archive: SystemPath,
    format: SourcePluginArtifactFormat,
    destination: SystemPath,
): SystemPath {
    destination.createDirectories()
    return when (format) {
        SourcePluginArtifactFormat.JAR -> destination.resolve("plugin.jar").also { archive.copyTo(it) }
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
                FileOutputStream(target).use { output -> input.copyTo(output) }
                if (target.name.lowercase() in LOADABLE_ARTIFACT_NAMES) artifact = target
            }
            input.closeEntry()
        }
    }
    return requireNotNull(artifact) {
        "Plugin package must contain plugin.jar, plugin.dex, or plugin.apk"
    }.let { file -> Path(file.path).inSystem }
}

private val LOADABLE_ARTIFACT_NAMES = setOf("plugin.jar", "plugin.dex", "plugin.apk")

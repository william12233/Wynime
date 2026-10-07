package com.wynime.app.ui.update.devbuild

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.toFile
import java.util.zip.ZipInputStream

internal actual suspend fun extractZipEntryByExtension(
    archive: SystemPath,
    extension: String,
    target: SystemPath,
): Boolean = withContext(Dispatchers.IO_) {
    val suffix = ".$extension"
    ZipInputStream(archive.toFile().inputStream().buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: return@withContext false
            if (!entry.isDirectory && entry.name.endsWith(suffix, ignoreCase = true)) {
                target.toFile().outputStream().buffered().use { output ->
                    zip.copyTo(output)
                }
                return@withContext true
            }
            zip.closeEntry()
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }
}

internal actual suspend fun markExecutable(file: SystemPath) {
    withContext(Dispatchers.IO_) {
        file.toFile().setExecutable(true, false)
    }
}

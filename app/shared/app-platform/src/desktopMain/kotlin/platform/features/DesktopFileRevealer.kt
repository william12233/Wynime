package com.wynime.app.platform.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.toFile
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatform
import java.awt.Desktop
import java.io.File

object DesktopFileRevealer : FileRevealer {
    private val logger = logger<DesktopFileRevealer>()
    override suspend fun revealFile(file: SystemPath): Boolean {
        return revealFile(file.toFile())
    }

    suspend fun revealFile(file: File): Boolean {
        if (highlightFile(file)) return true

        return try {
            withContext(Dispatchers.IO) {
                if (file.isDirectory) {
                    Desktop.getDesktop().open(file)
                } else {
                    Desktop.getDesktop().open(file.parentFile)
                }
            }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to reveal file: $file" }
            false
        }
    }

    private suspend fun highlightFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext false
        }

        try {
            when (currentPlatform()) {
                is Platform.Windows -> {

                    val command = listOf("explorer.exe", "/select,", file.absolutePath)
                    ProcessBuilder(command).start()
                    true
                }

                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }
}
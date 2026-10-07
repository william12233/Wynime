package com.wynime.app.tools.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.wynime.app.platform.ContextMP
import com.wynime.app.platform.ExecutableDirectoryDetector
import com.wynime.app.platform.features.DesktopFileRevealer
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.toFile
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.Platform
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

interface DesktopUpdateInstaller : UpdateInstaller {
    override suspend fun openForManualInstallation(file: SystemPath, context: ContextMP): Boolean {
        return DesktopFileRevealer.revealFile(file)
    }

    fun deleteOldUpdater()

    companion object {
        fun currentOS(): DesktopUpdateInstaller {
            return WindowsUpdateInstaller
        }
    }
}

private fun exitProcessForUpdate(delayMillis: Long): InstallationResult {
    thread(name = "ani-update-exit") {
        if (delayMillis > 0) Thread.sleep(delayMillis)
        exitProcess(0)
    }
    return InstallationResult.Succeed
}

object WindowsUpdateInstaller : DesktopUpdateInstaller {
    private val logger = logger<WindowsUpdateInstaller>()

    override val installablePackageExtensions: Set<String> = setOf("zip")

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        logger.info { "Installing update for Windows" }
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        logger.info { "Current app dir: ${appDir.absolutePath}" }
        if (!appDir.resolve("wynime.exe").exists()) {
            logger.info { "Current app dir does not have 'Wynime.exe'. Fallback to manual update" }
            return InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE)
        }

        val resourcesDir = File(
            System.getProperty("compose.application.resources.dir")
                ?: throw IllegalStateException("Cannot get resources directory"),
        )
        val updateExecutable = resourcesDir.resolve("ani_update.exe")
        if (!updateExecutable.exists()) {
            logger.info { "'ani_update.exe' not found. Fallback to manual update" }
            return InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE)
        }

        val copiedUpdateExecutable = appDir.resolve("ani_update.exe")
        updateExecutable.copyTo(copiedUpdateExecutable, true)

        ProcessBuilder(
            "cmd", "/c", "start", "cmd", "/c",
            "\"", copiedUpdateExecutable.absolutePath, file.absolutePath, appDir.absolutePath, "\"",
        )
            .directory(appDir)
            .start()

        logger.info { "Installer started" }
        return exitProcessForUpdate(delayMillis = 0)
    }

    override fun deleteOldUpdater() {
        val appDir = ExecutableDirectoryDetector.INSTANCE.getExecutableDirectory()
        val updateExecutable = appDir.resolve("ani_update.exe")
        if (updateExecutable.exists()) {
            updateExecutable.delete()
        }
    }
}

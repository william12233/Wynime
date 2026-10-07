package com.wynime.app.domain.update

import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.delete
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.isDirectory
import com.wynime.utils.io.isRegularFile
import com.wynime.utils.io.list
import com.wynime.utils.io.name
import com.wynime.utils.io.resolve
import com.wynime.utils.io.resolveSibling
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.koin.core.component.KoinComponent

class UpdateManager(

    val rootDir: SystemPath,
) : KoinComponent {
    companion object {
        private val logger = logger<UpdateManager>()

        const val SAVE_DIR_NAME = "download"
        const val DEV_BUILDS_DIR_NAME = "dev-builds"

        private val COMMIT_SHA_REGEX = Regex("[0-9a-f]{7,40}")

        fun devBuildShaOf(versionName: String): String? =
            versionName.substringAfterLast('-', "").takeIf { COMMIT_SHA_REGEX.matches(it) }
    }

    val saveDir: SystemPath = rootDir.resolve(SAVE_DIR_NAME)

    val devBuildsDir: SystemPath = rootDir.resolve(DEV_BUILDS_DIR_NAME)

    fun deleteInstalled(file: SystemPath, currentVersion: String) {
        if (file.name.contains(currentVersion)) {
            file.delete()
        }
    }

    fun deleteInstaller(file: SystemPath) {
        file.delete()
        file.resolveSibling(file.name + ".sha256").delete()
    }

    fun deleteStaleInstallers(keepFilenames: Collection<String>) {
        if (!saveDir.exists()) return
        for (entry in saveDir.list()) {
            val file = entry.inSystem
            if (file.name == ".DS_Store") continue
            if (!file.isRegularFile()) {
                logger.warn { "Unexpected non-file entry in update save dir, ignoring: $file" }
                continue
            }
            if (keepFilenames.any { file.name.contains(it) }) continue

            logger.info { "Deleting old installer: $file" }
            try {
                deleteInstaller(file)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to delete old installer, ignoring: $file" }
            }
        }
    }

    fun deleteInstalledFiles(currentVersion: String = currentWynimeBuildConfig.versionName) {
        deleteLegacyDevBuildsDir()
        deleteInstalledUpdate(currentVersion)
        deleteInstalledDevBuild(currentVersion)
    }

    private fun deleteInstalledUpdate(currentVersion: String) {
        if (!saveDir.exists()) return
        for (entry in saveDir.list()) {
            if (entry.name.contains(currentVersion)) {
                logger.info { "Deleting old installer file because it matches current version $currentVersion: $entry" }
                deleteInstaller(entry.inSystem)
            }
        }
    }

    private fun deleteInstalledDevBuild(currentVersion: String) {
        val sha = devBuildShaOf(currentVersion) ?: return
        if (!devBuildsDir.isDirectory()) return
        val installed = devBuildsDir.list().any { it.name.contains(sha) }
        if (!installed) return
        logger.info { "Deleting dev builds dir because it contains the package for current version $currentVersion: $devBuildsDir" }
        try {
            devBuildsDir.deleteRecursively()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to delete dev builds dir, ignoring: $devBuildsDir" }
        }
    }

    private fun deleteLegacyDevBuildsDir() {
        val legacy = saveDir.resolve(DEV_BUILDS_DIR_NAME)
        if (!legacy.isDirectory()) return
        logger.info { "Deleting legacy dev builds dir inside update save dir: $legacy" }
        try {
            legacy.deleteRecursively()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to delete legacy dev builds dir, ignoring: $legacy" }
        }
    }
}

@file:Suppress("FunctionName")

package com.wynime.app.desktop.storage

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.W32APIOptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal object WindowsJnaAppFolderResolver : AppFolderResolver {

    private interface Shell32 : Library {

        fun SHGetFolderPathW(
            hwndOwner: Pointer?,
            nFolder: Int,
            hToken: Pointer?,
            dwFlags: Int,
            pszPath: CharArray?
        ): Int

        companion object {
            val INSTANCE: Shell32 = Native.load(
                "shell32",
                Shell32::class.java,
                W32APIOptions.DEFAULT_OPTIONS,
            )
        }
    }

    private const val CSIDL_APPDATA = 0x001A
    private const val CSIDL_LOCAL_APPDATA = 0x001C
    private const val MAX_PATH = 260

    private fun getRoamingAppDataDirectory(
        organizationName: String,
        applicationName: String
    ): Path {
        val pathBuffer = CharArray(MAX_PATH)
        val result = Shell32.INSTANCE.SHGetFolderPathW(null, CSIDL_APPDATA, null, 0, pathBuffer)

        val appDataPath = if (result == 0) {

            Native.toString(pathBuffer)
        } else {

            System.getenv("APPDATA")
                ?: throw RuntimeException("Failed to retrieve APPDATA. SHGetFolderPath error code: $result")
        }

        val targetDir = Paths.get(appDataPath, organizationName, applicationName)
        ensureDirectoriesExist(targetDir)
        return targetDir
    }

    private fun getLocalAppDataDirectory(
        organizationName: String,
        applicationName: String
    ): Path {
        val pathBuffer = CharArray(MAX_PATH)
        val result = Shell32.INSTANCE.SHGetFolderPathW(null, CSIDL_LOCAL_APPDATA, null, 0, pathBuffer)

        val localAppDataPath = if (result == 0) {

            Native.toString(pathBuffer)
        } else {

            System.getenv("LOCALAPPDATA")
                ?: throw RuntimeException("Failed to retrieve LOCALAPPDATA. SHGetFolderPath error code: $result")
        }

        val targetDir = Paths.get(localAppDataPath, organizationName, applicationName)
        ensureDirectoriesExist(targetDir)
        return targetDir
    }

    @JvmStatic
    fun getAppDataDirectories(
        organizationName: String,
        applicationName: String
    ): AppDataDirectories {
        val roamingDir = getRoamingAppDataDirectory(organizationName, applicationName)
        val localDir = getLocalAppDataDirectory(organizationName, applicationName)
        return AppDataDirectories(roamingDir.resolve("data"), localDir.resolve("cache"))
    }

    private fun ensureDirectoriesExist(dir: Path) {
        if (Files.notExists(dir)) {
            try {
                Files.createDirectories(dir)
            } catch (e: Exception) {
                throw RuntimeException("Failed to create or access directory: $dir", e)
            }
        }
    }

    override fun resolve(appInfo: AppInfo): AppDataDirectories =
        getAppDataDirectories(appInfo.organization, appInfo.name)
}

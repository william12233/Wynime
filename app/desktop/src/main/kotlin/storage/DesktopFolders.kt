package com.wynime.app.desktop.storage

import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatformDesktop

data class AppInfo(
    val qualifier: String,
    val organization: String,
    val name: String,
)

interface AppFolderResolver {
    fun resolve(appInfo: AppInfo): AppDataDirectories

    companion object {
        val INSTANCE: AppFolderResolver by lazy {
            WindowsAppFolderResolver
        }
    }
}

object WindowsAppFolderResolver : AppFolderResolver {
    override fun resolve(appInfo: AppInfo): AppDataDirectories {
        return runCatching {
            WindowsJnaAppFolderResolver.resolve(appInfo)
        }

            .getOrThrow()
    }
}


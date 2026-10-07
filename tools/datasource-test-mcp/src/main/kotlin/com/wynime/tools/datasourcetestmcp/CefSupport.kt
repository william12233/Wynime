package com.wynime.tools.datasourcetestmcp

import com.wynime.app.platform.WynimeCefApp
import java.io.File

object McpCefApp {
    fun defaultWorkDir(): File = File(System.getProperty("java.io.tmpdir"))
        .resolve("wynime-source-plugin-media-test")
        .resolve("cef")

    suspend fun initialize(workDir: File = defaultWorkDir()) {
        WynimeCefApp.initialize(
            logDir = workDir.resolve("logs").also(File::mkdirs),
            cacheDir = workDir.resolve("cache").also(File::mkdirs),
        )
    }
}

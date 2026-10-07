package com.wynime.app.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.ScopedHttpClientUserAgent
import com.wynime.app.domain.foundation.get
import com.wynime.app.platform.WynimeCefApp
import com.wynime.app.platform.DesktopContext
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.tools.update.DefaultFileDownloader
import com.wynime.app.tools.update.InstallationResult
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.toKtPath
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.context.GlobalContext
import org.koin.mp.KoinPlatform
import org.openani.mediamp.ffmpeg.FFmpegKit
import java.io.File
import kotlin.system.exitProcess

object TestTasks {
    private val koin = GlobalContext.get()
    private val clientProvider by koin.inject<HttpClientProvider>()

    private val logger = logger<TestTasks>()
    fun handleTestTask(taskName: String, args: List<String>, context: DesktopContext): Nothing {
        when (taskName) {
            "mediamp-ffmpeg-smoke-test" -> {
                checkMediampFfmpeg()
                exitProcess(0)
            }

            "sqlite-bundled-load-test" -> {
                checkBundledSqlite()
                exitProcess(0)
            }

            "jcef-init-test" -> {
                checkJcef(context)
                exitProcess(0)
            }

            "download-update-and-install" -> {
                downloadUpdateAndInstall(args, context)
            }

            "sentry-dsn" -> {
                if (currentWynimeBuildConfig.sentryDsn.isBlank()) {
                    logger.error { "sentryDsn is empty" }
                    exitProcess(1)
                }
                exitProcess(0)
            }

            else -> {
                logger.error { "Unknown test task: $taskName" }
                exitProcess(1)
            }
        }
    }

    private fun checkMediampFfmpeg() {
        val runtimeDirectory = File(System.getProperty("compose.application.resources.dir"))
            .parentFile.absolutePath
        FFmpegKit.setRuntimeLibraryDirectory(runtimeDirectory, false)
        val result = runBlocking {
            FFmpegKit().execute(listOf("-version"))
        }
        check(result.isSuccess) { "FFmpeg smoke test failed: $result" }
    }

    private fun checkJcef(context: DesktopContext) {
        runBlocking {
            WynimeCefApp.initialize(
                logDir = context.logsDir,
                cacheDir = context.cacheDir.resolve("jcef-cache"),
            )
        }
        logger.info { "JCEF initialization check succeeded." }
        WynimeCefApp.disposeBlocking()
    }

    private fun checkBundledSqlite() {
        BundledSQLiteDriver().open(":memory:").use { }

    }

    private fun downloadUpdateAndInstall(args: List<String>, context: DesktopContext): Nothing {
        val url = args[0]

        val result = runBlocking {
            logger.info { "Downloading update from $url" }
            DefaultFileDownloader(clientProvider.get(ScopedHttpClientUserAgent.WYNIME)).download(
                listOf(url),
                saveDir = File(".").toKtPath().inSystem,
            ).also {
                logger.info { "Downloading done" }
            } ?: error("Download failed")
        }

                logger.info { "Performing install" }
                val updateInstaller = KoinPlatform.getKoin().get<UpdateInstaller>()
                val installationResult = updateInstaller.install(result, context)
                when (installationResult) {
                    InstallationResult.Succeed -> {

                        exitProcess(0)
                    }

                    InstallationResult.RequiresInstallPermission -> {
                        logger.error { "Install permission is required before installing update" }
                        exitProcess(1)
                    }

                    is InstallationResult.Failed -> {
                        logger.error { "Failed to install update: $installationResult" }
                        exitProcess(1)
                    }
                }

    }

}

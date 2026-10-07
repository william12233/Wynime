package com.wynime.app.desktop

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.platform.win32.WinUser.SW_SHOW
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatformDesktop
import kotlin.system.exitProcess

sealed interface SingleInstanceChecker {
    fun ensureSingleInstance()

    companion object {
        val instance by lazy {
            when (currentPlatformDesktop()) {
                is Platform.Windows -> WindowsSingleInstanceChecker
                else -> NoOpSingleInstanceChecker
            }
        }
    }
}

data object NoOpSingleInstanceChecker : SingleInstanceChecker {
    override fun ensureSingleInstance() {
    }
}

private const val WINDOW_NAME = "Wynime"

data object WindowsSingleInstanceChecker : SingleInstanceChecker {
    private val logger = logger<WindowsSingleInstanceChecker>()

    private var hMutex: WinNT.HANDLE? = null
    private const val MUTEX_NAME = "AniAppSingleInstanceMutex"

    override fun ensureSingleInstance() {
        try {
            val kernel32 = Kernel32.INSTANCE

            hMutex = kernel32.CreateMutex(null, true, MUTEX_NAME)

            val lastError = kernel32.GetLastError()
            if (hMutex == null || hMutex == WinBase.INVALID_HANDLE_VALUE) {
                error("Failed to create mutex. Error: $lastError")
            } else if (lastError == WinError.ERROR_ALREADY_EXISTS) {

                logger.warn("Another instance is already running.")
                kernel32.CloseHandle(hMutex)
                kotlin.runCatching { showWindowByName(WINDOW_NAME) }
                    .onFailure {
                        logger.error(it) { "Failed to bring window to front" }
                    }
                exitProcess(1)
            }

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    kernel32.ReleaseMutex(hMutex)
                    kernel32.CloseHandle(hMutex)
                },
            )

            logger.info("Application is running with mutex to prevent double instances: $MUTEX_NAME")
        } catch (e: Throwable) {
            logger.error(e) { "Failed to check single instance, ignoring and continuing" }
        }
    }

    private fun showWindowByName(windowTitle: String) {
        val user32 = User32.INSTANCE

        val hWnd: WinDef.HWND? = user32.FindWindow(null, windowTitle)

        if (hWnd == null) {
            logger.warn("Window with title '$windowTitle' not found.")
            return
        }

        user32.ShowWindow(hWnd, SW_RESTORE)
        user32.ShowWindow(hWnd, SW_SHOW)

        user32.SetForegroundWindow(hWnd)
    }
}


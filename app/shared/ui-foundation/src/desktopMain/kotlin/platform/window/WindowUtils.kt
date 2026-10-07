package com.wynime.app.platform.window

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowState
import com.wynime.app.platform.PlatformWindow
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatformDesktop
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.JComponent

@RequiresOptIn(
    message = "This is unsafe platform API, use [ComposeWindow.setTitleBar] instead.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafePlatformWindowApi

interface WindowUtils {
    @UnsafePlatformWindowApi
    fun setTitleBarColor(hwnd: Long, color: Color): Boolean {
        return false
    }

    @UnsafePlatformWindowApi
    fun setDarkTitleBar(hwnd: Long, dark: Boolean): Boolean {
        return false
    }

    suspend fun setUndecoratedFullscreen(window: PlatformWindow, windowState: WindowState, undecorated: Boolean) {
    }

    fun setPreventScreenSaver(prevent: Boolean) {
    }

    fun isCursorVisible(window: ComposeWindow): Boolean

    fun setCursorVisible(window: ComposeWindow, visible: Boolean) {
    }

    companion object {
        val instance by lazy {
            WindowsWindowUtils.instance
        }
    }
}

abstract class AwtWindowUtils : WindowUtils {
    companion object {
        val blankCursor: Cursor? by lazy {
            if (GraphicsEnvironment.isHeadless()) return@lazy null
            Toolkit.getDefaultToolkit().createCustomCursor(
                BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank cursor",
            )
        }
    }

    override fun isCursorVisible(window: ComposeWindow): Boolean = window.cursor != blankCursor

    override fun setCursorVisible(window: ComposeWindow, visible: Boolean) {
        if (GraphicsEnvironment.isHeadless()) return
        val cursor = if (visible) Cursor.getDefaultCursor() else blankCursor
        if (cursor != null) {
            window.cursor = cursor
            window.contentPane.cursor = cursor
        }
    }
}

@OptIn(UnsafePlatformWindowApi::class)
fun ComposeWindow.setTitleBar(color: Color, dark: Boolean) {
    if (currentPlatformDesktop() is Platform.Windows) {
        val winBuild = WindowsWindowUtils.instance.windowsBuildNumber()

        if (winBuild == null) return
        if (winBuild >= 22000) {
            WindowUtils.instance.setTitleBarColor(windowHandle, color)
        } else {
            WindowUtils.instance.setDarkTitleBar(windowHandle, dark)
        }
    }
}

fun Window.findSkiaLayer() = findComponent<SkiaLayer>()

private fun <T : JComponent> findComponent(
    container: Container,
    klass: Class<T>,
): T? {
    val componentSequence = container.components.asSequence()
    return componentSequence
        .filter { klass.isInstance(it) }
        .ifEmpty {
            componentSequence
                .filterIsInstance<Container>()
                .mapNotNull { findComponent(it, klass) }
        }.map { klass.cast(it) }
        .firstOrNull()
}

private inline fun <reified T : JComponent> Container.findComponent() = findComponent(this, T::class.java)

package com.wynime.app.platform.window

import androidx.annotation.Keep
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HMENU
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import com.sun.jna.platform.win32.WinUser.SWP_FRAMECHANGED
import com.sun.jna.platform.win32.WinUser.SWP_NOZORDER
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.platform.PlatformWindow
import com.wynime.app.platform.SavedWindowsWindowState
import com.wynime.app.platform.window.ExtendedUser32.Companion.MONITOR_DEFAULTTONEAREST
import com.wynime.app.platform.window.ExtendedUser32.Companion.SC_RESTORE
import com.wynime.app.platform.window.ExtendedUser32.Companion.SWP_NOACTIVATE
import com.wynime.app.platform.window.ExtendedUser32.Companion.WS_EX_CLIENTEDGE
import com.wynime.app.platform.window.ExtendedUser32.Companion.WS_EX_DLGMODALFRAME
import com.wynime.app.platform.window.ExtendedUser32.Companion.WS_EX_STATICEDGE
import com.wynime.app.platform.window.ExtendedUser32.Companion.WS_EX_WINDOWEDGE
import kotlin.math.roundToInt

@OptIn(UnsafePlatformWindowApi::class)
class WindowsWindowUtils : AwtWindowUtils() {
    private val dwmAPi: Dwmapi = Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    private val extendedUser32: ExtendedUser32 =
        Native.load("user32", ExtendedUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)

    override fun setTitleBarColor(hwnd: Long, color: Color): Boolean {
        return setTitleBarColor(HWND(Pointer.createConstant(hwnd)), argbToRgb(color.toArgb()))
    }

    override fun setDarkTitleBar(hwnd: Long, dark: Boolean): Boolean {
        return setDarkTitleBar(HWND(Pointer.createConstant(hwnd)), dark)
    }

    private fun argbToRgb(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF

        return (b shl 16) or (g shl 8) or r
    }

    private fun setTitleBarColor(hwnd: HWND, color: Int): Boolean {
        return kotlin.runCatching {
            val colorRef = IntByReference(color)
            W32Errors.SUCCEEDED(
                dwmAPi.DwmSetWindowAttribute(hwnd, Dwmapi.DWMWA_CAPTION_COLOR, colorRef.pointer, DWORD.SIZE),
            )
        }.getOrElse { false }
    }

    private fun setDarkTitleBar(hwnd: HWND, dark: Boolean): Boolean {
        return kotlin.runCatching {
            val isDarkRef = IntByReference(if (dark) 1 else 0)
            W32Errors.SUCCEEDED(
                dwmAPi.DwmSetWindowAttribute(hwnd, Dwmapi.DWMWA_USE_IMMERSIVE_DARK_MODE, isDarkRef.pointer, DWORD.SIZE),
            )
        }.getOrElse { false }
    }

    fun handleWindowProc(
        platformWindow: PlatformWindow,
        windowScope: WindowScope? = platformWindow.windowScope,
    ) {
        if (windowScope != null) {
            platformWindow.windowsWindowProc.value?.close()
            platformWindow.windowsWindowProc.tryEmit(
                if (platformWindow.layoutHitTestOwner == null) {
                    BasicWindowProc(
                        extendedUser32,
                        windowScope.window,
                    )
                } else {
                    ExtendedTitleBarWindowProc(
                        windowScope.window,
                        extendedUser32,
                        dwmAPi,
                    )
                },
            )
        }
    }

    suspend fun collectWindowProcHitTestProvider(
        platformWindow: PlatformWindow,
        hitTestOwner: WindowsWindowHitTestOwner
    ) {
        platformWindow
            .windowsWindowProc
            .filterIsInstance<ExtendedTitleBarWindowProc>()
            .collect { it.updateChildHitTestProvider(hitTestOwner) }
    }

    fun windowIsActive(platformWindow: PlatformWindow): Flow<Boolean?> {
        return platformWindow.windowsWindowProc
            .flatMapLatest { (it as? ExtendedTitleBarWindowProc)?.windowIsActive ?: flowOf(null) }
    }

    fun frameIsColorful(platformWindow: PlatformWindow): Flow<Boolean> {
        return platformWindow.windowsWindowProc
            .flatMapLatest { (it as? ExtendedTitleBarWindowProc)?.frameIsColorful ?: flowOf(false) }
    }

    fun restoreWindow(windowHandle: Long) {
        extendedUser32.ShowWindow(HWND(Pointer(windowHandle)), WinUser.SW_RESTORE)
    }

    fun minimizeWindow(windowHandle: Long) {
        extendedUser32.CloseWindow(HWND(Pointer(windowHandle)))
    }

    fun maximizeWindow(windowHandle: Long) {
        extendedUser32.ShowWindow(HWND(Pointer(windowHandle)), WinUser.SW_MAXIMIZE)
    }

    fun disposeWindowProc(platformWindow: PlatformWindow) {
        platformWindow.windowsWindowProc.value?.close()
        platformWindow.windowsWindowProc.tryEmit(null)
    }

    fun windowsBuildNumber(): Int? = kotlin.runCatching {
        Advapi32Util.registryGetStringValue(
            WinReg.HKEY_LOCAL_MACHINE,
            "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
            "CurrentBuildNumber",
        ).toIntOrNull()
    }.getOrElse { null }

    override suspend fun setUndecoratedFullscreen(
        window: PlatformWindow,
        windowState: WindowState,
        undecorated: Boolean
    ) {

        val hwnd = HWND(Pointer.createConstant(window.windowHandle))
        if (undecorated) {
            val maximised = extendedUser32.IsZoomed(hwnd)
            if (maximised) {

                extendedUser32.SendMessage(
                    hwnd,
                    User32.WM_SYSCOMMAND,
                    WinDef.WPARAM(SC_RESTORE.toLong()),
                    WinDef.LPARAM(0),
                )
            }

            val currentStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            if (currentStyle and WinUser.WS_CAPTION == 0) {

                return
            }

            window.savedWindowsWindowState = SavedWindowsWindowState(
                style = currentStyle,
                exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE),
                rect = RECT().apply {
                    User32.INSTANCE.GetWindowRect(hwnd, this)
                }.toComposeRect(),
                maximized = maximised,
            )

            (window.windowsWindowProc.value as? ExtendedTitleBarWindowProc)
                ?.applyWindowCompositionMode(fullscreen = true)

            User32.INSTANCE.SetWindowLong(
                hwnd, WinUser.GWL_STYLE,
                currentStyle and (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME).inv(),
            )

            User32.INSTANCE.SetWindowLong(
                hwnd, WinUser.GWL_EXSTYLE,
                User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
                    .and((WS_EX_DLGMODALFRAME or WS_EX_WINDOWEDGE or WS_EX_CLIENTEDGE or WS_EX_STATICEDGE).inv()),
            )

            val rect = getMonitorInfo(hwnd).rcMonitor!!

            extendedUser32.SetWindowPos(
                hwnd,
                null,
                rect.left,
                rect.top,
                rect.right - rect.left,
                rect.bottom - rect.top,
                SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
            )
            window.onWindowsUndecoratedFullscreenStateChange(true)
        } else {

            val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            if (style and WinUser.WS_CAPTION != 0) {

                return
            }

            val savedWindowState = checkNotNull(window.savedWindowsWindowState) {
                "window.savedWindowState is null, cannot restore window state"
            }
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, savedWindowState.style)
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, savedWindowState.exStyle)
            savedWindowState.rect.run {
                User32.INSTANCE.SetWindowPos(
                    hwnd, null,
                    left.roundToInt(), top.roundToInt(), (right - left).roundToInt(), (bottom - top).roundToInt(),
                    SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
                )
            }
            if (savedWindowState.maximized) {
                User32.INSTANCE.SendMessage(
                    hwnd,
                    User32.WM_SYSCOMMAND,
                    WinDef.WPARAM(WinUser.SC_MAXIMIZE.toLong()),
                    WinDef.LPARAM(0),
                )
            }
            window.savedWindowsWindowState = null
            (window.windowsWindowProc.value as? ExtendedTitleBarWindowProc)
                ?.applyWindowCompositionMode(fullscreen = false)
            window.onWindowsUndecoratedFullscreenStateChange(false)
        }
    }

    private fun getMonitorInfo(hwnd: HWND): MONITORINFO {
        return MONITORINFO().apply {
            extendedUser32.GetMonitorInfoA(
                extendedUser32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST),
                this,
            )
        }
    }

    override fun setPreventScreenSaver(prevent: Boolean) {
        if (prevent) {
            Kernel32.INSTANCE.SetThreadExecutionState(Kernel32.ES_CONTINUOUS or Kernel32.ES_SYSTEM_REQUIRED or Kernel32.ES_DISPLAY_REQUIRED)
        } else {
            Kernel32.INSTANCE.SetThreadExecutionState(Kernel32.ES_CONTINUOUS)
        }
    }

    companion object {
        val instance: WindowsWindowUtils by lazy { WindowsWindowUtils() }
    }
}

private fun RECT.toComposeRect(): Rect {
    return Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

@Keep
@Suppress("FunctionName", "SpellCheckingInspection")
internal interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): Int

    fun DwmExtendFrameIntoClientArea(hwnd: HWND, margins: WindowMargins): LRESULT

    @Structure.FieldOrder(
        "leftBorderWidth",
        "rightBorderWidth",
        "topBorderHeight",
        "bottomBorderHeight",
    )
    data class WindowMargins(
        @JvmField var leftBorderWidth: Int,
        @JvmField var rightBorderWidth: Int,
        @JvmField var topBorderHeight: Int,
        @JvmField var bottomBorderHeight: Int
    ) : Structure(), Structure.ByReference

    companion object {

        const val DWMWA_USE_IMMERSIVE_DARK_MODE: Int = 20
        const val DWMWA_CAPTION_COLOR: Int = 35
    }
}

internal interface ExtendedUser32 : User32 {
    fun GetPointerInfo(pointerId: Int, pointerInfo: POINTER_INFO): Boolean

    fun IsZoomed(hWnd: HWND?): Boolean

    fun MonitorFromWindow(hWnd: HWND?, dwFlags: DWORD?): Pointer?

    fun GetMonitorInfoA(hMonitor: Pointer?, lpMonitorInfo: MONITORINFO?): Boolean

    override fun SendMessage(hWnd: HWND, Msg: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): LRESULT

    fun ScreenToClient(
        hWnd: HWND,
        lpPoint: POINT,
    ): Boolean

    fun GetSystemMetricsForDpi(
        nIndex: Int,
        dpi: UINT,
    ): Int

    fun GetDpiForWindow(hWnd: HWND): UINT

    fun GetSystemMenu(hWnd: HWND, bRevert: Boolean): HMENU?

    fun SetMenuItemInfo(hMenu: HMENU, uItem: Int, fByPosition: Boolean, lpmii: MENUITEMINFO): Boolean

    fun TrackPopupMenu(hMenu: HMENU, uFlags: Int, x: Int, y: Int, nReserved: Int, hWnd: HWND, prcRect: RECT?): Int

    fun SetMenuDefaultItem(hMenu: HMENU, uItem: Int, fByPos: Boolean): Boolean

    @Suppress("SpellCheckingInspection", "unused")
    class MENUITEMINFO : Structure() {
        @JvmField
        var cbSize: Int = 0

        @JvmField
        var fMask: Int = 0

        @JvmField
        var fType: Int = 0

        @JvmField
        var fState: Int = 0

        @JvmField
        var wID: Int = 0

        @JvmField
        var hSubMenu: HMENU? = null

        @JvmField
        var hbmpChecked: HBITMAP? = null

        @JvmField
        var hbmpUnchecked: HBITMAP? = null

        @JvmField
        var dwItemData: ULONG_PTR = ULONG_PTR(0)

        @JvmField
        var dwTypeData: String? = null

        @JvmField
        var cch: Int = 0

        @JvmField
        var hbmpItem: HBITMAP? = null

        override fun getFieldOrder(): List<String> {
            return listOf(
                "cbSize", "fMask", "fType", "fState", "wID", "hSubMenu", "hbmpChecked", "hbmpUnchecked",
                "dwItemData", "dwTypeData", "cch", "hbmpItem",
            )
        }
    }

    companion object {
        const val SC_RESTORE: Int = 0x0000f120
        const val SC_MOVE: Int = 0xF010
        const val SC_SIZE: Int = 0xF000
        const val SC_CLOSE: Int = 0xF060

        const val WINT_MAX: Int = 0xFFFF

        const val MIIM_STATE: Int = 0x00000001

        const val MFT_STRING: Int = 0x00000000

        const val TPM_RETURNCMD: Int =
            0x0100

        const val MFS_ENABLED: Int = 0x00000000

        const val MFS_DISABLED: Int = 0x00000003

        const val WM_NCCALCSIZE: Int = 0x0083

        const val WM_NCHITTEST: Int = 0x0084

        const val WM_MOUSEMOVE: Int = 0x0200

        const val WM_LBUTTONDOWN: Int = 0x0201

        const val WM_LBUTTONUP: Int = 0x0202

        const val WM_NCMOUSEMOVE: Int = 0x00A0

        const val WM_NCLBUTTONDOWN: Int = 0x00A1

        const val WM_NCLBUTTONUP: Int = 0x00A2

        val WM_NCRBUTTONUP: Int = 0x00A5

        const val WM_ACTIVATE: Int = 0x0006

        const val WM_SETTINGCHANGE: Int = 0x001A

        const val WA_INACTIVE: Int = 0x00000000

        const val WS_EX_DLGMODALFRAME: Int = 0x00000001
        const val WS_EX_WINDOWEDGE: Int = 0x00000100
        const val WS_EX_CLIENTEDGE: Int = 0x00000200
        const val WS_EX_STATICEDGE: Int = 0x00020000

        const val SWP_NOACTIVATE: Int = 0x0010

        val MONITOR_DEFAULTTONEAREST: DWORD = DWORD(2)
    }
}

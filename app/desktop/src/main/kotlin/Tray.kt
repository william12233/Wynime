package com.wynime.app.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.wynime.app.data.models.preference.DesktopCloseBehavior
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.desktop_tray_open
import com.wynime.app.ui.lang.settings_app_close_behavior_exit
import org.jetbrains.compose.resources.stringResource
import java.awt.SystemTray
import com.kdroid.composetray.tray.api.Tray as ComposeNativeTray

@Composable
fun rememberWynimeTrayState(): WynimeTrayState {
    val isTraySupported = remember { SystemTray.isSupported() }
    return remember(isTraySupported) {
        WynimeTrayState(isTraySupported = isTraySupported)
    }
}

@Stable
class WynimeTrayState internal constructor(
    val isTraySupported: Boolean,
) {
    var isWindowHiddenToTray by mutableStateOf(false)
        private set

    fun restoreWindow() {
        isWindowHiddenToTray = false
    }

    fun minimizeToTray(onExit: () -> Unit) {
        if (isTraySupported) {
            isWindowHiddenToTray = true
        } else {
            onExit()
        }
    }

    fun handleCloseRequest(
        closeBehavior: DesktopCloseBehavior,
        onExit: () -> Unit,
    ) {
        when {
            !isTraySupported -> onExit()
            closeBehavior == DesktopCloseBehavior.MINIMIZE -> minimizeToTray(onExit)
            else -> onExit()
        }
    }
}

@Composable
fun ApplicationScope.WynimeSystemTray(
    state: WynimeTrayState,
    icon: Painter,
    tooltip: String,
    onExit: () -> Unit,
) {
    if (!state.isTraySupported || !state.isWindowHiddenToTray) {
        return
    }

    val openTrayText = stringResource(Lang.desktop_tray_open)
    val exitTrayText = stringResource(Lang.settings_app_close_behavior_exit)

    NativeTray(
        icon = icon,
        tooltip = tooltip,
        primaryAction = state::restoreWindow,
    ) {
        Item(
            label = openTrayText,
            onClick = state::restoreWindow,
        )
        Divider()
        Item(
            label = exitTrayText,
            onClick = onExit,
        )
    }
}

@Composable
private fun ApplicationScope.NativeTray(
    icon: Painter,
    tooltip: String,
    primaryAction: () -> Unit,
    menuContent: TrayMenuBuilder.() -> Unit,
) {
    ComposeNativeTray(
        icon = icon,
        tooltip = tooltip,
        primaryAction = primaryAction,
        menuContent = menuContent,
    )
}

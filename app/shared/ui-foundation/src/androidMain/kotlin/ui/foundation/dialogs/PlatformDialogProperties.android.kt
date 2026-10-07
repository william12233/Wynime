package com.wynime.app.ui.foundation.dialogs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

@Suppress("FunctionName")
actual fun PlatformDialogPropertiesImpl(
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean,
    usePlatformDefaultWidth: Boolean,
    excludeFromSystemGesture: Boolean,
    usePlatformInsets: Boolean,
    decorFitsSystemWindows: Boolean,
    scrimColor: Color,
    animateTransition: Boolean,
): DialogProperties {
    return DialogProperties(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        decorFitsSystemWindows = decorFitsSystemWindows,
    )
}
package com.wynime.app.ui.foundation.dialogs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

@Suppress("FunctionName")
expect fun PlatformDialogPropertiesImpl(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = false,

    excludeFromSystemGesture: Boolean = true,

    usePlatformInsets: Boolean = true,
    decorFitsSystemWindows: Boolean = true,
    scrimColor: Color = Color.Transparent,
    animateTransition: Boolean = true,
): DialogProperties

@Suppress("FunctionName")
fun PlatformDialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = false,

    excludeFromSystemGesture: Boolean = true,
    clippingEnabled: Boolean = true,
    decorFitsSystemWindows: Boolean = true,

    usePlatformInsets: Boolean = true,
    scrimColor: Color = Color.Transparent,
    animateTransition: Boolean = true,
): DialogProperties = PlatformDialogPropertiesImpl(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
    excludeFromSystemGesture = excludeFromSystemGesture,
    usePlatformInsets = usePlatformInsets,
    decorFitsSystemWindows = decorFitsSystemWindows,
    scrimColor = scrimColor,
    animateTransition = animateTransition,
)

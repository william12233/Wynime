package com.wynime.app.ui.foundation.layout

import androidx.compose.runtime.Composable
import com.wynime.app.platform.Context

expect suspend fun Context.setRequestFullScreen(window: PlatformWindowMP, fullscreen: Boolean)

expect fun Context.setSystemBarVisible(window: PlatformWindowMP, visible: Boolean)

@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
@Composable
inline fun isSystemInFullscreen(): Boolean = LocalPlatformWindow.current.isUndecoratedFullscreen

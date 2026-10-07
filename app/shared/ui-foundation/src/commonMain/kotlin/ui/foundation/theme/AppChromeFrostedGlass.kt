package com.wynime.app.ui.foundation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.wynime.app.ui.foundation.ifThen

val LocalAppChromeHazeState = staticCompositionLocalOf<HazeState?> { null }

val LocalAppChromeOverlayInsets = compositionLocalOf<WindowInsets> { WindowInsets(0, 0, 0, 0) }

@Composable
fun isAppChromeFrostedGlassActive(): Boolean {
    return LocalThemeSettings.current.enableFrostedGlassEffect &&
            LocalAppChromeHazeState.current != null
}

@Composable
fun Modifier.appChromeHazeSource(backgroundColor: Color = Color.Unspecified): Modifier {
    val hazeState = LocalAppChromeHazeState.current ?: return this
    if (!isAppChromeFrostedGlassActive()) return this

    return hazeSource(hazeState)
        .ifThen(backgroundColor.isSpecified) { background(backgroundColor) }
}

@Composable
fun Modifier.appChromeFrostedGlass(
    enabled: Boolean,
    containerColor: Color,
): Modifier {
    val hazeState = LocalAppChromeHazeState.current ?: return this
    if (!enabled) return this

    return hazeEffect(state = hazeState) {
        blurRadius = 24.dp
        tints = listOf(HazeTint(containerColor.copy(alpha = 0.8f)))
        noiseFactor = 0.08f
    }
}

package com.wynime.app.ui.foundation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSystemDarkThemeOverride: ProvidableCompositionLocal<Boolean?> = staticCompositionLocalOf { null }

@Composable
fun isSystemInDarkThemeDetected(): Boolean = LocalSystemDarkThemeOverride.current ?: isSystemInDarkTheme()

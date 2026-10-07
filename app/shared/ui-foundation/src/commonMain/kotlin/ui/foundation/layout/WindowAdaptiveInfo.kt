package com.wynime.app.ui.foundation.layout

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

@Composable
expect fun currentWindowAdaptiveInfo1(): WindowAdaptiveInfo

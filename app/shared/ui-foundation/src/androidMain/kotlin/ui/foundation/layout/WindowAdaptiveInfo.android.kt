package com.wynime.app.ui.foundation.layout

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable

@Suppress("NOTHING_TO_INLINE")
@Composable
actual inline fun currentWindowAdaptiveInfo1(): WindowAdaptiveInfo =
    androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()

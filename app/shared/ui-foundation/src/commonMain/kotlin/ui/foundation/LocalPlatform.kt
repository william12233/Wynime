package com.wynime.app.ui.foundation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wynime.utils.platform.currentPlatform

@Stable
val LocalPlatform = staticCompositionLocalOf {
    currentPlatform()
}

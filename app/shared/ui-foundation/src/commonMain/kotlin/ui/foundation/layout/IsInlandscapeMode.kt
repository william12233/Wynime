package com.wynime.app.ui.foundation.layout

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.dp
import com.wynime.app.platform.DeviceOrientation

@Composable
fun isInLandscapeMode(): Boolean = LocalPlatformWindow.current.deviceOrientation == DeviceOrientation.LANDSCAPE

@Stable
fun BoxWithConstraintsScope.showTabletUI(): Boolean {

    return maxWidth >= 600.dp && maxHeight >= 600.dp
}

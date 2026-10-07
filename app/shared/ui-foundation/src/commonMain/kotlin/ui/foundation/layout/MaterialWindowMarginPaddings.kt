package com.wynime.app.ui.foundation.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp

fun Modifier.materialWindowMarginPadding(): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "materialWindowMarginPadding"
    },
) {

    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
    val values = when {
        windowSizeClass.isWidthCompact -> {

            MaterialWindowMarginPaddings.COMPACT
        }

        else -> {
            MaterialWindowMarginPaddings.EXPANDED
        }
    }
    padding(values)
}

private object MaterialWindowMarginPaddings {

    val COMPACT = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
    val EXPANDED = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
}

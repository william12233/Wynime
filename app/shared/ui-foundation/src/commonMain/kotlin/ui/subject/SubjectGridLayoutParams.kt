package com.wynime.app.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightCompact
import com.wynime.app.ui.foundation.layout.isWidthAtLeastExpanded
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthCompact

@Immutable
data class SubjectGridLayoutParams(
    val gridCells: GridCells,
    val horizontalArrangement: Arrangement.Horizontal,
    val verticalArrangement: Arrangement.Vertical,
    val cardShape: Shape,
)

object SubjectGridDefaults {
    @Composable
    fun coverLayoutParameters(windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()): SubjectGridLayoutParams {
        val windowSizeClass = windowAdaptiveInfo.windowSizeClass

        val arrangement = when {
            windowSizeClass.isWidthAtLeastExpanded -> Arrangement.spacedBy(16.dp)
            windowSizeClass.isWidthAtLeastMedium -> Arrangement.spacedBy(12.dp)
            else -> Arrangement.spacedBy(8.dp)
        }
        return SubjectGridLayoutParams(
            gridCells = when {
                windowSizeClass.isWidthCompact || windowSizeClass.isHeightCompact -> GridCells.Adaptive(minSize = 100.dp)

                windowSizeClass.isWidthAtLeastBreakpoint(1200) -> {
                    GridCells.Adaptive(minSize = 180.dp)
                }

                windowSizeClass.isWidthAtLeastExpanded -> {
                    GridCells.Adaptive(minSize = 150.dp)
                }

                windowSizeClass.isWidthAtLeastMedium -> {
                    GridCells.Adaptive(minSize = 128.dp)
                }

                else -> {
                    GridCells.Adaptive(minSize = 100.dp)
                }
            },
            horizontalArrangement = arrangement,
            verticalArrangement = arrangement,
            cardShape = MaterialTheme.shapes.large,
        )
    }
}

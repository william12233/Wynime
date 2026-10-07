package com.wynime.app.ui.foundation.layout

import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.runtime.Stable

@Stable
sealed class ThreePaneScaffoldValueConverter {
    abstract fun convert(value: ThreePaneScaffoldValue): ThreePaneScaffoldValue

    @Stable
    data object ExtraPaneForNestedDetails : ThreePaneScaffoldValueConverter() {
        override fun convert(value: ThreePaneScaffoldValue): ThreePaneScaffoldValue {
            return if (value.tertiary == PaneAdaptedValue.Expanded && value.secondary == PaneAdaptedValue.Expanded) {

                ThreePaneScaffoldValue(
                    primary = PaneAdaptedValue.Expanded,
                    secondary = PaneAdaptedValue.Hidden,
                    tertiary = PaneAdaptedValue.Expanded,
                )
            } else {
                value
            }
        }
    }
}

@Stable
fun ThreePaneScaffoldValue.convert(
    converter: ThreePaneScaffoldValueConverter,
): ThreePaneScaffoldValue = converter.convert(this@convert)

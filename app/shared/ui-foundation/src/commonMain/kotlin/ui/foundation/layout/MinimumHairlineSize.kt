package com.wynime.app.ui.foundation.layout

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Stable
private val MinimumHairlineSize = Modifier.sizeIn(
    minHeight = Dp.Hairline,
    minWidth = Dp.Hairline,
)

@Stable
fun Modifier.minimumHairlineSize() = this then MinimumHairlineSize

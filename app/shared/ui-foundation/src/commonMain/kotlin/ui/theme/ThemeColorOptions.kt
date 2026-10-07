package com.wynime.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults

private val colorList = ArrayList<Color>(11)
    .apply {
        add(getHctColor(4))
        add(getHctColor(5))
        add(getHctColor(6))
        add(getHctColor(7))
        add(getHctColor(8))
        add(DefaultSeedColor)
        add(getHctColor(9))
        add(getHctColor(10))
        add(getHctColor(1))
        add(getHctColor(2))
        add(getHctColor(3))
    }

private fun getHctColor(base: Int): Color {
    return Color(Hct.from(base * 35.0, 40.0, 40.0).toInt())
}

@Suppress("UnusedReceiverParameter")
val WynimeThemeDefaults.themeColorOptions: List<Color>
    get() = colorList
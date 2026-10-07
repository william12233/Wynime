package com.wynime.app.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.wynime.utils.platform.Platform
import java.awt.Dimension
import java.awt.GraphicsEnvironment

object ScreenUtils {

    fun getScreenSize(): DpSize {
        val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val dimension: Dimension = graphicsEnvironment.maximumWindowBounds.size
        return DpSize(dimension.width.dp, dimension.height.dp)
    }
}

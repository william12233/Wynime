package com.wynime.app.ui.foundation.imageviewer

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.min

internal data class ImageViewerWindowBounds(
    val position: DpOffset,
    val size: DpSize,
)

internal val IMAGE_VIEWER_WINDOW_MIN_SIZE: DpSize = DpSize(480.dp, 360.dp)

internal const val IMAGE_VIEWER_WINDOW_MAX_SCREEN_FRACTION: Float = 0.9f

internal fun computeImageViewerWindowBounds(
    imageSize: IntSize,
    density: Float,
    screen: DpRect,
    minSize: DpSize = IMAGE_VIEWER_WINDOW_MIN_SIZE,
    maxScreenFraction: Float = IMAGE_VIEWER_WINDOW_MAX_SCREEN_FRACTION,
): ImageViewerWindowBounds {
    val maxWidth = screen.width * maxScreenFraction
    val maxHeight = screen.height * maxScreenFraction

    val imageWidth = imageSize.width.coerceAtLeast(1) / density.coerceAtLeast(0.01f)
    val imageHeight = imageSize.height.coerceAtLeast(1) / density.coerceAtLeast(0.01f)
    val scale = min(1f, min(maxWidth.value / imageWidth, maxHeight.value / imageHeight))

    val width = (imageWidth * scale).dp
    val height = (imageHeight * scale).dp
    val size = DpSize(
        width.coerceIn(minOf(minSize.width, maxWidth), maxWidth),
        height.coerceIn(minOf(minSize.height, maxHeight), maxHeight),
    )
    val position = DpOffset(
        x = screen.left + (screen.width - size.width) / 2,
        y = screen.top + (screen.height - size.height) / 2,
    )
    return ImageViewerWindowBounds(position, size)
}

internal fun screenDensity(window: Window): Float =
    window.graphicsConfiguration.defaultTransform.scaleX.toFloat()

internal fun usableScreenArea(window: Window): DpRect {
    val gc = window.graphicsConfiguration
    val bounds = gc.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return DpRect(
        left = (bounds.x + insets.left).dp,
        top = (bounds.y + insets.top).dp,
        right = (bounds.x + bounds.width - insets.right).dp,
        bottom = (bounds.y + bounds.height - insets.bottom).dp,
    )
}

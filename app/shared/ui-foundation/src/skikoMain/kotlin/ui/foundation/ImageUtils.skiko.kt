package com.wynime.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage

actual fun ImageBitmap.resize(
    width: Int,
    height: Int,
): ImageBitmap {

    val skiaBitmap = this.asSkiaBitmap()

    val imageInfo = ImageInfo.makeN32Premul(width, height)
    val surface = Surface.makeRaster(imageInfo)
    val canvas = surface.canvas

    val originalImage = SkiaImage.makeFromBitmap(skiaBitmap)

    val srcRect = Rect(0f, 0f, skiaBitmap.width.toFloat(), skiaBitmap.height.toFloat())

    val destRect = Rect(0f, 0f, width.toFloat(), height.toFloat())

    canvas.drawImageRect(originalImage, srcRect, destRect, Paint())

    return surface.makeImageSnapshot().toComposeImageBitmap()
}

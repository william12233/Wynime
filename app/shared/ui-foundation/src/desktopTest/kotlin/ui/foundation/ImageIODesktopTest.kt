package com.wynime.app.ui.foundation

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageIODesktopTest {
    private fun makeTestImageBytes(width: Int, height: Int): ByteArray {

        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas

        fun paint(color: Int): Paint = Paint().apply { this.color = color }

        val midX = width / 2f
        val midY = height / 2f
        canvas.drawRect(Rect.makeXYWH(0f, 0f, midX, midY), paint(0xFFFF0000.toInt()))
        canvas.drawRect(Rect.makeXYWH(midX, 0f, width - midX, midY), paint(0xFF00FF00.toInt()))
        canvas.drawRect(Rect.makeXYWH(0f, midY, midX, height - midY), paint(0xFF0000FF.toInt()))
        canvas.drawRect(Rect.makeXYWH(midX, midY, width - midX, height - midY), paint(0xFFFFFFFF.toInt()))

        val image = surface.makeImageSnapshot()
        return image.encodeToData(EncodedImageFormat.PNG, 100)!!.bytes
    }

    private fun decodeResultAndSampleCenter(bytes: ByteArray): Int {
        val img = Image.makeFromEncoded(bytes)
        val centerX = img.width / 2
        val centerY = img.height / 2
        val bmp = org.jetbrains.skia.Bitmap()
        bmp.allocPixels(ImageInfo.makeN32Premul(1, 1))
        val success = img.readPixels(bmp, centerX, centerY)
        assertTrue(success, "Failed to read pixels from output image")
        return bmp.getColor(0, 0)
    }

    @Test
    fun cropTopLeft_quadrant_isRed() {
        val src = makeTestImageBytes(400, 300)
        val size = 100
        val out = cropImageToSquare(src, CropRect(10, 10, size), outputSize = 256)
        val color = decodeResultAndSampleCenter(out)

        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        assertTrue(r > 200 && g < 80 && b < 80, "Center pixel not red-ish: r=$r g=$g b=$b")
    }

    @Test
    fun cropBottomRight_quadrant_isWhite() {
        val src = makeTestImageBytes(400, 300)
        val size = 120
        val out = cropImageToSquare(src, CropRect(400 - size - 5, 300 - size - 5, size), outputSize = 256)
        val color = decodeResultAndSampleCenter(out)
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        assertTrue(r > 220 && g > 220 && b > 220, "Center pixel not white-ish: r=$r g=$g b=$b")
    }

    @Test
    fun cropOutOfBounds_isClamped() {
        val src = makeTestImageBytes(400, 300)
        val out = cropImageToSquare(src, CropRect(-50, -40, 150), outputSize = 128)

        val color = decodeResultAndSampleCenter(out)
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        assertTrue(r > 200 && g < 80 && b < 80, "Center pixel not red-ish after clamp: r=$r g=$g b=$b")
    }
}

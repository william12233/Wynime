package com.wynime.app.ui.foundation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return bmp.asImageBitmap()
}

actual fun cropImageToSquare(imageData: ByteArray, crop: CropRect, outputSize: Int, jpegQuality: Int): ByteArray {
    val src = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
    val safeSize = min(min(crop.size, src.width), src.height)
    val safeX = min(max(0, crop.x), max(0, src.width - safeSize))
    val safeY = min(max(0, crop.y), max(0, src.height - safeSize))

    val cropped = Bitmap.createBitmap(src, safeX, safeY, safeSize, safeSize)
    val scaled = cropped.scale(outputSize, outputSize)

    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), baos)
    return baos.toByteArray()
}


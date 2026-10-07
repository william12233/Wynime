package com.wynime.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

data class CropRect(val x: Int, val y: Int, val size: Int)

expect fun cropImageToSquare(imageData: ByteArray, crop: CropRect, outputSize: Int, jpegQuality: Int = 90): ByteArray


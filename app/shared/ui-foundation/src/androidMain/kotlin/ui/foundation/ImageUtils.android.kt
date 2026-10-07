package com.wynime.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

actual fun ImageBitmap.resize(
    width: Int,
    height: Int,
): ImageBitmap {
    return this.asAndroidBitmap().scale(width, height).asImageBitmap()
}

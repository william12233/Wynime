package com.wynime.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import com.github.panpf.sketch.Image
import com.github.panpf.sketch.asImage

actual fun ImageBitmap.resize(
    width: Int,
    height: Int,
): ImageBitmap {
    return this.asAndroidBitmap().scale(width, height).asImageBitmap()
}

internal actual fun ImageBitmap.asSketchImage(): Image = asAndroidBitmap().asImage()

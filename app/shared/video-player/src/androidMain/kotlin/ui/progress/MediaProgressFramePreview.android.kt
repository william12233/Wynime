package com.wynime.app.videoplayer.ui.progress

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.openani.mediamp.features.PreviewFrame

internal actual fun PreviewFrame.toImageBitmap(): ImageBitmap {
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

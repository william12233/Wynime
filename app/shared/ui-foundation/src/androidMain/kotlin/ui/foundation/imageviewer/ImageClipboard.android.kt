package com.wynime.app.ui.foundation.imageviewer

import android.content.ClipData
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.wynime.utils.io.absolutePath
import java.io.File

@Composable
actual fun rememberImageClipboard(): ImageClipboard? {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    return remember(context, clipboard) { AndroidImageClipboard(context.applicationContext, clipboard) }
}

private class AndroidImageClipboard(
    private val context: Context,
    private val clipboard: Clipboard,
) : ImageClipboard {
    override suspend fun copy(file: ImageViewerExportedFile) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(file.path.absolutePath),
        )
        clipboard.setClipEntry(ClipEntry(ClipData.newUri(context.contentResolver, file.fileName, uri)))
    }
}

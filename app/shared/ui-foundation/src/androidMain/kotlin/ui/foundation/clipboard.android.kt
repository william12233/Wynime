package com.wynime.app.ui.foundation

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

actual fun textClipEntryOf(text: String): ClipEntry {
    return ClipEntry(ClipData(text, arrayOf("text/plain"), ClipData.Item(text)))
}

actual suspend fun Clipboard.getClipEntryText(): String? {
    return getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
}

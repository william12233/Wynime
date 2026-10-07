package com.wynime.app.ui.foundation

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import kotlinx.coroutines.Dispatchers
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.runInterruptible
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun textClipEntryOf(text: String): ClipEntry {
    return ClipEntry(StringSelection(text))
}

actual suspend fun Clipboard.getClipEntryText(): String? {
    val transferable = getClipEntry()?.asAwtTransferable ?: return null
    return runInterruptible(Dispatchers.IO_) {
        runCatching {
            transferable.getTransferData(DataFlavor.stringFlavor)?.toString()
        }.getOrNull()
    }
}

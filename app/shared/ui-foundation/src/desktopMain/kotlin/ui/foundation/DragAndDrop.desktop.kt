package com.wynime.app.ui.foundation

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import kotlinx.io.files.Path
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.io.IOException

@Suppress("UNCHECKED_CAST")
actual fun processDragAndDropEventImpl(event: DragAndDropEvent): DragAndDropContent {
    val transferable = event.awtTransferable

    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        try {
            val data = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            return DragAndDropContent.FileList(data.map { Path(it.absolutePath) })
        } catch (_: IOException) {

        }
    }

    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        try {
            val data = transferable.getTransferData(DataFlavor.stringFlavor) as String
            return DragAndDropContent.PlainText(data)
        } catch (_: IOException) {

        }
    }

    return DragAndDropContent.Unsupported
}
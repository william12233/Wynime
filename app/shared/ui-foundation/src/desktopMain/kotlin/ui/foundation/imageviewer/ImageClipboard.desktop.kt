package com.wynime.app.ui.foundation.imageviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.Dispatchers
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.runInterruptible
import com.wynime.utils.io.absolutePath
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.imageio.ImageIO

@Composable
actual fun rememberImageClipboard(): ImageClipboard? {
    val clipboard = LocalClipboard.current
    return remember(clipboard) { AwtImageClipboard(clipboard) }
}

private class AwtImageClipboard(private val clipboard: Clipboard) : ImageClipboard {
    override suspend fun copy(file: ImageViewerExportedFile) {
        val awtFile = File(file.path.absolutePath)

        val image = runInterruptible(Dispatchers.IO_) {
            runCatching { ImageIO.read(awtFile) }.getOrNull()
        }
        clipboard.setClipEntry(ClipEntry(ImageFileTransferable(image, awtFile)))
    }
}

private class ImageFileTransferable(
    private val image: Image?,
    private val file: File,
) : Transferable {
    private val flavors = listOfNotNull(
        image?.let { DataFlavor.imageFlavor },
        DataFlavor.javaFileListFlavor,
    ).toTypedArray()

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor == DataFlavor.imageFlavor && image != null -> image
        flavor == DataFlavor.javaFileListFlavor -> listOf(file)
        else -> throw UnsupportedFlavorException(flavor)
    }
}

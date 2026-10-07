package com.wynime.app.ui.foundation.imageviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.io.readBytes

fun interface ImageFileSaver {

    suspend fun save(file: ImageViewerExportedFile): Boolean
}

class FileKitImageFileSaver(
    private val dialogSettings: FileKitDialogSettings = FileKitDialogSettings.createDefault(),
) : ImageFileSaver {
    override suspend fun save(file: ImageViewerExportedFile): Boolean {
        val target = FileKit.openFileSaver(
            suggestedName = file.baseName,
            extension = file.extension,
            dialogSettings = dialogSettings,
        ) ?: return false
        withContext(Dispatchers.IO_) {
            target.write(file.path.readBytes())
        }
        return true
    }
}

@Composable
fun rememberFileKitImageFileSaver(): ImageFileSaver = remember { FileKitImageFileSaver() }

package com.wynime.app.ui.update.devbuild

import com.wynime.utils.io.SystemPath

internal expect suspend fun extractZipEntryByExtension(
    archive: SystemPath,
    extension: String,
    target: SystemPath,
): Boolean

internal expect suspend fun markExecutable(file: SystemPath)

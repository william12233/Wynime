package com.wynime.app.platform.features

import kotlinx.io.files.Path
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem

interface FileRevealer {
    suspend fun revealFile(file: SystemPath): Boolean

    suspend fun revealFile(file: Path): Boolean = revealFile(file.inSystem)
}
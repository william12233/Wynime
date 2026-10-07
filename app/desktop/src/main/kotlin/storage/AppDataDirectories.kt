package com.wynime.app.desktop.storage

import java.nio.file.Path

data class AppDataDirectories(
    val data: Path,
    val cache: Path
)

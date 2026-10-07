package com.wynime.app.shared

suspend fun loadOpenSourceLibrariesJsons(): List<ByteArray> = listOf(
    Res.readBytes("files/aboutlibraries.json"),
    Res.readBytes("files/additional_libraries.json"),
)

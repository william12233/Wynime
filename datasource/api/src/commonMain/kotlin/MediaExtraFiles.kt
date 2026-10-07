package com.wynime.datasources.api

import kotlinx.serialization.Serializable

@Serializable
class MediaExtraFiles(
    val subtitles: List<Subtitle> = emptyList(),
) {
    companion object {
        val EMPTY = MediaExtraFiles()
    }
}

@Serializable
data class Subtitle(

    val uri: String,

    val mimeType: String? = null,

    val language: String? = null,

    val label: String? = null,
)

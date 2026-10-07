package com.wynime.datasources.api.topic

import kotlinx.serialization.Serializable

@Serializable
enum class MediaOrigin(
    val id: String,
    vararg val otherNames: String,
) {
    BDRip("BDRip"),
    BluRay("Blu-Ray", "BluRay"),
    WebRip("WebRip"),
    Baha("Baha"),
    TVRip("TVRip"),
    ;

    companion object {
        private val values by lazy { values() }
        fun tryParse(text: String): MediaOrigin? {
            for (value in values) {
                if (text.contains(value.id, ignoreCase = true)
                    || value.otherNames.any { text.contains(it, ignoreCase = true) }
                ) {
                    return value
                }
            }
            return null
        }
    }
}
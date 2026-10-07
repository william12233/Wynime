package com.wynime.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class MediaCacheSettings(
    val enabled: Boolean = false,
    val maxCountPerSubject: Int = 1,

    val mostRecentOnly: Boolean = false,
    val mostRecentCount: Int = 8,

    val saveDir: String? = null,

    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        val Default = MediaCacheSettings()
    }
}


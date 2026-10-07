package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import com.wynime.app.data.models.preference.MediaPreference.Companion.Empty
import com.wynime.datasources.api.topic.Resolution
import com.wynime.datasources.api.topic.SubtitleLanguage
import com.wynime.utils.platform.annotations.SerializationOnly
import com.wynime.utils.platform.annotations.TestOnly

@Immutable
@Serializable
data class MediaPreference
@SerializationOnly
constructor(

    val alliance: String? = null,

    val alliancePatterns: List<String>? = null,

    val resolution: String? = null,
    val fallbackResolutions: List<String>? = listOf(
        Resolution.R2160P,
        Resolution.R1440P,
        Resolution.R1080P,
        Resolution.R720P,
    ).map { it.id },

    val subtitleLanguageId: String? = null,

    val fallbackSubtitleLanguageIds: List<String>? = listOf(
        SubtitleLanguage.ChineseSimplified,
        SubtitleLanguage.ChineseTraditional,
    ).map { it.id },

    val showWithoutSubtitle: Boolean = false,

    val mediaSourceId: String? = null,
    @Deprecated("Only for migration")
    val fallbackMediaSourceIds: List<String>? = null,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    @OptIn(SerializationOnly::class)
    companion object {

        val PlatformDefault = MediaPreference()

        val Empty = MediaPreference(
            mediaSourceId = null,
            fallbackSubtitleLanguageIds = null,
            fallbackResolutions = null,
        )

        @TestOnly
        val Any = MediaPreference.Empty.copy(
            alliance = ANY_FILTER,
            resolution = ANY_FILTER,
            subtitleLanguageId = ANY_FILTER,
            mediaSourceId = ANY_FILTER,
            showWithoutSubtitle = true,
        )

        const val ANY_FILTER = "*"
    }

    fun merge(other: MediaPreference): MediaPreference {
        if (other == Empty) return this
        if (this == Empty) return other
        @OptIn(SerializationOnly::class)
        return MediaPreference(
            alliance = other.alliance ?: alliance,
            alliancePatterns = other.alliancePatterns ?: alliancePatterns,
            resolution = other.resolution ?: resolution,
            subtitleLanguageId = other.subtitleLanguageId ?: subtitleLanguageId,
            fallbackSubtitleLanguageIds = other.fallbackSubtitleLanguageIds ?: fallbackSubtitleLanguageIds,
            mediaSourceId = other.mediaSourceId ?: mediaSourceId,
            fallbackResolutions = other.fallbackResolutions ?: fallbackResolutions,
        )
    }
}

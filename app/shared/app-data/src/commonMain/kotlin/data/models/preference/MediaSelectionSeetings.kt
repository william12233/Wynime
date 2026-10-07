package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.datasources.api.source.MediaSourceKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Serializable
@Immutable
data class MediaSelectorSettings
@Deprecated("Use Default instead", level = DeprecationLevel.ERROR)
constructor(

    val showDisabled: Boolean = true,

    val autoEnableLastSelected: Boolean = true,

    val preferKind: MediaSourceKind? = null,

    val fastSelectWebKind: Boolean = true,

    val fastSelectWebLowTierToleranceDuration: Duration = 5.seconds,

    val enableImageCaptchaAutoSolve: Boolean = true,

    val webSearchCacheTtl: Duration = 6.hours,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {

        @Stable
        @Suppress("DEPRECATION_ERROR")
        val Default = MediaSelectorSettings(
            preferKind = MediaSourceKind.WEB,
        )

        @Stable
        @Suppress("DEPRECATION_ERROR")
        val AllVisible = Default
    }
}

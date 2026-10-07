package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.app.data.network.protocol.ReleaseClass
import com.wynime.utils.platform.currentPlatform

@Immutable
@Serializable
data class UpdateSettings(
    val autoCheckUpdate: Boolean = true,
    val releaseClass: ReleaseClass = ReleaseClass.STABLE,
    val autoDownloadUpdate: Boolean = false,

    val inAppDownload: Boolean = true,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {

    companion object {
        @Stable
        val Default = UpdateSettings()
    }
}
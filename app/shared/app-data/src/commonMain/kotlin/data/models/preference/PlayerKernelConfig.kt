package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
data class PlayerKernelConfig(

    val mpvOptions: List<String> = emptyList(),

    val exoPlayerInitEffectGraphInAdvance: Boolean = true,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = PlayerKernelConfig()
    }
}

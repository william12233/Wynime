package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class OneshotActionConfig(

    val deleteSearchTagTip: Boolean = true,
    val horizontalScrollTip: Boolean = true,

    val metadataMigratedFor408: Boolean = false,

    val metadataMigratedFor411: Boolean = false,

    val needReLoginAfter500: Boolean = true,
) {
    companion object {
        @Stable
        val Default = OneshotActionConfig()
    }
}
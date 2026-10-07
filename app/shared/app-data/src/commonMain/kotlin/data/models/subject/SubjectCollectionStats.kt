package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Serializable
@Immutable
data class SubjectCollectionStats(
    val wish: Int,
    val doing: Int,
    val done: Int,
    val onHold: Int,
    val dropped: Int,
) {
    val collect get() = wish + doing + done + onHold + dropped

    companion object {
        @Stable
        val Zero = SubjectCollectionStats(0, 0, 0, 0, 0)
    }
}
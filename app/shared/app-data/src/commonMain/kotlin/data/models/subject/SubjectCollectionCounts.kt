package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Immutable
data class SubjectCollectionCounts(
    val wish: Int,
    val doing: Int,
    val done: Int,
    val onHold: Int,
    val dropped: Int,
    val total: Int,
) {
    @Stable
    fun getCount(type: UnifiedCollectionType): Int {
        return when (type) {
            UnifiedCollectionType.WISH -> wish
            UnifiedCollectionType.DOING -> doing
            UnifiedCollectionType.DONE -> done
            UnifiedCollectionType.ON_HOLD -> onHold
            UnifiedCollectionType.DROPPED -> dropped
            UnifiedCollectionType.NOT_COLLECTED -> throw IllegalArgumentException("NOT_COLLECTED is not a valid collection type")
        }
    }
}

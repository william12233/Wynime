package com.wynime.datasources.api.topic

import kotlinx.serialization.Serializable
import com.wynime.datasources.api.topic.UnifiedCollectionType.NOT_COLLECTED

@Serializable
enum class UnifiedCollectionType {
    WISH,
    DOING,
    DONE,
    ON_HOLD,
    DROPPED,

    NOT_COLLECTED,
}

fun UnifiedCollectionType.isDoneOrDropped(): Boolean {
    return this == UnifiedCollectionType.DONE || this == UnifiedCollectionType.DROPPED
}

fun UnifiedCollectionType.toggleCollected(): UnifiedCollectionType {
    return if (this.isDoneOrDropped()) {
        UnifiedCollectionType.WISH
    } else {
        UnifiedCollectionType.DONE
    }
}

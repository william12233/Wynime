package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import com.wynime.app.data.persistent.database.ProtoConverters
import com.wynime.utils.platform.annotations.TestOnly

@Serializable
@Immutable
data class SelfRatingInfo(

    val score: Int,

    val comment: String?,
    @field:TypeConverters(ProtoConverters.StringList::class)
    val tags: List<String>,
    val isPrivate: Boolean,
) {
    companion object {
        @Stable
        val Empty = SelfRatingInfo(0, null, emptyList(), false)
    }
}

@TestOnly
val TestSelfRatingInfo
    get() = SelfRatingInfo(
        score = 7,
        comment = "test",
        tags = listOf("My tag"),
        isPrivate = false,
    )

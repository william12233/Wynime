@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.EpisodeCommentDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class EpisodeCommentsResponseDto (

    @SerialName(value = "total") @Required val total: kotlin.Long,

    @SerialName(value = "items") @Required val items: kotlin.collections.List<EpisodeCommentDto>,

    @SerialName(value = "bangumiUnavailable") @Required val bangumiUnavailable: kotlin.Boolean,

    @SerialName(value = "nextCursor") val nextCursor: kotlin.String? = null

) {

}


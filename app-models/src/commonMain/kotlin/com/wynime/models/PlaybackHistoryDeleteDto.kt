@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable
@SerialName(value = "DELETE")

data class PlaybackHistoryDeleteDto (

    @SerialName(value = "episodeId") @Required val episodeId: kotlin.Long,

    @SerialName(value = "deletedAt") @Required val deletedAt: kotlin.String

) : PlaybackHistoryOpDto {

}


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
@SerialName(value = "UPSERT")

data class PlaybackHistoryUpsertDto (

    @SerialName(value = "episodeId") @Required val episodeId: kotlin.Long,

    @SerialName(value = "subjectId") @Required val subjectId: kotlin.Long,

    @SerialName(value = "positionMillis") @Required val positionMillis: kotlin.Long,

    @SerialName(value = "durationMillis") @Required val durationMillis: kotlin.Long,

    @SerialName(value = "updatedAt") @Required val updatedAt: kotlin.String,

    @SerialName(value = "episodeSort") val episodeSort: kotlin.Float? = null,

    @SerialName(value = "subjectName") val subjectName: kotlin.String? = null,

    @SerialName(value = "subjectImageUrl") val subjectImageUrl: kotlin.String? = null,

    @SerialName(value = "episodeName") val episodeName: kotlin.String? = null

) : PlaybackHistoryOpDto {

}


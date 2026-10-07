@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.PlaybackHistoryOpDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class PlaybackHistorySyncRequestDto (

    @SerialName(value = "ops") val ops: kotlin.collections.List<PlaybackHistoryOpDto>? = null,

    @SerialName(value = "lastSyncAt") val lastSyncAt: kotlin.String? = null

) {

}


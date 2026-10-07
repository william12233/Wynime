@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.BangumiFullSyncStateDto
import com.wynime.models.BangumiSyncErrorDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiSyncStateEntityDto (

    @SerialName(value = "state") val state: BangumiFullSyncStateDto? = null,

    @SerialName(value = "value") val `value`: kotlin.Int? = null,

    @SerialName(value = "error") val error: BangumiSyncErrorDto? = null

) {

}


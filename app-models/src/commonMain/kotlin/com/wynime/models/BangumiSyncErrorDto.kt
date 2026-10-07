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

data class BangumiSyncErrorDto (

    @SerialName(value = "stageName") @Required val stageName: kotlin.String,

    @SerialName(value = "extraInfo") @Required val extraInfo: kotlin.String,

    @SerialName(value = "exception") val exception: kotlin.String? = null

) {

}


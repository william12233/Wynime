@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectAirtime (

    @SerialName(value = "date") @Required val date: kotlin.String,

    @SerialName(value = "month") @Required val month: kotlin.Int,

    @SerialName(value = "weekday") @Required val weekday: kotlin.Int,

    @SerialName(value = "year") @Required val year: kotlin.Int

) {

}


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

data class AnimeRecurrenceDto (

    @SerialName(value = "startTime") @Required val startTime: kotlin.String,

    @SerialName(value = "intervalMillis") @Required val intervalMillis: kotlin.Long

) {

}


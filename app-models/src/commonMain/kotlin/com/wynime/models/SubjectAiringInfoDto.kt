@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.AnimeRecurrenceDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class SubjectAiringInfoDto (

    @SerialName(value = "begin") val begin: kotlin.String? = null,

    @SerialName(value = "recurrence") val recurrence: AnimeRecurrenceDto? = null,

    @SerialName(value = "end") val end: kotlin.String? = null

) {

}


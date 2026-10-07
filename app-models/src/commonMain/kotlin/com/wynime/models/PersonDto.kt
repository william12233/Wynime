@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.InfoboxDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class PersonDto (

    @SerialName(value = "id") @Required val id: kotlin.Long,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCn") @Required val nameCn: kotlin.String,

    @SerialName(value = "type") @Required val type: kotlin.Int,

    @SerialName(value = "imageLarge") @Required val imageLarge: kotlin.String,

    @SerialName(value = "imageMedium") @Required val imageMedium: kotlin.String,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "infobox") val infobox: InfoboxDto? = null

) {

}


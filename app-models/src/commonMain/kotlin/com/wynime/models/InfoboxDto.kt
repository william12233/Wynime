@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.InfoboxItemDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class InfoboxDto (

    @SerialName(value = "template") @Required val template: kotlin.String,

    @SerialName(value = "fields") @Required val fields: kotlin.collections.List<InfoboxItemDto>

) {

}


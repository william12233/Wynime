@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.InfoboxValueDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class InfoboxItemDto (

    @SerialName(value = "key") @Required val key: kotlin.String,

    @SerialName(value = "values") @Required val propertyValues: kotlin.collections.List<InfoboxValueDto>

) {

}


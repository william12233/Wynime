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

data class TagDto (

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "count") @Required val count: kotlin.Int

) {

}


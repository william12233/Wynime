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

data class TmdbImageDto (

    @SerialName(value = "medium") @Required val medium: kotlin.String,

    @SerialName(value = "large") @Required val large: kotlin.String,

    @SerialName(value = "vector") val vector: kotlin.String? = null

) {

}


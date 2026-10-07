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

data class SelfRatingInfoDto (

    @SerialName(value = "score") @Required val score: kotlin.Int,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "isPrivate") @Required val isPrivate: kotlin.Boolean,

    @SerialName(value = "comment") val comment: kotlin.String? = null

) {

}


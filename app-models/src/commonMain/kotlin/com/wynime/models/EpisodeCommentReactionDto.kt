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

data class EpisodeCommentReactionDto (

    @SerialName(value = "value") @Required val `value`: kotlin.String,

    @SerialName(value = "count") @Required val count: kotlin.Int,

    @SerialName(value = "selected") @Required val selected: kotlin.Boolean

) {

}


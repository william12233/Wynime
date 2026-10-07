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

data class FavouriteDto (

    @SerialName(value = "wish") @Required val wish: kotlin.Int,

    @SerialName(value = "done") @Required val done: kotlin.Int,

    @SerialName(value = "doing") @Required val doing: kotlin.Int,

    @SerialName(value = "onHold") @Required val onHold: kotlin.Int,

    @SerialName(value = "dropped") @Required val dropped: kotlin.Int

) {

}


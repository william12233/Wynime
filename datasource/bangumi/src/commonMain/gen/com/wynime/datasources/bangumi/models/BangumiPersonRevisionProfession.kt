@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiPersonRevisionProfession (

    @SerialName(value = "producer") val producer: kotlin.String? = null,

    @SerialName(value = "mangaka") val mangaka: kotlin.String? = null,

    @SerialName(value = "artist") val artist: kotlin.String? = null,

    @SerialName(value = "seiyu") val seiyu: kotlin.String? = null,

    @SerialName(value = "writer") val writer: kotlin.String? = null,

    @SerialName(value = "illustrator") val illustrator: kotlin.String? = null,

    @SerialName(value = "actor") val actor: kotlin.String? = null

) {

}


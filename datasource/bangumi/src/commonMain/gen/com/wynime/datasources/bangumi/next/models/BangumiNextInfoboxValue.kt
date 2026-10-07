@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextInfoboxValue (

    @SerialName(value = "v") @Required val v: kotlin.String,

    @SerialName(value = "k") val k: kotlin.String? = null

) {

}


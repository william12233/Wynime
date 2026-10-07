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

data class BangumiAvatar (

    @SerialName(value = "large") @Required val large: kotlin.String,

    @SerialName(value = "medium") @Required val medium: kotlin.String,

    @SerialName(value = "small") @Required val small: kotlin.String

) {

}


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

data class BangumiNextSubjectImages (

    @SerialName(value = "common") @Required val common: kotlin.String,

    @SerialName(value = "grid") @Required val grid: kotlin.String,

    @SerialName(value = "large") @Required val large: kotlin.String,

    @SerialName(value = "medium") @Required val medium: kotlin.String,

    @SerialName(value = "small") @Required val small: kotlin.String

) {

}


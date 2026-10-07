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

data class BangumiStat (

    @SerialName(value = "comments") @Required val comments: kotlin.Int,

    @SerialName(value = "collects") @Required val collects: kotlin.Int

) {

}


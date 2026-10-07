@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiCount

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiRating (

    @SerialName(value = "rank") @Required val rank: kotlin.Int,

    @SerialName(value = "total") @Required val total: kotlin.Int,

    @SerialName(value = "count") @Required val count: BangumiCount,

    @SerialName(value = "score") @Required val score: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum

) {

}


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

data class BangumiNextSubjectRating (

    @SerialName(value = "count") @Required val count: kotlin.collections.List<kotlin.Int>,

    @SerialName(value = "rank") @Required val rank: kotlin.Int,

    @SerialName(value = "score") @Required val score: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum,

    @SerialName(value = "total") @Required val total: kotlin.Int

) {

}


@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextTrendingSubject

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextGetTrendingSubjects200Response (

    @SerialName(value = "data") @Required val `data`: kotlin.collections.List<BangumiNextTrendingSubject>,

    @SerialName(value = "total") @Required val total: kotlin.Int

) {

}


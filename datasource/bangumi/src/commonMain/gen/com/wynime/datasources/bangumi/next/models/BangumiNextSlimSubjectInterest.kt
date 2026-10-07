@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextCollectionType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSlimSubjectInterest (

    @SerialName(value = "comment") @Required val comment: kotlin.String,

    @SerialName(value = "rate") @Required val rate: kotlin.Int,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "type") @Required val type: BangumiNextCollectionType,

    @SerialName(value = "updatedAt") @Required val updatedAt: kotlin.Int

) {

}


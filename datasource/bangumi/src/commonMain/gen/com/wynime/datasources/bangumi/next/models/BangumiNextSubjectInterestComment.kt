@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextCollectionType
import com.wynime.datasources.bangumi.next.models.BangumiNextReaction
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimUser

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectInterestComment (

    @SerialName(value = "comment") @Required val comment: kotlin.String,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "rate") @Required val rate: kotlin.Int,

    @SerialName(value = "type") @Required val type: BangumiNextCollectionType,

    @SerialName(value = "updatedAt") @Required val updatedAt: kotlin.Int,

    @SerialName(value = "user") @Required val user: BangumiNextSlimUser,

    @SerialName(value = "reactions") val reactions: kotlin.collections.List<BangumiNextReaction>? = null

) {

}


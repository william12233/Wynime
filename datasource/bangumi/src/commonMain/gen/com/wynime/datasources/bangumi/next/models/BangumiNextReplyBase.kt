@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextReaction
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimUser

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextReplyBase (

    @SerialName(value = "content") @Required val content: kotlin.String,

    @SerialName(value = "createdAt") @Required val createdAt: kotlin.Int,

    @SerialName(value = "creatorID") @Required val creatorID: kotlin.Int,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "state") @Required val state: kotlin.Int,

    @SerialName(value = "creator") val creator: BangumiNextSlimUser? = null,

    @SerialName(value = "reactions") val reactions: kotlin.collections.List<BangumiNextReaction>? = null

) {

}


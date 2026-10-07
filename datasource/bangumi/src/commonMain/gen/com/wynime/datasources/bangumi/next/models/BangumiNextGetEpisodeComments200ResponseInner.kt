@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextCommentBase
import com.wynime.datasources.bangumi.next.models.BangumiNextReaction
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimUser

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextGetEpisodeComments200ResponseInner (

    @SerialName(value = "content") @Required val content: kotlin.String,

    @SerialName(value = "createdAt") @Required val createdAt: kotlin.Int,

    @SerialName(value = "creatorID") @Required val creatorID: kotlin.Int,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "mainID") @Required val mainID: kotlin.Int,

    @SerialName(value = "relatedID") @Required val relatedID: kotlin.Int,

    @SerialName(value = "state") @Required val state: kotlin.Int,

    @SerialName(value = "replies") @Required val replies: kotlin.collections.List<BangumiNextCommentBase>,

    @SerialName(value = "reactions") val reactions: kotlin.collections.List<BangumiNextReaction>? = null,

    @SerialName(value = "user") val user: BangumiNextSlimUser? = null

) {

}


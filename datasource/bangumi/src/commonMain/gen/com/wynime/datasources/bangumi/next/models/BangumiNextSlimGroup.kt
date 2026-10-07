@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextAvatar

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSlimGroup (

    @SerialName(value = "accessible") @Required val accessible: kotlin.Boolean,

    @SerialName(value = "createdAt") @Required val createdAt: kotlin.Int,

    @SerialName(value = "creatorID") @Required val creatorID: kotlin.Int,

    @SerialName(value = "icon") @Required val icon: BangumiNextAvatar,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "members") @Required val members: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "title") @Required val title: kotlin.String

) {

}


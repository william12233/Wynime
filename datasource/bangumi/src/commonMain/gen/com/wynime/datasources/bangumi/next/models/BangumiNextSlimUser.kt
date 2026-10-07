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

data class BangumiNextSlimUser (

    @SerialName(value = "avatar") @Required val avatar: BangumiNextAvatar,

    @SerialName(value = "group") @Required val group: kotlin.Int,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "joinedAt") @Required val joinedAt: kotlin.Int,

    @SerialName(value = "nickname") @Required val nickname: kotlin.String,

    @SerialName(value = "sign") @Required val sign: kotlin.String,

    @SerialName(value = "username") @Required val username: kotlin.String

) {

}


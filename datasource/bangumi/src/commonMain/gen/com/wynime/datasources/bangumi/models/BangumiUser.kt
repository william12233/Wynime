@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiAvatar
import com.wynime.datasources.bangumi.models.BangumiUserGroup

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiUser (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "username") @Required val username: kotlin.String,

    @SerialName(value = "nickname") @Required val nickname: kotlin.String,

    @SerialName(value = "user_group") @Required val userGroup: BangumiUserGroup,

    @SerialName(value = "avatar") @Required val avatar: BangumiAvatar,

    @SerialName(value = "sign") @Required val sign: kotlin.String

) {

}


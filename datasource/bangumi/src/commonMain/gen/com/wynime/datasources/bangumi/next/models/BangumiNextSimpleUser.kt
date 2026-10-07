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

data class BangumiNextSimpleUser (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "nickname") @Required val nickname: kotlin.String,

    @SerialName(value = "username") @Required val username: kotlin.String

) {

}


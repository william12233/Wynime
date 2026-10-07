@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiCreator (

    @SerialName(value = "username") @Required val username: kotlin.String,

    @SerialName(value = "nickname") @Required val nickname: kotlin.String

) {

}


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

data class BangumiNextSlimIndex (

    @SerialName(value = "createdAt") @Required val createdAt: kotlin.Int,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "title") @Required val title: kotlin.String,

    @SerialName(value = "total") @Required val total: kotlin.Int,

    @SerialName(value = "type") @Required val type: kotlin.Int

) {

}


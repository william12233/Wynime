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

data class BangumiPage (

    @SerialName(value = "total") @Required val total: kotlin.Int,

    @SerialName(value = "limit") @Required val limit: kotlin.Int,

    @SerialName(value = "offset") @Required val offset: kotlin.Int

) {

}


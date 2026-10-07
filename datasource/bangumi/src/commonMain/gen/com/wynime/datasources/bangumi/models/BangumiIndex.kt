@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiCreator
import com.wynime.datasources.bangumi.models.BangumiStat

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiIndex (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "title") @Required val title: kotlin.String,

    @SerialName(value = "desc") @Required val desc: kotlin.String,

    @SerialName(value = "stat") @Required val stat: BangumiStat,

    @SerialName(value = "created_at") @Required val createdAt: kotlinx.datetime.Instant,

    @SerialName(value = "updated_at") @Required val updatedAt: kotlinx.datetime.Instant,

    @SerialName(value = "creator") @Required val creator: BangumiCreator,

    @Deprecated(message = "This property is deprecated.")
    @SerialName(value = "ban") @Required val ban: kotlin.Boolean,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "total") val total: kotlin.Int? = 0

) {

}


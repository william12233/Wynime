@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiCharacterRevisionDataItem
import com.wynime.datasources.bangumi.models.BangumiCreator

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiCharacterRevision (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "type") @Required val type: kotlin.Int,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "created_at") @Required val createdAt: kotlinx.datetime.Instant,

    @SerialName(value = "creator") val creator: BangumiCreator? = null,

    @SerialName(value = "data") val `data`: kotlin.collections.Map<kotlin.String, BangumiCharacterRevisionDataItem>? = null

) {

}


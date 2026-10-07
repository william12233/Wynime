@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiSubjectType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiSearchSubjectsRequestFilter (

    @SerialName(value = "type") val type: kotlin.collections.List<BangumiSubjectType>? = null,

    @SerialName(value = "tag") val tag: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "air_date") val airDate: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "rating") val rating: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "rank") val rank: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "nsfw") val nsfw: kotlin.Boolean? = null

) {

}


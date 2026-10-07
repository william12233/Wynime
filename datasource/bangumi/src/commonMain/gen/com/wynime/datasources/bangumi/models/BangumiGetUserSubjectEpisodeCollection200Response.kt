@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiUserEpisodeCollection

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiGetUserSubjectEpisodeCollection200Response (

    @SerialName(value = "total") @Required val total: kotlin.Int,

    @SerialName(value = "limit") @Required val limit: kotlin.Int,

    @SerialName(value = "offset") @Required val offset: kotlin.Int,

    @SerialName(value = "data") val `data`: kotlin.collections.List<BangumiUserEpisodeCollection>? = null

) {

}


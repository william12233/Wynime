@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiIndexSubject

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiPagedIndexSubject (

    @SerialName(value = "total") val total: kotlin.Int? = 0,

    @SerialName(value = "limit") val limit: kotlin.Int? = 0,

    @SerialName(value = "offset") val offset: kotlin.Int? = 0,

    @SerialName(value = "data") val `data`: kotlin.collections.List<BangumiIndexSubject>? = arrayListOf()

) {

}


@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiSearchSubjects200ResponseDataInner

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiSearchSubjects200Response (

    @SerialName(value = "total") val total: kotlin.Int? = null,

    @SerialName(value = "limit") val limit: kotlin.Int? = null,

    @SerialName(value = "offset") val offset: kotlin.Int? = null,

    @SerialName(value = "data") val `data`: kotlin.collections.List<BangumiSearchSubjects200ResponseDataInner>? = null

) {

}


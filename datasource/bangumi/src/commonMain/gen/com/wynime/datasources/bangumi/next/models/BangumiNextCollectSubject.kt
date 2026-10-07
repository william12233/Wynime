@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextCollectionType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextCollectSubject (

    @SerialName(value = "comment") val comment: kotlin.String? = null,

    @SerialName(value = "private") val `private`: kotlin.Boolean? = null,

    @SerialName(value = "rate") val rate: kotlin.Int? = null,

    @SerialName(value = "tags") val tags: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "type") val type: BangumiNextCollectionType? = null

) {

}


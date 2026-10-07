@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiSubjectCollectionType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiUserSubjectCollectionModifyPayload (

    @SerialName(value = "type") val type: BangumiSubjectCollectionType? = null,

    @SerialName(value = "rate") val rate: kotlin.Int? = null,

    @SerialName(value = "ep_status") val epStatus: kotlin.Int? = null,

    @SerialName(value = "vol_status") val volStatus: kotlin.Int? = null,

    @SerialName(value = "comment") val comment: kotlin.String? = null,

    @SerialName(value = "private") val `private`: kotlin.Boolean? = null,

    @SerialName(value = "tags") val tags: kotlin.collections.List<kotlin.String>? = null

) {

}


@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiImages
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiTag

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiSlimSubject (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "type") @Required val type: BangumiSubjectType,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "name_cn") @Required val nameCn: kotlin.String,

    @SerialName(value = "short_summary") @Required val shortSummary: kotlin.String,

    @SerialName(value = "images") @Required val images: BangumiImages,

    @SerialName(value = "volumes") @Required val volumes: kotlin.Int,

    @SerialName(value = "eps") @Required val eps: kotlin.Int,

    @SerialName(value = "collection_total") @Required val collectionTotal: kotlin.Int,

    @SerialName(value = "score") @Required val score: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<BangumiTag>,

    @SerialName(value = "date") val date: kotlin.String? = null

) {

}


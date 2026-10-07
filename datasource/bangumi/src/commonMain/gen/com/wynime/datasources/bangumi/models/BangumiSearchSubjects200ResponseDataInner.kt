@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiTag

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiSearchSubjects200ResponseDataInner (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "date") @Required val date: kotlin.String?,

    @SerialName(value = "image") @Required val image: kotlin.String,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "name_cn") @Required val nameCn: kotlin.String,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<BangumiTag>,

    @SerialName(value = "type") val type: BangumiSubjectType? = null,

    @SerialName(value = "score") val score: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum? = null,

    @SerialName(value = "rank") val rank: kotlin.Int? = null

) {

}


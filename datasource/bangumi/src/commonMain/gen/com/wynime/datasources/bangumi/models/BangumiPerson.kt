@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiPersonCareer
import com.wynime.datasources.bangumi.models.BangumiPersonImages
import com.wynime.datasources.bangumi.models.BangumiPersonType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiPerson (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "type") @Required val type: BangumiPersonType,

    @SerialName(value = "career") @Required val career: kotlin.collections.List<BangumiPersonCareer>,

    @SerialName(value = "short_summary") @Required val shortSummary: kotlin.String,

    @SerialName(value = "locked") @Required val locked: kotlin.Boolean,

    @SerialName(value = "images") val images: BangumiPersonImages? = null

) {

}


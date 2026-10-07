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

data class BangumiRelatedPerson (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "type") @Required val type: BangumiPersonType,

    @SerialName(value = "career") @Required val career: kotlin.collections.List<BangumiPersonCareer>,

    @SerialName(value = "relation") @Required val relation: kotlin.String,

    @SerialName(value = "images") val images: BangumiPersonImages? = null

) {

}


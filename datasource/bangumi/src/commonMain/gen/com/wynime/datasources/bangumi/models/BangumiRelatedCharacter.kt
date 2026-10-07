@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiCharacterType
import com.wynime.datasources.bangumi.models.BangumiPerson
import com.wynime.datasources.bangumi.models.BangumiPersonImages

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiRelatedCharacter (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "type") @Required val type: BangumiCharacterType,

    @SerialName(value = "relation") @Required val relation: kotlin.String,

    @SerialName(value = "images") val images: BangumiPersonImages? = null,

    @SerialName(value = "actors") val actors: kotlin.collections.List<BangumiPerson>? = arrayListOf()

) {

}


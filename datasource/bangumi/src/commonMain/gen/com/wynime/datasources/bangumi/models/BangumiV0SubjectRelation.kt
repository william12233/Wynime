@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiImages

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiV0SubjectRelation (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "type") @Required val type: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "name_cn") @Required val nameCn: kotlin.String,

    @SerialName(value = "relation") @Required val relation: kotlin.String,

    @SerialName(value = "images") val images: BangumiImages? = null

) {

}


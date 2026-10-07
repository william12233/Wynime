@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.CollectionTypeDto
import com.wynime.models.SubjectRelationGraphNodeRoleDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class SubjectRelationGraphNodeDto (

    @SerialName(value = "id") @Required val id: kotlin.Long,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCn") @Required val nameCn: kotlin.String,

    @SerialName(value = "imageLarge") @Required val imageLarge: kotlin.String,

    @SerialName(value = "airDate") @Required val airDate: kotlin.String,

    @SerialName(value = "episodeCount") @Required val episodeCount: kotlin.Int,

    @SerialName(value = "compilation") @Required val compilation: kotlin.Boolean,

    @SerialName(value = "role") @Required val role: SubjectRelationGraphNodeRoleDto,

    @SerialName(value = "platform") val platform: kotlin.Int? = null,

    @SerialName(value = "attachTo") val attachTo: kotlin.Long? = null,

    @SerialName(value = "relation") val relation: kotlin.Int? = null,

    @SerialName(value = "collectionType") val collectionType: CollectionTypeDto? = null

) {

}


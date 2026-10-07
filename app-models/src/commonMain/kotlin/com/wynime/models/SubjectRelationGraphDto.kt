@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.SubjectRelationGraphEdgeDto
import com.wynime.models.SubjectRelationGraphNodeDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class SubjectRelationGraphDto (

    @SerialName(value = "subjectId") @Required val subjectId: kotlin.Long,

    @SerialName(value = "nodes") @Required val nodes: kotlin.collections.List<SubjectRelationGraphNodeDto>,

    @SerialName(value = "edges") @Required val edges: kotlin.collections.List<SubjectRelationGraphEdgeDto>,

    @SerialName(value = "mainline") @Required val mainline: kotlin.collections.List<kotlin.Long>,

    @SerialName(value = "truncated") @Required val truncated: kotlin.Boolean

) {

}


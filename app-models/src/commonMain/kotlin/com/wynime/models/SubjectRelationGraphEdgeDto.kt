@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class SubjectRelationGraphEdgeDto (

    @SerialName(value = "from") @Required val from: kotlin.Long,

    @SerialName(value = "to") @Required val to: kotlin.Long,

    @SerialName(value = "relation") @Required val relation: kotlin.Int

) {

}


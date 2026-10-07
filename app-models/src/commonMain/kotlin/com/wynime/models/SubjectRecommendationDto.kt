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

data class SubjectRecommendationDto (

    @SerialName(value = "subjectName") @Required val subjectName: kotlin.String,

    @SerialName(value = "subjectNameCn") @Required val subjectNameCn: kotlin.String,

    @SerialName(value = "imageUrl") @Required val imageUrl: kotlin.String,

    @SerialName(value = "desc1") @Required val desc1: kotlin.String,

    @SerialName(value = "desc2") @Required val desc2: kotlin.String,

    @SerialName(value = "subjectId") val subjectId: kotlin.Long? = null,

    @SerialName(value = "uri") val uri: kotlin.String? = null

) {

}


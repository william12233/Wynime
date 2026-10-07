@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.CollectionTypeDto
import com.wynime.models.SelfRatingInfoDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class UpdateSubjectCollectionRequestDto (

    @SerialName(value = "collectionType") val collectionType: CollectionTypeDto? = null,

    @SerialName(value = "selfRating") val selfRating: SelfRatingInfoDto? = null

) {

}


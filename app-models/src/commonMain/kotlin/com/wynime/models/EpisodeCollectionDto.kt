@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.EpisodeCollectionTypeDto
import com.wynime.models.EpisodeTypeDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class EpisodeCollectionDto (

    @SerialName(value = "episodeId") @Required val episodeId: kotlin.Long,

    @SerialName(value = "subjectId") @Required val subjectId: kotlin.Long,

    @SerialName(value = "sort") @Required val sort: kotlin.String,

    @SerialName(value = "type") @Required val type: EpisodeTypeDto,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCn") @Required val nameCn: kotlin.String,

    @SerialName(value = "description") @Required val description: kotlin.String,

    @SerialName(value = "ep") val ep: kotlin.String? = null,

    @SerialName(value = "airdate") val airdate: kotlin.String? = null,

    @SerialName(value = "disc") val disc: kotlin.Int? = null,

    @SerialName(value = "duration") val duration: kotlin.String? = null,

    @SerialName(value = "imageMedium") val imageMedium: kotlin.String? = null,

    @SerialName(value = "imageLarge") val imageLarge: kotlin.String? = null,

    @SerialName(value = "collectionType") val collectionType: EpisodeCollectionTypeDto? = null

) {

}


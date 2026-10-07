@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiEpisode
import com.wynime.datasources.bangumi.models.BangumiEpisodeCollectionType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiUserEpisodeCollection (

    @SerialName(value = "episode") @Required val episode: BangumiEpisode,

    @SerialName(value = "type") @Required val type: BangumiEpisodeCollectionType

) {

}


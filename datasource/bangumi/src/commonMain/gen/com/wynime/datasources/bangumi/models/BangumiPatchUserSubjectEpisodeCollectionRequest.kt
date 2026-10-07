@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiEpisodeCollectionType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiPatchUserSubjectEpisodeCollectionRequest (

    @SerialName(value = "episode_id") @Required val episodeId: kotlin.collections.List<kotlin.Int>,

    @SerialName(value = "type") @Required val type: BangumiEpisodeCollectionType

) {

}


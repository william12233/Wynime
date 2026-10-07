@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiCollection
import com.wynime.datasources.bangumi.models.BangumiImages
import com.wynime.datasources.bangumi.models.BangumiItem
import com.wynime.datasources.bangumi.models.BangumiRating
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiTag

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiSubject (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "type") @Required val type: BangumiSubjectType,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "name_cn") @Required val nameCn: kotlin.String,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "locked") @Required val locked: kotlin.Boolean,

    @SerialName(value = "platform") @Required val platform: kotlin.String,

    @SerialName(value = "images") @Required val images: BangumiImages,

    @SerialName(value = "volumes") @Required val volumes: kotlin.Int,

    @SerialName(value = "eps") @Required val eps: kotlin.Int,

    @SerialName(value = "total_episodes") @Required val totalEpisodes: kotlin.Int,

    @SerialName(value = "rating") @Required val rating: BangumiRating,

    @SerialName(value = "collection") @Required val collection: BangumiCollection,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<BangumiTag>,

    @SerialName(value = "date") val date: kotlin.String? = null,

    @SerialName(value = "infobox") val infobox: kotlin.collections.List<BangumiItem>? = null

) {

}


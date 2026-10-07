@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiImages
import com.wynime.datasources.bangumi.models.BangumiItem

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiIndexSubject (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "type") @Required val type: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "comment") @Required val comment: kotlin.String,

    @SerialName(value = "added_at") @Required val addedAt: kotlinx.datetime.Instant,

    @SerialName(value = "images") val images: BangumiImages? = null,

    @SerialName(value = "infobox") val infobox: kotlin.collections.List<BangumiItem>? = null,

    @SerialName(value = "date") val date: kotlin.String? = null

) {

}


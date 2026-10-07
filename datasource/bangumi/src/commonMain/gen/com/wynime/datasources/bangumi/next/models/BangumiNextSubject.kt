@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextInfoboxItem
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectAirtime
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectImages
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectInterest
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectPlatform
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectRating
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectTag
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubject (

    @SerialName(value = "airtime") @Required val airtime: BangumiNextSubjectAirtime,

    @SerialName(value = "collection") @Required val collection: kotlin.collections.Map<kotlin.String, kotlin.Int>,

    @SerialName(value = "eps") @Required val eps: kotlin.Int,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "info") @Required val info: kotlin.String,

    @SerialName(value = "infobox") @Required val infobox: kotlin.collections.List<BangumiNextInfoboxItem>,

    @SerialName(value = "locked") @Required val locked: kotlin.Boolean,

    @SerialName(value = "metaTags") @Required val metaTags: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCN") @Required val nameCN: kotlin.String,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "platform") @Required val platform: BangumiNextSubjectPlatform,

    @SerialName(value = "rating") @Required val rating: BangumiNextSubjectRating,

    @SerialName(value = "redirect") @Required val redirect: kotlin.Int,

    @SerialName(value = "series") @Required val series: kotlin.Boolean,

    @SerialName(value = "seriesEntry") @Required val seriesEntry: kotlin.Int,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<BangumiNextSubjectTag>,

    @SerialName(value = "type") @Required val type: BangumiNextSubjectType,

    @SerialName(value = "volumes") @Required val volumes: kotlin.Int,

    @SerialName(value = "images") val images: BangumiNextSubjectImages? = null,

    @SerialName(value = "interest") val interest: BangumiNextSubjectInterest? = null

) {

}


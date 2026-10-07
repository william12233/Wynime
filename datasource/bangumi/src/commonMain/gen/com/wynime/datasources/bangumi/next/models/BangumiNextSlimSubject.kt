@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSlimSubjectInterest
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectImages
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectRating
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSlimSubject (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "info") @Required val info: kotlin.String,

    @SerialName(value = "locked") @Required val locked: kotlin.Boolean,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCN") @Required val nameCN: kotlin.String,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "rating") @Required val rating: BangumiNextSubjectRating,

    @SerialName(value = "type") @Required val type: BangumiNextSubjectType,

    @SerialName(value = "images") val images: BangumiNextSubjectImages? = null,

    @SerialName(value = "interest") val interest: BangumiNextSlimSubjectInterest? = null

) {

}


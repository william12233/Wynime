@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectType
import com.wynime.datasources.bangumi.next.models.BangumiNextWikiPlatform

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectWikiInfo (

    @SerialName(value = "availablePlatform") @Required val availablePlatform: kotlin.collections.List<BangumiNextWikiPlatform>,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "infobox") @Required val infobox: kotlin.String,

    @SerialName(value = "metaTags") @Required val metaTags: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "platform") @Required val platform: kotlin.Int,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "typeID") @Required val typeID: BangumiNextSubjectType

) {

}


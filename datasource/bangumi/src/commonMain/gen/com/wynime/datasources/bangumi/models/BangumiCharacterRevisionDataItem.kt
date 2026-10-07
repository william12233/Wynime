@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiRevisionExtra

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiCharacterRevisionDataItem (

    @SerialName(value = "infobox") @Required val infobox: kotlin.String,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "extra") @Required val extra: BangumiRevisionExtra

) {

}


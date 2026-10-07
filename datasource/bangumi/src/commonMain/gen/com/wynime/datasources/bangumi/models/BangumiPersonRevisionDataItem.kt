@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiPersonRevisionProfession
import com.wynime.datasources.bangumi.models.BangumiRevisionExtra

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiPersonRevisionDataItem (

    @SerialName(value = "prsn_infobox") @Required val prsnInfobox: kotlin.String,

    @SerialName(value = "prsn_summary") @Required val prsnSummary: kotlin.String,

    @SerialName(value = "profession") @Required val profession: BangumiPersonRevisionProfession,

    @SerialName(value = "extra") @Required val extra: BangumiRevisionExtra,

    @SerialName(value = "prsn_name") @Required val prsnName: kotlin.String

) {

}


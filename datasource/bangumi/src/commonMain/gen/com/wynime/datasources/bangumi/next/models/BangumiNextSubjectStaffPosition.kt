@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectStaffPositionType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectStaffPosition (

    @SerialName(value = "appearEps") @Required val appearEps: kotlin.String,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "type") @Required val type: BangumiNextSubjectStaffPositionType

) {

}


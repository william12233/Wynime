@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSlimPerson

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectPositionStaff (

    @SerialName(value = "appearEps") @Required val appearEps: kotlin.String,

    @SerialName(value = "person") @Required val person: BangumiNextSlimPerson,

    @SerialName(value = "summary") @Required val summary: kotlin.String

) {

}


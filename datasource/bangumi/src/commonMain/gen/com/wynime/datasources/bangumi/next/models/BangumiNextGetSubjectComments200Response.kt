@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectInterestComment

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextGetSubjectComments200Response (

    @SerialName(value = "data") @Required val `data`: kotlin.collections.List<BangumiNextSubjectInterestComment>,

    @SerialName(value = "total") @Required val total: kotlin.Int

) {

}


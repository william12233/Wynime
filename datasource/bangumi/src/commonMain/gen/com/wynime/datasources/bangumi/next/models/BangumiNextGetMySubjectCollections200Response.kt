@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSubject

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextGetMySubjectCollections200Response (

    @SerialName(value = "data") @Required val `data`: kotlin.collections.List<BangumiNextSubject>,

    @SerialName(value = "total") @Required val total: kotlin.Int

) {

}


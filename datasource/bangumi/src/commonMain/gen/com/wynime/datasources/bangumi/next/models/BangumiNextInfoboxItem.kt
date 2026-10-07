@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextInfoboxValue

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextInfoboxItem (

    @SerialName(value = "key") @Required val key: kotlin.String,

    @SerialName(value = "values") @Required val propertyValues: kotlin.collections.List<BangumiNextInfoboxValue>

) {

}


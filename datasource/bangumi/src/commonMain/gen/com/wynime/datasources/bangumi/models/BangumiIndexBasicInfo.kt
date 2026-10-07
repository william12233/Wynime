@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiIndexBasicInfo (

    @SerialName(value = "title") val title: kotlin.String? = null,

    @SerialName(value = "description") val description: kotlin.String? = null

) {

}


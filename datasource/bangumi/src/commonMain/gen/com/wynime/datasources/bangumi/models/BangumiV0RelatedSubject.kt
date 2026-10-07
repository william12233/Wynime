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

data class BangumiV0RelatedSubject (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "staff") @Required val staff: kotlin.String,

    @SerialName(value = "name_cn") @Required val nameCn: kotlin.String,

    @SerialName(value = "name") val name: kotlin.String? = null,

    @SerialName(value = "image") val image: kotlin.String? = null

) {

}


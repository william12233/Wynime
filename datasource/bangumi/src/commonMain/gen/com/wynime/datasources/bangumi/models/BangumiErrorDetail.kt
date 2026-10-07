@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiDetail

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiErrorDetail (

    @SerialName(value = "title") @Required val title: kotlin.String,

    @SerialName(value = "description") @Required val description: kotlin.String,

    @SerialName(value = "details") val details: BangumiDetail? = null

) {

}


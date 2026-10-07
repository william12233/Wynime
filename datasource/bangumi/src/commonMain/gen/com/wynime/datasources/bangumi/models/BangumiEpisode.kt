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

data class BangumiEpisode (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "type") @Required val type: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "name_cn") @Required val nameCn: kotlin.String,

    @SerialName(value = "sort") @Required val sort: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum,

    @SerialName(value = "airdate") @Required val airdate: kotlin.String,

    @SerialName(value = "comment") @Required val comment: kotlin.Int,

    @SerialName(value = "duration") @Required val duration: kotlin.String,

    @SerialName(value = "desc") @Required val desc: kotlin.String,

    @SerialName(value = "disc") @Required val disc: kotlin.Int,

    @SerialName(value = "ep") val ep: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum? = null,

    @SerialName(value = "duration_seconds") val durationSeconds: kotlin.Int? = null

) {

}


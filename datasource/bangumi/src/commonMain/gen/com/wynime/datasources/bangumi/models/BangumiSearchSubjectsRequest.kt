@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiSearchSubjectsRequestFilter

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiSearchSubjectsRequest (

    @SerialName(value = "keyword") @Required val keyword: kotlin.String,

    @SerialName(value = "sort") val sort: BangumiSearchSubjectsRequest.Sort? = Sort.MATCH,

    @SerialName(value = "filter") val filter: BangumiSearchSubjectsRequestFilter? = null

) {

    @Serializable
    enum class Sort(val value: kotlin.String) {
        @SerialName(value = "match") MATCH("match"),
        @SerialName(value = "heat") HEAT("heat"),
        @SerialName(value = "rank") RANK("rank"),
        @SerialName(value = "score") SCORE("score");
    }

}


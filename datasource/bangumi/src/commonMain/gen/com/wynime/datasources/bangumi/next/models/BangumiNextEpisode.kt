@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextEpisodeCollectionStatus
import com.wynime.datasources.bangumi.next.models.BangumiNextEpisodeType
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimSubject

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextEpisode (

    @SerialName(value = "airdate") @Required val airdate: kotlin.String,

    @SerialName(value = "comment") @Required val comment: kotlin.Int,

    @SerialName(value = "disc") @Required val disc: kotlin.Int,

    @SerialName(value = "duration") @Required val duration: kotlin.String,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCN") @Required val nameCN: kotlin.String,

    @SerialName(value = "sort") @Required val sort: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum,

    @SerialName(value = "subjectID") @Required val subjectID: kotlin.Int,

    @SerialName(value = "type") @Required val type: BangumiNextEpisodeType,

    @SerialName(value = "desc") val desc: kotlin.String? = null,

    @SerialName(value = "status") val status: BangumiNextEpisodeCollectionStatus? = null,

    @SerialName(value = "subject") val subject: BangumiNextSlimSubject? = null

) {

}


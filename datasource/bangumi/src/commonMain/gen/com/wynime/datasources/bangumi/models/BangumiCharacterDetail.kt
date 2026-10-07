@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiBloodType
import com.wynime.datasources.bangumi.models.BangumiCharacterType
import com.wynime.datasources.bangumi.models.BangumiPersonImages
import com.wynime.datasources.bangumi.models.BangumiStat

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiCharacterDetail (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "type") @Required val type: BangumiCharacterType,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "locked") @Required val locked: kotlin.Boolean,

    @SerialName(value = "stat") @Required val stat: BangumiStat,

    @SerialName(value = "images") val images: BangumiPersonImages? = null,

    @SerialName(value = "infobox") val infobox: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "gender") val gender: kotlin.String? = null,

    @SerialName(value = "blood_type") val bloodType: BangumiBloodType? = null,

    @SerialName(value = "birth_year") val birthYear: kotlin.Int? = null,

    @SerialName(value = "birth_mon") val birthMon: kotlin.Int? = null,

    @SerialName(value = "birth_day") val birthDay: kotlin.Int? = null

) {

}


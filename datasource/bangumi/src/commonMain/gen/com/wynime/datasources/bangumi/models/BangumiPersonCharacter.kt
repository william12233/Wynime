@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiCharacterType
import com.wynime.datasources.bangumi.models.BangumiPersonImages

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiPersonCharacter (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "type") @Required val type: BangumiCharacterType,

    @SerialName(value = "subject_id") @Required val subjectId: kotlin.Int,

    @SerialName(value = "subject_name") @Required val subjectName: kotlin.String,

    @SerialName(value = "subject_name_cn") @Required val subjectNameCn: kotlin.String,

    @SerialName(value = "images") val images: BangumiPersonImages? = null,

    @SerialName(value = "staff") val staff: kotlin.String? = null

) {

}


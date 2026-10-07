@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectStaffPositionType (

    @SerialName(value = "cn") @Required val cn: kotlin.String,

    @SerialName(value = "en") @Required val en: kotlin.String,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "jp") @Required val jp: kotlin.String

) {

}


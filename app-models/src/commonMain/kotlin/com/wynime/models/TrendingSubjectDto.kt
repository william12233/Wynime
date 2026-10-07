@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class TrendingSubjectDto (

    @SerialName(value = "bangumiId") @Required val bangumiId: kotlin.Int,

    @SerialName(value = "nameCn") @Required val nameCn: kotlin.String,

    @SerialName(value = "imageLarge") @Required val imageLarge: kotlin.String

) {

}


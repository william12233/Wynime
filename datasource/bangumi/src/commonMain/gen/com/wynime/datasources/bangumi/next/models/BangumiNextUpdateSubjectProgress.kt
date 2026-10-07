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

data class BangumiNextUpdateSubjectProgress (

    @SerialName(value = "epStatus") val epStatus: kotlin.Int? = null,

    @SerialName(value = "volStatus") val volStatus: kotlin.Int? = null

) {

}


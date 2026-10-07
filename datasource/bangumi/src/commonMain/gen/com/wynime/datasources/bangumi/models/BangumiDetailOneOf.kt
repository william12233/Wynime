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

data class BangumiDetailOneOf (

    @SerialName(value = "error") val error: kotlin.String? = null,

    @SerialName(value = "path") val path: kotlin.String? = null

) {

}


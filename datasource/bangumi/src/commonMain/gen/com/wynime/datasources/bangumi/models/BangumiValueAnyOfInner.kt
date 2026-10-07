@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiKV
import com.wynime.datasources.bangumi.models.BangumiV

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiValueAnyOfInner (

    @SerialName(value = "k") @Required val k: kotlin.String,

    @SerialName(value = "v") @Required val v: kotlin.String

) {

}


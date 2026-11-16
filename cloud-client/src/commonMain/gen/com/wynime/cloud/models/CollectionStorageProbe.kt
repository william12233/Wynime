@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.cloud.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class CollectionStorageProbe (

    @SerialName(value = "ok") @Required val ok: kotlin.Boolean,

    @SerialName(value = "storageLatencyMs") @Required val storageLatencyMs: kotlin.Long,

    @SerialName(value = "roundTripLatencyMs") @Required val roundTripLatencyMs: kotlin.Long

) {

}

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

data class CollectionSyncAccepted (

    @SerialName(value = "mutationId") @Required val mutationId: kotlin.String,

    @SerialName(value = "revision") @Required val revision: kotlin.Long,

    @SerialName(value = "changed") @Required val changed: kotlin.Boolean

) {

}

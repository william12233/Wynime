@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.cloud.models

import com.wynime.cloud.models.CollectionSyncAccepted
import com.wynime.cloud.models.CollectionSyncRecord

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class CollectionSyncResponse (

    @SerialName(value = "cursor") @Required val cursor: kotlin.Long,

    @SerialName(value = "records") @Required val records: kotlin.collections.List<CollectionSyncRecord>,

    @SerialName(value = "accepted") @Required val accepted: kotlin.collections.List<CollectionSyncAccepted>,

    @SerialName(value = "upstreamStatus") @Required val upstreamStatus: CollectionSyncResponse.UpstreamStatus,

    @SerialName(value = "pendingCount") @Required val pendingCount: kotlin.Int

) {

    @Serializable
    enum class UpstreamStatus(val value: kotlin.String) {
        @SerialName(value = "not_requested") not_requested("not_requested"),
        @SerialName(value = "pending") pending("pending"),
        @SerialName(value = "unavailable") unavailable("unavailable"),
        @SerialName(value = "synced") synced("synced");
    }

}

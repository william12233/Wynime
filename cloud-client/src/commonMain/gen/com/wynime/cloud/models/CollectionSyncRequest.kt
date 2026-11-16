@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.cloud.models

import com.wynime.cloud.models.CollectionSyncMutation

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class CollectionSyncRequest (

    @SerialName(value = "deviceId") @Required val deviceId: kotlin.String,

    @SerialName(value = "cursor") @Required val cursor: kotlin.Long,

    @SerialName(value = "mode") @Required val mode: CollectionSyncRequest.Mode,

    @SerialName(value = "changes") @Required val changes: kotlin.collections.List<CollectionSyncMutation>,

    @SerialName(value = "fullSnapshot") val fullSnapshot: kotlin.Boolean? = null,

    @SerialName(value = "subjectIds") val subjectIds: kotlin.collections.List<kotlin.Int>? = null,

    @SerialName(value = "includeEpisodes") val includeEpisodes: kotlin.Boolean? = null

) {

    @Serializable
    enum class Mode(val value: kotlin.String) {
        @SerialName(value = "fast") fast("fast"),
        @SerialName(value = "reconcile") reconcile("reconcile");
    }

}

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

data class CollectionSyncMutation (

    @SerialName(value = "mutationId") @Required val mutationId: kotlin.String,

    @SerialName(value = "deviceId") @Required val deviceId: kotlin.String,

    @SerialName(value = "kind") @Required val kind: CollectionSyncMutation.Kind,

    @SerialName(value = "subjectId") @Required val subjectId: kotlin.Int,

    @SerialName(value = "modifiedAt") @Required val modifiedAt: kotlin.Long,

    @SerialName(value = "changedFields") @Required val changedFields: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "episodeId") val episodeId: kotlin.Int? = null,

    @SerialName(value = "fieldModifiedAt") val fieldModifiedAt: kotlin.collections.Map<kotlin.String, kotlin.Long>? = null,

    @SerialName(value = "collectionType") val collectionType: kotlin.String? = null,

    @SerialName(value = "rate") val rate: kotlin.Int? = null,

    @SerialName(value = "comment") val comment: kotlin.String? = null,

    @SerialName(value = "tags") val tags: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "private") val `private`: kotlin.Boolean? = null,

    @SerialName(value = "episodeStatus") val episodeStatus: kotlin.Int? = null,

    @SerialName(value = "volumeStatus") val volumeStatus: kotlin.Int? = null

) {

    @Serializable
    enum class Kind(val value: kotlin.String) {
        @SerialName(value = "subject") subject("subject"),
        @SerialName(value = "episode") episode("episode");
    }

}

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

data class CollectionSyncRecord (

    @SerialName(value = "kind") @Required val kind: CollectionSyncRecord.Kind,

    @SerialName(value = "subjectId") @Required val subjectId: kotlin.Int,

    @SerialName(value = "deleted") @Required val deleted: kotlin.Boolean,

    @SerialName(value = "fieldModifiedAt") @Required val fieldModifiedAt: kotlin.collections.Map<kotlin.String, kotlin.Long>,

    @SerialName(value = "revision") @Required val revision: kotlin.Long,

    @SerialName(value = "episodeId") val episodeId: kotlin.Int? = null,

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

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

data class CollectionRemoval (

    @SerialName(value = "subjectId") @Required val subjectId: kotlin.Int,

    @SerialName(value = "status") @Required val status: CollectionRemoval.Status,

    @SerialName(value = "webUrl") @Required val webUrl: kotlin.String,

    @SerialName(value = "expiresAt") @Required val expiresAt: kotlin.Long

) {

    @Serializable
    enum class Status(val value: kotlin.String) {
        @SerialName(value = "awaiting_web_action") awaiting_web_action("awaiting_web_action"),
        @SerialName(value = "confirmed") confirmed("confirmed");
    }

}


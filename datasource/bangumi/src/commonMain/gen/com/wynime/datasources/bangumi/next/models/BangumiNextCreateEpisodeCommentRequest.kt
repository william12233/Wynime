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

data class BangumiNextCreateEpisodeCommentRequest (

    @SerialName(value = "content") @Required val content: kotlin.String,

    @SerialName(value = "turnstileToken") @Required val turnstileToken: kotlin.String,

    @SerialName(value = "replyTo") val replyTo: kotlin.Int? = 0

) {

}


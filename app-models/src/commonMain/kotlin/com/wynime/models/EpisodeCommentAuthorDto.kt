@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class EpisodeCommentAuthorDto (

    @SerialName(value = "id") @Required val id: kotlin.String,

    @SerialName(value = "nickname") val nickname: kotlin.String? = null,

    @SerialName(value = "avatarUrl") val avatarUrl: kotlin.String? = null

) {

}


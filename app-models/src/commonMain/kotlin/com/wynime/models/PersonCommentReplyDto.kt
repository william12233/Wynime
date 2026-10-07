@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.EpisodeCommentAuthorDto
import com.wynime.models.EpisodeCommentReactionDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class PersonCommentReplyDto (

    @SerialName(value = "id") @Required val id: kotlin.String,

    @SerialName(value = "sourceCommentId") @Required val sourceCommentId: kotlin.String,

    @SerialName(value = "contentBbcode") @Required val contentBbcode: kotlin.String,

    @SerialName(value = "createdAtMillis") @Required val createdAtMillis: kotlin.Long,

    @SerialName(value = "reactions") @Required val reactions: kotlin.collections.List<EpisodeCommentReactionDto>,

    @SerialName(value = "author") val author: EpisodeCommentAuthorDto? = null

) {

}


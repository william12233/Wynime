@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.CommentVoteValueDto
import com.wynime.models.EpisodeCommentAuthorDto
import com.wynime.models.EpisodeCommentReactionDto
import com.wynime.models.PersonCommentReplyDto
import com.wynime.models.PersonCommentSourceDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class PersonCommentDto (

    @SerialName(value = "id") @Required val id: kotlin.String,

    @SerialName(value = "sourceCommentId") @Required val sourceCommentId: kotlin.String,

    @SerialName(value = "contentBbcode") @Required val contentBbcode: kotlin.String,

    @SerialName(value = "createdAtMillis") @Required val createdAtMillis: kotlin.Long,

    @SerialName(value = "replyCount") @Required val replyCount: kotlin.Int,

    @SerialName(value = "briefReplies") @Required val briefReplies: kotlin.collections.List<PersonCommentReplyDto>,

    @SerialName(value = "reactions") @Required val reactions: kotlin.collections.List<EpisodeCommentReactionDto>,

    @SerialName(value = "canReply") @Required val canReply: kotlin.Boolean,

    @SerialName(value = "source") @Required val source: PersonCommentSourceDto,

    @SerialName(value = "likeCount") @Required val likeCount: kotlin.Int,

    @SerialName(value = "author") val author: EpisodeCommentAuthorDto? = null,

    @SerialName(value = "selfVote") val selfVote: CommentVoteValueDto? = null

) {

}


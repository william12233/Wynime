package com.wynime.app.data.models.episode

import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.comment.CommentVoteValue

enum class EpisodeCommentSource {
    WYNIME,
    BANGUMI,
}

data class EpisodeComment(
    val stableId: String,
    val source: EpisodeCommentSource,
    val sourceCommentId: String,
    val commentId: String,
    val episodeId: Long,

    val createdAt: Long,
    val content: String,
    val author: UserInfo?,
    val reactions: List<EpisodeCommentReaction> = emptyList(),
    val replies: List<EpisodeComment> = listOf(),
    val canReply: Boolean = false,

    val replyCount: Int = replies.size,

    val likeCount: Int = 0,

    val selfVote: CommentVoteValue? = null,
)

data class EpisodeCommentReaction(
    val value: String,
    val count: Int,
    val selected: Boolean,
)

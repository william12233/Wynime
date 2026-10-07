package com.wynime.app.data.models.person

import androidx.compose.runtime.Immutable
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.comment.CommentVoteValue

@Immutable
sealed class PersonCommentTarget {
    data class Person(val personId: Int) : PersonCommentTarget()
    data class Character(val characterId: Int) : PersonCommentTarget()
}

enum class PersonCommentSource {
    WYNIME,
    BANGUMI,
}

@Immutable
data class PersonComment(

    val stableId: String,
    val source: PersonCommentSource,

    val sourceCommentId: String,

    val createdAt: Long,
    val content: String,
    val author: UserInfo?,
    val reactions: List<PersonCommentReaction> = emptyList(),

    val replies: List<PersonComment> = emptyList(),
    val canReply: Boolean = false,
    val replyCount: Int = replies.size,

    val likeCount: Int = 0,

    val selfVote: CommentVoteValue? = null,
)

@Immutable
data class PersonCommentReaction(
    val value: String,
    val count: Int,
    val selected: Boolean,
)

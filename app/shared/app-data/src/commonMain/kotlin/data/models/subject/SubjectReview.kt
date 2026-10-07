package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.comment.CommentVoteValue

@Immutable
data class SubjectReview(

    val id: Long,

    val reviewId: String,
    val source: SubjectReviewSource,

    val updatedAt: Long,
    val content: String,
    val creator: UserInfo?,
    val rating: Int,

    val likeCount: Int = 0,

    val selfVote: CommentVoteValue? = null,
)

enum class SubjectReviewSource {
    WYNIME,
    BANGUMI,
}
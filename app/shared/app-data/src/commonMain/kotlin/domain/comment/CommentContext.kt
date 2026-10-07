package com.wynime.app.domain.comment

import androidx.compose.runtime.Immutable
import com.wynime.app.data.models.person.PersonCommentTarget

@Immutable
sealed interface CommentContext {

    data class PersonComment(val target: PersonCommentTarget) : CommentContext

    data class PersonCommentReply(val target: PersonCommentTarget, val commentId: String) : CommentContext

    data class Episode(val subjectId: Int, val episodeId: Long) : CommentContext

    data class SubjectReview(val subjectId: Int) : CommentContext

    data class EpisodeReply(val subjectId: Int, val episodeId: Long, val commentId: String) : CommentContext
}

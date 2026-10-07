package com.wynime.app.ui.comment

import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.episode.EpisodeComment
import com.wynime.app.data.models.episode.EpisodeCommentSource
import com.wynime.app.data.models.person.PersonComment
import com.wynime.app.data.models.person.PersonCommentSource
import com.wynime.app.data.models.subject.SubjectReview
import com.wynime.app.data.models.subject.SubjectReviewSource
import com.wynime.app.ui.richtext.toUIBriefText
import com.wynime.app.ui.richtext.toUIRichElements
import com.wynime.utils.bbcode.BBCode

object CommentMapperContext {
    private fun String.toUiCommentId(): Long = hashCode().toLong()

    fun parseBBCode(code: String): UIRichText = UIRichText(BBCode.parse(code).toUIRichElements())

    fun parseBBCodeAsReply(code: String): UIRichText =
        UIRichText(listOf(BBCode.parse(code).toUIBriefText().copy(maxLine = 2)))

    private fun CommentVoteValue.toUICommentVote(): UICommentVote = when (this) {
        CommentVoteValue.LIKE -> UICommentVote.LIKE
        CommentVoteValue.DISLIKE -> UICommentVote.DISLIKE
    }

    fun UICommentVote.toCommentVoteValue(): CommentVoteValue = when (this) {
        UICommentVote.LIKE -> CommentVoteValue.LIKE
        UICommentVote.DISLIKE -> CommentVoteValue.DISLIKE
    }

    fun SubjectReview.parseToUIComment() =
        UIComment(
            id = id,
            stableId = id.toString(),
            author = creator,
            content = parseBBCode(content),
            createdAt = updatedAt,
            reactions = emptyList(),
            briefReplies = emptyList(),
            replyCount = 0,
            rating = rating,
            source = when (source) {
                SubjectReviewSource.WYNIME -> UICommentSource.WYNIME
                SubjectReviewSource.BANGUMI -> UICommentSource.BANGUMI
            },
            sourceCommentId = reviewId,
            canReply = false,
            likeCount = likeCount,
            selfVote = selfVote?.toUICommentVote(),
            rawContent = content,
        )

    fun EpisodeComment.parseToUIComment(): UIComment {
        val comment = this
        return UIComment(
            id = comment.stableId.toUiCommentId(),
            stableId = comment.stableId,
            author = comment.author,
            content = parseBBCode(comment.content),
            createdAt = comment.createdAt,
            reactions = comment.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
            briefReplies = comment.replies.map { reply ->
                UIComment(
                    id = reply.stableId.toUiCommentId(),
                    stableId = reply.stableId,
                    author = reply.author,
                    content = parseBBCode(reply.content),
                    createdAt = reply.createdAt,
                    reactions = reply.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = null,
                    source = when (reply.source) {
                        EpisodeCommentSource.WYNIME -> UICommentSource.WYNIME
                        EpisodeCommentSource.BANGUMI -> UICommentSource.BANGUMI
                    },
                    sourceCommentId = reply.sourceCommentId,
                    canReply = reply.canReply,
                    rawContent = reply.content,
                    episodeId = reply.episodeId,
                )
            },
            replyCount = comment.replyCount,
            rating = null,
            source = when (comment.source) {
                EpisodeCommentSource.WYNIME -> UICommentSource.WYNIME
                EpisodeCommentSource.BANGUMI -> UICommentSource.BANGUMI
            },
            sourceCommentId = comment.sourceCommentId,
            canReply = comment.canReply,
            likeCount = comment.likeCount,
            selfVote = comment.selfVote?.toUICommentVote(),
            rawContent = comment.content,
            episodeId = comment.episodeId,
        )
    }

    fun PersonComment.parseToUIComment(): UIComment {
        val comment = this
        return UIComment(
            id = comment.stableId.toUiCommentId(),
            stableId = comment.stableId,
            author = comment.author,
            content = parseBBCode(comment.content),
            createdAt = comment.createdAt,
            reactions = comment.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
            briefReplies = comment.replies.map { reply ->
                UIComment(
                    id = reply.stableId.toUiCommentId(),
                    stableId = reply.stableId,
                    author = reply.author,
                    content = parseBBCode(reply.content),
                    createdAt = reply.createdAt,
                    reactions = reply.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = null,
                    source = reply.source.toUICommentSource(),
                    sourceCommentId = reply.sourceCommentId,
                    canReply = reply.canReply,
                    rawContent = reply.content,
                )
            },
            replyCount = comment.replyCount,
            rating = null,
            source = comment.source.toUICommentSource(),
            sourceCommentId = comment.sourceCommentId,
            canReply = comment.canReply,
            likeCount = comment.likeCount,
            selfVote = comment.selfVote?.toUICommentVote(),
            rawContent = comment.content,
        )
    }

    private fun PersonCommentSource.toUICommentSource(): UICommentSource = when (this) {
        PersonCommentSource.WYNIME -> UICommentSource.WYNIME
        PersonCommentSource.BANGUMI -> UICommentSource.BANGUMI
    }
}

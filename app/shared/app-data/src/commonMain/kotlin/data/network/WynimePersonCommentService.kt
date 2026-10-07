package com.wynime.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.person.PersonComment
import com.wynime.app.data.models.person.PersonCommentReaction
import com.wynime.app.data.models.person.PersonCommentSource
import com.wynime.app.data.models.person.PersonCommentTarget
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.RepositoryException
import com.wynime.models.EpisodeCommentReactionDto
import com.wynime.models.PersonCommentDto
import com.wynime.models.PersonCommentReplyDto
import com.wynime.models.PersonCommentSourceDto
import com.wynime.models.PersonCommentsResponseDto
import com.wynime.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

open class WynimePersonCommentService(
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val bangumiApi: BangumiApiProvider? = null,
) {
    open suspend fun listComments(
        target: PersonCommentTarget,
        after: String? = null,
        limit: Int = 30,
    ): PersonCommentsResponseDto = call {
        if (bangumiApi != null) {
            return@call PersonCommentsResponseDto(
                total = 0,
                items = emptyList(),
                bangumiUnavailable = false,
                nextCursor = null,
            )
        }
        throw RepositoryRequestError("Bangumi 人物留言服務未設定")
    }

    open suspend fun createComment(target: PersonCommentTarget, contentBbcode: String) {
        if (bangumiApi != null) {
            throw RepositoryRequestError("官方 Bangumi 目前不提供人物或角色留言寫入介面")
        }
        throw RepositoryRequestError("Bangumi 人物留言服務未設定")
    }

    open suspend fun createReply(target: PersonCommentTarget, commentId: String, contentBbcode: String) {
        if (bangumiApi != null) {
            throw RepositoryRequestError("官方 Bangumi 目前不提供人物或角色留言回覆介面")
        }
        throw RepositoryRequestError("Bangumi 人物留言服務未設定")
    }

    open suspend fun addReaction(target: PersonCommentTarget, commentId: String, value: String) {
        if (bangumiApi != null) {
            throw RepositoryRequestError("官方 Bangumi 目前不提供人物或角色留言互動介面")
        }
        throw RepositoryRequestError("Bangumi 人物留言服務未設定")
    }

    open suspend fun removeReaction(target: PersonCommentTarget, commentId: String, value: String) {
        if (bangumiApi != null) {
            throw RepositoryRequestError("官方 Bangumi 目前不提供人物或角色留言互動介面")
        }
        throw RepositoryRequestError("Bangumi 人物留言服務未設定")
    }

    open suspend fun vote(target: PersonCommentTarget, commentId: String, vote: CommentVoteValue?) {
        if (bangumiApi != null) {
            throw RepositoryRequestError("官方 Bangumi 目前不提供人物或角色評價投票介面")
        }
        throw RepositoryRequestError("Bangumi 人物留言服務未設定")
    }

    private suspend inline fun <R> call(crossinline block: suspend () -> R): R = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

fun PersonCommentDto.toPersonComment(): PersonComment {

    val commentSource = source.toPersonCommentSource()
    return PersonComment(
        stableId = id,
        source = commentSource,
        sourceCommentId = sourceCommentId,
        createdAt = createdAtMillis,
        content = contentBbcode,
        author = author?.let {
            UserInfo(
                id = it.id,
                username = null,
                nickname = it.nickname,
                avatarUrl = it.avatarUrl,
            )
        },
        reactions = reactions.map { it.toPersonCommentReaction() },
        replies = briefReplies.map { it.toPersonComment(commentSource) },
        canReply = canReply,
        replyCount = replyCount,
        likeCount = likeCount,
        selfVote = selfVote?.toCommentVoteValue(),
    )
}

private fun PersonCommentReplyDto.toPersonComment(source: PersonCommentSource): PersonComment {
    return PersonComment(
        stableId = id,
        source = source,
        sourceCommentId = sourceCommentId,
        createdAt = createdAtMillis,
        content = contentBbcode,
        author = author?.let {
            UserInfo(
                id = it.id,
                username = null,
                nickname = it.nickname,
                avatarUrl = it.avatarUrl,
            )
        },
        reactions = reactions.map { it.toPersonCommentReaction() },
        canReply = false,
    )
}

private fun PersonCommentSourceDto.toPersonCommentSource(): PersonCommentSource = when (this) {
    PersonCommentSourceDto.LEGACY_SERVICE -> PersonCommentSource.WYNIME
    PersonCommentSourceDto.BANGUMI -> PersonCommentSource.BANGUMI
}

private fun EpisodeCommentReactionDto.toPersonCommentReaction(): PersonCommentReaction {
    return PersonCommentReaction(
        value = value,
        count = count,
        selected = selected,
    )
}

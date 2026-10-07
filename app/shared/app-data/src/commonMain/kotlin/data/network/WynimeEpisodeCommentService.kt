package com.wynime.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.episode.EpisodeComment
import com.wynime.app.data.models.episode.EpisodeCommentReaction
import com.wynime.app.data.models.episode.EpisodeCommentSource
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.models.CommentVoteValueDto
import com.wynime.models.EpisodeCommentDto
import com.wynime.models.EpisodeCommentAuthorDto
import com.wynime.models.EpisodeCommentReactionDto
import com.wynime.models.EpisodeCommentReplyDto
import com.wynime.models.EpisodeCommentSourceDto
import com.wynime.models.EpisodeCommentsResponseDto
import com.wynime.datasources.bangumi.next.models.BangumiNextCommentBase
import com.wynime.datasources.bangumi.next.models.BangumiNextGetEpisodeComments200ResponseInner
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimUser
import com.wynime.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

open class WynimeEpisodeCommentService(
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val bangumiApi: BangumiApiProvider? = null,
) {

    open suspend fun listEpisodeComments(
        episodeId: Long,
        after: String? = null,
        limit: Int = 30,
    ): EpisodeCommentsResponseDto = withContext(ioDispatcher) {
        (bangumiApi ?: throw RepositoryRequestError("Bangumi 留言讀取服務未設定")).let { api ->
            val all = api.nextEpisodeRequest { getEpisodeComments(episodeId.toInt()) }
            val offset = after?.removePrefix(BANGUMI_CURSOR_PREFIX)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val items = all.drop(offset).take(limit)
            return@withContext EpisodeCommentsResponseDto(
                total = all.size.toLong(),
                items = items.map { it.toWynimeEpisodeComment(episodeId) },
                bangumiUnavailable = false,
                nextCursor = (offset + items.size).takeIf { it < all.size }
                    ?.let { "$BANGUMI_CURSOR_PREFIX$it" },
            )
        }
    }

    open suspend fun createEpisodeComment(
        episodeId: Long,
        contentBbcode: String,
    ): Unit {
        throw RepositoryRequestError("請至 Bangumi 網頁完成留言互動")
    }

    open suspend fun createEpisodeReply(
        episodeId: Long,
        commentId: String,
        contentBbcode: String,
    ): Unit {
        throw RepositoryRequestError("請至 Bangumi 網頁完成留言互動")
    }

    open suspend fun addEpisodeCommentReaction(
        episodeId: Long,
        commentId: String,
        value: String,
    ): Unit {
        throw RepositoryRequestError("請至 Bangumi 網頁完成留言互動")
    }

    open suspend fun removeEpisodeCommentReaction(
        episodeId: Long,
        commentId: String,
        value: String,
    ): Unit {
        throw RepositoryRequestError("請至 Bangumi 網頁完成留言互動")
    }

    open suspend fun voteEpisodeComment(
        episodeId: Long,
        commentId: String,
        vote: CommentVoteValue?,
    ): Unit {
        throw RepositoryRequestError("請至 Bangumi 網頁完成留言互動")
    }
}

private const val BANGUMI_CURSOR_PREFIX = "bangumi:"

private fun BangumiNextGetEpisodeComments200ResponseInner.toWynimeEpisodeComment(episodeId: Long) =
    EpisodeCommentDto(
        id = "bangumi:$id",
        sourceCommentId = id.toString(),
        episodeId = episodeId,
        contentBbcode = content,
        createdAtMillis = createdAt.toLong() * 1000,
        replyCount = replies.size,
        briefReplies = replies.map { it.toWynimeEpisodeCommentReply(episodeId) },
        reactions = reactions.orEmpty().map { reaction ->
            EpisodeCommentReactionDto(
                value = reaction.value.toString(),
                count = reaction.users.size,
                selected = false,
            )
        },
        canReply = false,
        source = EpisodeCommentSourceDto.BANGUMI,
        likeCount = reactions.orEmpty().sumOf { it.users.size },
        author = user?.toWynimeEpisodeCommentAuthor(),
        selfVote = null,
    )

private fun BangumiNextCommentBase.toWynimeEpisodeCommentReply(episodeId: Long) =
    EpisodeCommentReplyDto(
        id = "bangumi:$id",
        sourceCommentId = id.toString(),
        episodeId = episodeId,
        contentBbcode = content,
        createdAtMillis = createdAt.toLong() * 1000,
        reactions = reactions.orEmpty().map { reaction ->
            EpisodeCommentReactionDto(
                value = reaction.value.toString(),
                count = reaction.users.size,
                selected = false,
            )
        },
        author = user?.toWynimeEpisodeCommentAuthor(),
    )

private fun BangumiNextSlimUser.toWynimeEpisodeCommentAuthor() = EpisodeCommentAuthorDto(
        id = id.toString(),
        nickname = nickname,
        avatarUrl = avatar.large,
    )

internal fun CommentVoteValue.toWynimeCommentVoteValue(): CommentVoteValueDto = when (this) {
    CommentVoteValue.LIKE -> CommentVoteValueDto.LIKE
    CommentVoteValue.DISLIKE -> CommentVoteValueDto.DISLIKE
}

internal fun CommentVoteValueDto.toCommentVoteValue(): CommentVoteValue = when (this) {
    CommentVoteValueDto.LIKE -> CommentVoteValue.LIKE
    CommentVoteValueDto.DISLIKE -> CommentVoteValue.DISLIKE
}

fun EpisodeCommentDto.toEpisodeComment(): EpisodeComment {

    val commentSource = when (source) {
        EpisodeCommentSourceDto.LEGACY_SERVICE -> EpisodeCommentSource.WYNIME
        EpisodeCommentSourceDto.BANGUMI -> EpisodeCommentSource.BANGUMI
    }
    return EpisodeComment(
        stableId = id,
        source = commentSource,
        sourceCommentId = sourceCommentId,
        commentId = sourceCommentId,
        episodeId = episodeId,
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
        reactions = reactions.map { it.toEpisodeCommentReaction() },
        replies = briefReplies.map { it.toEpisodeComment(episodeId, commentSource) },
        canReply = canReply,
        replyCount = replyCount,
        likeCount = likeCount,
        selfVote = selfVote?.toCommentVoteValue(),
    )
}

private fun EpisodeCommentReplyDto.toEpisodeComment(
    episodeId: Long,
    source: EpisodeCommentSource,
): EpisodeComment {
    return EpisodeComment(
        stableId = id,
        source = source,
        sourceCommentId = sourceCommentId,
        commentId = sourceCommentId,
        episodeId = episodeId,
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
        reactions = reactions.map { it.toEpisodeCommentReaction() },
        canReply = false,
    )
}

private fun com.wynime.models.EpisodeCommentReactionDto.toEpisodeCommentReaction(): EpisodeCommentReaction {
    return EpisodeCommentReaction(
        value = value,
        count = count,
        selected = selected,
    )
}

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.comment.CommentVoteValue
import me.him188.ani.app.data.models.subject.SubjectReview
import me.him188.ani.app.data.models.subject.SubjectReviewSource
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSubjectInterestComment
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

interface BangumiCommentService {
    /**
     * @return `null` if [subjectId] is invalid
     */
    suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>?

    /**
     * 对条目评价投票. [vote] 为 `null` 表示取消投票.
     * 只有 [SubjectReviewSource.ANI] 来源的评价可投票.
     */
    suspend fun voteSubjectReview(subjectId: Int, reviewId: String, vote: CommentVoteValue?)
}

class BangumiBangumiCommentServiceImpl(
    private val bangumiApi: BangumiApiProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : BangumiCommentService {
    override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>? {
        return withContext(ioDispatcher) {
            val response = bangumiApi.nextSubjectRequest {
                getSubjectComments(subjectID = subjectId, limit = limit, offset = offset)
            }
            val list = response.data.map { it.toSubjectReview() }
            Paged(
                total = response.total,
                hasMore = offset + list.size < response.total,
                page = list,
            )
        }
    }

    override suspend fun voteSubjectReview(subjectId: Int, reviewId: String, vote: CommentVoteValue?) {
        throw RepositoryRequestError("官方 Bangumi 吐槽箱目前不提供評價投票介面")
    }
}

private fun BangumiNextSubjectInterestComment.toSubjectReview() = SubjectReview(
    id = id.toLong(),
    reviewId = id.toString(),
    source = SubjectReviewSource.BANGUMI,
    content = comment,
    updatedAt = updatedAt.toLong() * 1000,
    rating = rate,
    creator = UserInfo(
        id = user.id.toString(),
        nickname = user.nickname,
        username = user.username,
        avatarUrl = user.avatar.large,
    ),
    likeCount = reactions.orEmpty().sumOf { it.users.size },
)

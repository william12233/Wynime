package com.wynime.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.subject.SubjectReview
import com.wynime.app.data.models.subject.SubjectReviewSource
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.datasources.api.paging.Paged
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectInterestComment
import com.wynime.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

interface BangumiCommentService {

    suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>?

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

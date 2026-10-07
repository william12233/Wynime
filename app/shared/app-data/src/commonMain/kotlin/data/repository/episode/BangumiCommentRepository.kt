package com.wynime.app.data.repository.episode

import androidx.compose.ui.util.packInts
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingData.Companion.from
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.episode.EpisodeComment
import com.wynime.app.data.models.episode.EpisodeCommentSource
import com.wynime.app.data.models.subject.SubjectReview
import com.wynime.app.data.models.subject.SubjectReviewSource
import com.wynime.app.data.network.BangumiCommentService
import com.wynime.app.data.persistent.database.dao.SubjectReviewDao
import com.wynime.app.data.persistent.database.entity.SubjectReviewEntity
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException

class BangumiCommentRepository(
    private val commentService: BangumiCommentService,
    private val subjectReviewDao: SubjectReviewDao,
) : Repository() {
    fun subjectCommentsPager(subjectId: Int, onTotalCount: (Int?) -> Unit = {}): Flow<PagingData<SubjectReview>> {
        return Pager(
            config = defaultPagingConfig,
            initialKey = 0,
            pagingSourceFactory = {
                SubjectReviewPagingSource(subjectId, onTotalCount)
            },
        ).flow
    }

    suspend fun voteSubjectReview(subjectId: Int, reviewId: String, vote: CommentVoteValue?) {
        commentService.voteSubjectReview(subjectId, reviewId, vote)
    }

    private inner class SubjectReviewPagingSource(
        private val subjectId: Int,
        private val onTotalCount: (Int?) -> Unit,
    ) : PagingSource<Int, SubjectReview>() {
        override fun getRefreshKey(state: PagingState<Int, SubjectReview>): Int? = state.anchorPosition

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SubjectReview> = withContext(defaultDispatcher) {
            val offset = params.key ?: 0
            try {
                val subjectReviews = commentService.getSubjectComments(subjectId, offset, params.loadSize)
                    ?: return@withContext LoadResult.Page(
                        data = emptyList(),
                        prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                        nextKey = null,
                    )

                onTotalCount(subjectReviews.total.takeUnless { subjectReviews.hasMore })
                LoadResult.Page(
                    data = subjectReviews.page,
                    prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                    nextKey = if (subjectReviews.hasMore) offset + params.loadSize else null,
                )
            } catch (e: Exception) {
                LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }
    }

    private inner class SubjectReviewRemoteMediator<T : Any>(
        private val subjectId: Int,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult = withContext(defaultDispatcher) {
            val offset = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastLoadedPage = state.pages.lastOrNull()
                    if (lastLoadedPage != null) {
                        lastLoadedPage.itemsBefore + lastLoadedPage.data.size
                    } else {
                        0
                    }
                }
            }

            try {
                val subjectReviews = commentService.getSubjectComments(subjectId, offset, state.config.pageSize)
                    ?: return@withContext MediatorResult.Success(endOfPaginationReached = true)

                subjectReviewDao.upsert(subjectReviews.page.mapNotNull { it.toEntity(subjectId) })

                MediatorResult.Success(endOfPaginationReached = !subjectReviews.hasMore)
            } catch (e: Exception) {
                return@withContext MediatorResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }
    }
}

private fun SubjectReview.toEntity(subjectId: Int): SubjectReviewEntity? {
    return SubjectReviewEntity(
        subjectId = subjectId,
        authorId = creator?.id?.toIntOrNull() ?: return null,
        content = content,
        updatedAt = updatedAt,
        rating = rating,
        authorNickname = creator.nickname ?: "",
        authorAvatarUrl = creator.avatarUrl,
    )
}

private fun SubjectReviewEntity.toInfo(): SubjectReview {
    return SubjectReview(
        id = packInts(subjectId, authorId),
        reviewId = "",
        source = SubjectReviewSource.BANGUMI,
        updatedAt = updatedAt,
        content = content,
        creator = UserInfo(
            id = authorId.toString(),
            username = null,
            nickname = authorNickname,
            avatarUrl = authorAvatarUrl,
        ),
        rating = rating,
    )
}

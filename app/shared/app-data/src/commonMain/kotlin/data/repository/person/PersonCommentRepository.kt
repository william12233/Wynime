package com.wynime.app.data.repository.person

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.person.PersonComment
import com.wynime.app.data.models.person.PersonCommentTarget
import com.wynime.app.data.network.WynimePersonCommentService
import com.wynime.app.data.network.toPersonComment
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.runWrappingExceptionAsLoadResult

class PersonCommentRepository(
    private val wynimeCommentService: WynimePersonCommentService,
) : Repository() {

    fun commentsPager(
        target: PersonCommentTarget,
        onBangumiUnavailable: () -> Unit = {},
    ): Flow<PagingData<PersonComment>> {
        return Pager(defaultPagingConfig) {
            PersonCommentPagingSource(
                target = target,
                wynimeCommentService = wynimeCommentService,
                onBangumiUnavailable = onBangumiUnavailable,
            )
        }.flow
    }

    suspend fun submitReaction(
        target: PersonCommentTarget,
        commentId: String,
        value: String,
        selected: Boolean,
    ) {
        if (selected) {
            wynimeCommentService.addReaction(target, commentId, value)
        } else {
            wynimeCommentService.removeReaction(target, commentId, value)
        }
    }

    suspend fun submitVote(
        target: PersonCommentTarget,
        commentId: String,
        vote: CommentVoteValue?,
    ) {
        wynimeCommentService.vote(target, commentId, vote)
    }
}

internal class PersonCommentPagingSource(
    private val target: PersonCommentTarget,
    private val wynimeCommentService: WynimePersonCommentService,
    private val onBangumiUnavailable: () -> Unit = {},
) : PagingSource<String, PersonComment>() {
    override fun getRefreshKey(state: PagingState<String, PersonComment>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, PersonComment> {
        return runWrappingExceptionAsLoadResult {
            val response = wynimeCommentService.listComments(
                target = target,
                after = params.key,
                limit = params.loadSize.coerceAtMost(MAX_LIMIT),
            )

            if (response.bangumiUnavailable && params.key == null) {
                onBangumiUnavailable()
            }
            LoadResult.Page(
                data = response.items.map { it.toPersonComment() },
                prevKey = null,
                nextKey = response.nextCursor,
            )
        }
    }

    private companion object {

        const val MAX_LIMIT = 100
    }
}

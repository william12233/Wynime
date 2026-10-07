package com.wynime.app.data.repository.episode

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.episode.EpisodeComment
import com.wynime.app.data.network.WynimeEpisodeCommentService
import com.wynime.app.data.network.toEpisodeComment
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.runWrappingExceptionAsLoadResult

class EpisodeCommentRepository(
    private val wynimeCommentService: WynimeEpisodeCommentService,
) : Repository() {

    fun subjectEpisodeCommentsPager(
        episodeId: Long,
        onBangumiUnavailable: () -> Unit = {},
    ): Flow<PagingData<EpisodeComment>> {
        return Pager(defaultPagingConfig) {
            EpisodeCommentPagingSource(
                episodeId = episodeId,
                wynimeCommentService = wynimeCommentService,
                onBangumiUnavailable = onBangumiUnavailable,
            )
        }.flow
    }

    suspend fun submitReaction(
        episodeId: Long,
        commentId: String,
        value: String,
        selected: Boolean,
    ) {
        if (selected) {
            wynimeCommentService.addEpisodeCommentReaction(episodeId, commentId, value)
        } else {
            wynimeCommentService.removeEpisodeCommentReaction(episodeId, commentId, value)
        }
    }

    suspend fun submitVote(
        episodeId: Long,
        commentId: String,
        vote: CommentVoteValue?,
    ) {
        wynimeCommentService.voteEpisodeComment(episodeId, commentId, vote)
    }
}

internal class EpisodeCommentPagingSource(
    private val episodeId: Long,
    private val wynimeCommentService: WynimeEpisodeCommentService,
    private val onBangumiUnavailable: () -> Unit = {},
) : PagingSource<String, EpisodeComment>() {
    override fun getRefreshKey(state: PagingState<String, EpisodeComment>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, EpisodeComment> {
        return runWrappingExceptionAsLoadResult {
            val response = wynimeCommentService.listEpisodeComments(
                episodeId = episodeId,
                after = params.key,
                limit = params.loadSize.coerceAtMost(MAX_LIMIT),
            )

            if (response.bangumiUnavailable && params.key == null) {
                onBangumiUnavailable()
            }
            LoadResult.Page(
                data = response.items.map { it.toEpisodeComment() },
                prevKey = null,
                nextKey = response.nextCursor,
            )
        }
    }

    private companion object {

        const val MAX_LIMIT = 100
    }
}

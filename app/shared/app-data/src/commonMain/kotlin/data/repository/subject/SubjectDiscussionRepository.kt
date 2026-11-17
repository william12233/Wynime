package com.wynime.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.subject.SubjectDiscussion
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.repository.RepositoryException

class SubjectDiscussionRepository(private val bangumiApi: BangumiApiProvider) {
    private val topicRequests = Semaphore(3)

    fun discussionsPager(subjectId: Int): Flow<PagingData<SubjectDiscussion>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { pagingSource(subjectId) },
    ).flow

    internal fun pagingSource(subjectId: Int): PagingSource<Int, SubjectDiscussion> =
        object : PagingSource<Int, SubjectDiscussion>() {
            override fun getRefreshKey(state: PagingState<Int, SubjectDiscussion>): Int? = null

            override suspend fun load(
                params: LoadParams<Int>,
            ): LoadResult<Int, SubjectDiscussion> = withContext(Dispatchers.Default) {
                val offset = params.key ?: 0
                try {
                    val page = bangumiApi.getSubjectTopics(subjectId, PAGE_SIZE, offset)
                    val discussions = coroutineScope {
                        page.data.map { topic ->
                            async {
                                val detail = topicRequests.withPermit { bangumiApi.getSubjectTopic(topic.id) }
                                SubjectDiscussion(
                                    id = topic.id,
                                    title = topic.title,
                                    author = (topic.creator ?: detail?.creator)?.toUserInfo()
                                        ?: UserInfo(id = topic.creatorId.toString(), username = null),
                                    createdAtMillis = topic.createdAt * 1000,
                                    content = detail?.openingPost(topic),
                                )
                            }
                        }.awaitAll()
                    }
                    val nextOffset = offset + page.data.size
                    LoadResult.Page(
                        data = discussions,
                        prevKey = null,
                        nextKey = nextOffset.takeIf { page.data.isNotEmpty() && it < page.total },
                    )
                } catch (e: Exception) {
                    LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e))
                }
            }
        }

    private companion object {
        const val PAGE_SIZE = 10
    }
}

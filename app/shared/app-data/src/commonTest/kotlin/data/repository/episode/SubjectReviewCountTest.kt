package com.wynime.app.data.repository.episode

import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.subject.SubjectReview
import com.wynime.app.data.models.subject.SubjectReviewSource
import com.wynime.app.data.network.BangumiCommentService
import com.wynime.app.data.persistent.database.dao.SubjectReviewDao
import com.wynime.app.data.persistent.database.entity.SubjectReviewEntity
import com.wynime.datasources.api.paging.Paged
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectReviewCountTest {
    @Test
    fun completedPageDeliversTheServerTotal() = verifyPage(hasMore = false, expectedTotal = 128)

    @Test
    fun nonFinalPageDoesNotPublishALowerBoundAsTheTotal() = verifyPage(hasMore = true, expectedTotal = null)

    private fun verifyPage(hasMore: Boolean, expectedTotal: Int?) = runTest {
        val review = SubjectReview(1, "review-1", SubjectReviewSource.BANGUMI, 0, "A review", null, 8)
        val service = object : BangumiCommentService {
            override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int) =
                Paged(total = if (hasMore) 2 else 128, hasMore = hasMore, page = listOf(review))

            override suspend fun voteSubjectReview(subjectId: Int, reviewId: String, vote: CommentVoteValue?) = Unit
        }
        val dao = object : SubjectReviewDao {
            override suspend fun upsert(item: SubjectReviewEntity) = error("The network pager must not write the database")
            override suspend fun upsert(item: List<SubjectReviewEntity>) = error("The network pager must not write the database")
            override fun filterBySubjectIdPager(subjectId: Int): PagingSource<Int, SubjectReviewEntity> = error("Unused")
        }
        val total = CompletableDeferred<Int?>()
        val received = CompletableDeferred<List<SubjectReview>>()
        val presenter = object : PagingDataPresenter<SubjectReview>(mainContext = coroutineContext) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<SubjectReview>) {
                received.complete(snapshot().items)
            }
        }
        backgroundScope.launch {
            BangumiCommentRepository(service, dao).subjectCommentsPager(42) { total.complete(it) }
                .collectLatest(presenter::collectFrom)
        }
        assertEquals(expectedTotal, total.await())
        assertEquals(listOf(review), received.await())
    }
}

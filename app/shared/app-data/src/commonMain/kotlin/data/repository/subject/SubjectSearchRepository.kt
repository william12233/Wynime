package com.wynime.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.schedule.AnimeSeasonId
import com.wynime.app.data.models.schedule.yearMonths
import com.wynime.app.data.network.WynimeSubjectSearchService
import com.wynime.app.data.network.BatchSubjectDetails
import com.wynime.app.data.network.SubjectSearchField
import com.wynime.app.data.network.SubjectSearchFilters
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.domain.search.RatingRange
import com.wynime.app.domain.search.SearchSort
import com.wynime.app.domain.search.SubjectSearchQuery
import com.wynime.datasources.api.topic.UnifiedCollectionType
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class SubjectSearchRepository(
    private val wynimeSubjectSearchService: WynimeSubjectSearchService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {

    fun searchSubjects(
        searchQuery: SubjectSearchQuery,
        ignoreDoneAndDropped: suspend () -> Boolean = { false },
        pagingConfig: PagingConfig = bangumiSearchPagingConfig
    ): Flow<PagingData<BatchSubjectDetails>> = Pager(
        config = pagingConfig,
        initialKey = 0,
        pagingSourceFactory = {
            SubjectSearchPagingSource(ignoreDoneAndDropped, searchQuery)
        },
    ).flow.flowOn(defaultDispatcher)

    private inner class SubjectSearchPagingSource(
        private val ignoreDoneAndDropped: suspend () -> Boolean,
        private val searchQuery: SubjectSearchQuery
    ) : PagingSource<Int, BatchSubjectDetails>() {
        private val filters = searchQuery.toSubjectSearchFilters()
        override fun getRefreshKey(state: PagingState<Int, BatchSubjectDetails>): Int? = null
        override suspend fun load(
            params: LoadParams<Int>
        ): LoadResult<Int, BatchSubjectDetails> = withContext(defaultDispatcher) {
            val offset = params.key
                ?: return@withContext LoadResult.Error(IllegalArgumentException("Key is null"))
            return@withContext try {
                val subjects = wynimeSubjectSearchService.searchSubjects(
                    searchQuery.keywords,
                    offset = offset,
                    limit = params.loadSize,
                    filters = filters,
                    sort = searchQuery.sort,
                    fields = subjectSearchFields,
                )

                val filteredSubjects = if (ignoreDoneAndDropped()) {
                    val excludedIds = subjectCollectionRepository.getSubjectIdsByCollectionType(
                        types = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED),
                    ).first()

                    subjects.filter { it.subjectInfo.subjectId !in excludedIds }
                } else {
                    subjects
                }

                val subjectInfos = filterSubjectsBySort(
                    filteredSubjects,
                    searchQuery.sort,
                )

                return@withContext LoadResult.Page(
                    subjectInfos,
                    prevKey = if (offset == 0) null else offset,
                    nextKey = if (subjectInfos.isEmpty()) null else offset + params.loadSize,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }

        private fun SubjectSearchQuery.toSubjectSearchFilters(): SubjectSearchFilters {
            return SubjectSearchFilters(
                tags,
                airDates = toBangumiAirDates(),
                ratings = rating?.toBangumiRatings(),
                nsfw = nsfw,
            )
        }

        private fun RatingRange.toBangumiRatings(): List<String> {
            val range = this
            return listOfNotNull(
                range.min?.let { ">=${it}" },
                range.max?.let { "<${it}" },
            )
        }

        private fun filterSubjectsBySort(
            subjects: List<BatchSubjectDetails>,
            sort: SearchSort
        ): List<BatchSubjectDetails> {
            return when (sort) {
                SearchSort.RANK -> subjects.filter { it.subjectInfo.ratingInfo.total >= 50 }
                SearchSort.DATE -> subjects.sortedByDescending { it.subjectInfo.airDate }
                SearchSort.MATCH,
                SearchSort.COLLECTION -> subjects
            }
        }
    }

    private companion object {
        private val bangumiSearchPagingConfig = PagingConfig(
            pageSize = 20,
            initialLoadSize = 20,
        )

        private val subjectSearchFields = listOf(
            SubjectSearchField.NAME,
            SubjectSearchField.SUMMARY,
            SubjectSearchField.IMAGE_LARGE,
            SubjectSearchField.NSFW,
            SubjectSearchField.AIR_DATE,
            SubjectSearchField.SCORE,
            SubjectSearchField.RANK,
            SubjectSearchField.RATING_TOTAL,
            SubjectSearchField.TAGS,
            SubjectSearchField.MAIN_EPISODE_COUNT,
            SubjectSearchField.LIGHT_RELATED_PERSON_INFO,
        )
    }
}

internal fun SubjectSearchQuery.toBangumiAirDates(): List<String>? {
    val y = year ?: return null
    val q = season
    if (q == null) {
        return listOf(">=$y-01-01", "<${y + 1}-01-01")
    }
    val (begin, _, end) = AnimeSeasonId(y, q).yearMonths

    fun Int.twoDigits(): String = toString().padStart(2, '0')
    return listOf(
        ">=${begin.first}-${begin.second.twoDigits()}-01",
        "<${end.first}-${(end.second + 1).twoDigits()}-01",
    )
}

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import com.wynime.datasources.api.PackedDate
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
        private val dateSortedSubjectsMutex = Mutex()
        private val dateSortedSubjects = ArrayList<BatchSubjectDetails>()
        private val dateSortedSubjectIds = HashSet<Int>()
        private val dateSortRanges = if (searchQuery.year == null) {
            dateSearchAirDateRanges()
        } else {
            emptyList()
        }
        private var dateSortRangeIndex = 0
        private var dateSortUnknownSubjectsLoaded = false
        private var dateSortFullyLoaded = false
        private var excludedSubjectIds: Set<Int>? = null

        override fun getRefreshKey(state: PagingState<Int, BatchSubjectDetails>): Int? = null
        override suspend fun load(
            params: LoadParams<Int>
        ): LoadResult<Int, BatchSubjectDetails> = withContext(defaultDispatcher) {
            return@withContext try {
                if (searchQuery.sort == SearchSort.DATE) {
                    loadDateSortedPage(params)
                } else {
                    loadRemotePage(params)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }

        private suspend fun loadRemotePage(
            params: LoadParams<Int>,
        ): LoadResult<Int, BatchSubjectDetails> {
            val offset = params.key
                ?: return LoadResult.Error(IllegalArgumentException("Key is null"))
            val subjects = wynimeSubjectSearchService.searchSubjects(
                searchQuery.keywords,
                offset = offset,
                limit = params.loadSize,
                filters = filters,
                sort = searchQuery.sort,
                fields = subjectSearchFields,
            )
            val filteredSubjects = filterExcludedSubjects(subjects)

            return LoadResult.Page(
                data = filteredSubjects,
                prevKey = if (offset == 0) null else offset,
                nextKey = if (subjects.isEmpty()) null else offset + subjects.size,
            )
        }

        private suspend fun loadDateSortedPage(
            params: LoadParams<Int>,
        ): LoadResult<Int, BatchSubjectDetails> {
            val offset = params.key
                ?: return LoadResult.Error(IllegalArgumentException("Key is null"))
            return dateSortedSubjectsMutex.withLock {
                ensureDateSortedSubjects(offset + params.loadSize)
                val endExclusive = (offset + params.loadSize).coerceAtMost(dateSortedSubjects.size)
                val page = dateSortedSubjects
                    .subList(offset.coerceAtMost(dateSortedSubjects.size), endExclusive)
                    .toList()

                LoadResult.Page(
                    data = page,
                    prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                    nextKey = when {
                        endExclusive < dateSortedSubjects.size -> endExclusive
                        !dateSortFullyLoaded -> endExclusive
                        else -> null
                    },
                )
            }
        }

        private suspend fun ensureDateSortedSubjects(requiredSize: Int) {
            while (!dateSortFullyLoaded && dateSortedSubjects.size < requiredSize) {
                when {
                    searchQuery.year != null -> {
                        loadDateSortedBatch(filters)
                        dateSortFullyLoaded = true
                    }

                    dateSortRangeIndex < dateSortRanges.size -> {
                        val range = dateSortRanges[dateSortRangeIndex]
                        loadDateSortedBatch(filters.copy(airDates = range), onlyValidDates = true)
                        dateSortRangeIndex++
                    }

                    !dateSortUnknownSubjectsLoaded -> {
                        loadDateSortedBatch(filters)
                        dateSortUnknownSubjectsLoaded = true
                    }

                    else -> dateSortFullyLoaded = true
                }
            }
        }

        private suspend fun loadDateSortedBatch(
            batchFilters: SubjectSearchFilters,
            onlyValidDates: Boolean = false,
        ) {
            val excludedIds = getExcludedSubjectIds()
            var offset = 0
            var keyword = searchQuery.keywords
            while (true) {
                val page = wynimeSubjectSearchService.searchSubjectsPage(
                    keyword = keyword,
                    offset = offset,
                    limit = DATE_SORT_FETCH_PAGE_SIZE,
                    filters = batchFilters,
                    sort = SearchSort.DATE,
                    fields = subjectSearchFields,
                )
                keyword = page.resolvedKeyword
                page.items.forEach { subject ->
                    if ((!onlyValidDates || subject.subjectInfo.airDate.isValid) &&
                        subject.subjectInfo.subjectId !in excludedIds &&
                        dateSortedSubjectIds.add(subject.subjectInfo.subjectId)
                    ) {
                        dateSortedSubjects += subject
                    }
                }

                if (page.items.isEmpty()) break
                val pageSize = page.items.size
                offset += pageSize
                if (page.total != null && offset >= page.total) break
                if (page.total == null && pageSize < DATE_SORT_FETCH_PAGE_SIZE) break
            }
            dateSortedSubjects.sortWith(RELEASE_DATE_COMPARATOR)
        }

        private suspend fun filterExcludedSubjects(
            subjects: List<BatchSubjectDetails>,
        ): List<BatchSubjectDetails> {
            val excludedIds = getExcludedSubjectIds()
            return subjects.filter { it.subjectInfo.subjectId !in excludedIds }
        }

        private suspend fun getExcludedSubjectIds(): Set<Int> {
            return excludedSubjectIds ?: if (ignoreDoneAndDropped()) {
                subjectCollectionRepository.getSubjectIdsByCollectionType(
                    types = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED),
                ).first().toSet()
            } else {
                emptySet()
            }.also { excludedSubjectIds = it }
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

    }

    private companion object {
        private val bangumiSearchPagingConfig = PagingConfig(
            pageSize = 20,
            initialLoadSize = 20,
        )

        private const val DATE_SORT_FETCH_PAGE_SIZE = 100

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

internal fun sortSubjectsByReleaseDate(
    subjects: List<BatchSubjectDetails>,
): List<BatchSubjectDetails> = subjects.sortedWith(RELEASE_DATE_COMPARATOR)

private val RELEASE_DATE_COMPARATOR = compareByDescending<BatchSubjectDetails> {
    it.subjectInfo.airDate.isValid
}.thenByDescending { it.subjectInfo.airDate }
    .thenBy { it.subjectInfo.subjectId }

internal fun dateSearchAirDateRanges(
    currentYear: Int = PackedDate.now().year,
    minimumYear: Int = 1900,
): List<List<String>> = buildList {
    add(listOf(">=${currentYear + 1}-01-01"))
    for (year in currentYear downTo minimumYear) {
        for (month in 12 downTo 1) {
            val monthText = month.toString().padStart(2, '0')
            val nextMonth = if (month == 12) {
                "${year + 1}-01-01"
            } else {
                "${year}-${(month + 1).toString().padStart(2, '0')}-01"
            }
            add(listOf(">=$year-$monthText-01", "<$nextMonth"))
        }
    }
    add(listOf("<$minimumYear-01-01"))
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

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Loads the public Bangumi trending feed used by the exploration page. */
class TrendsRepository(
    private val dataSource: BangumiExploreDataSource,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val clock: Clock = Clock.System,
    private val cacheDuration: Duration = 10.minutes,
) : Repository() {
    private val cacheMutex = Mutex()
    private var cachedFirstPage: CachedTrendingPage? = null

    suspend fun getTrendsInfo(forceRefresh: Boolean = false): TrendsInfo {
        return loadPage(limit = FIRST_PAGE_SIZE, offset = 0, forceRefresh = forceRefresh).toTrendsInfo()
    }

    fun trendsInfoPager(): Flow<PagingData<TrendsInfo>> {
        logger.info { "Creating Bangumi trending pager." }
        return Pager(defaultPagingConfig) {
            BangumiTrendingPagingSource()
        }.flow
    }

    private suspend fun loadPage(limit: Int, offset: Int, forceRefresh: Boolean): BangumiTrendingPage {
        val requestedLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        val now = clock.now()
        if (offset == 0 && !forceRefresh) {
            cacheMutex.withLock {
                cachedFirstPage?.takeIf { now - it.cachedAt < cacheDuration }
                    ?.page
                    ?.takeIf { it.subjects.size >= requestedLimit || it.subjects.size >= it.total }
                    ?.let { return it.copy(subjects = it.subjects.take(requestedLimit)) }
            }
        }

        val page = try {
            withContext(ioDispatcher) {
                dataSource.getTrendingSubjects(
                    limit = if (offset == 0) maxOf(requestedLimit, FIRST_PAGE_SIZE) else requestedLimit,
                    offset = offset,
                )
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        if (offset == 0) {
            cacheMutex.withLock {
                cachedFirstPage = CachedTrendingPage(page = page, cachedAt = now)
            }
        }
        return page
    }

    private inner class BangumiTrendingPagingSource : PagingSource<Int, TrendsInfo>() {
        private var firstLoad = true

        override fun getRefreshKey(state: PagingState<Int, TrendsInfo>): Int? = state.anchorPosition

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TrendsInfo> {
            val offset = params.key ?: 0
            logger.info {
                "Loading Bangumi trending page: offset=$offset, requestedSize=${params.loadSize}, firstLoad=$firstLoad."
            }
            return runWrappingExceptionAsLoadResult {
                val page = loadPage(
                    limit = params.loadSize,
                    offset = offset,
                    forceRefresh = firstLoad && offset == 0,
                )
                firstLoad = false
                logger.info {
                    "Loaded Bangumi trending page: offset=$offset, subjects=${page.subjects.size}, total=${page.total}."
                }
                val nextOffset = offset + page.subjects.size
                LoadResult.Page(
                    data = listOf(page.toTrendsInfo()),
                    prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                    nextKey = if (page.subjects.isEmpty() || nextOffset >= page.total) null else nextOffset,
                )
            }.also {
                if (it is LoadResult.Error) {
                    logger.error(it.throwable) {
                        "Failed to load Bangumi trending subjects (operation=trending subjects, endpoint=/p1/trending/subjects)."
                    }
                }
            }
        }
    }

    private data class CachedTrendingPage(
        val page: BangumiTrendingPage,
        val cachedAt: Instant,
    )

    private companion object {
        const val FIRST_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 100
    }
}

internal fun BangumiTrendingPage.toTrendsInfo(): TrendsInfo {
    return TrendsInfo(
        subjects = subjects.map { it.toTrendingSubjectInfo() },
        total = total,
    )
}

internal fun BangumiExploreSubject.toTrendingSubjectInfo(): TrendingSubjectInfo {
    val fallbackName = "Bangumi #$id"
    return TrendingSubjectInfo(
        bangumiId = id,
        nameCn = nameCn.ifBlank { name }.ifBlank { fallbackName },
        name = name.ifBlank { nameCn }.ifBlank { fallbackName },
        imageLarge = imageLarge,
        nsfw = nsfw,
        score = score,
        scoreCount = scoreCount,
        rank = rank,
        tags = tags,
        airDate = airDate,
        trendingCount = trendingCount,
    )
}

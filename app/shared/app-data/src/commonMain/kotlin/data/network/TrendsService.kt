/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Loads the bounded public Bangumi trending candidate set used by recommendations. */
class TrendsRepository(
    private val dataSource: BangumiExploreDataSource,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val clock: Clock = Clock.System,
    private val cacheDuration: Duration = 10.minutes,
) : Repository() {
    private val cacheMutex = Mutex()
    private val requestScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var cachedFirstPage: CachedTrendingPage? = null
    private var inFlightFirstPage: CompletableDeferred<BangumiTrendingPage>? = null

    suspend fun getTrendsInfo(forceRefresh: Boolean = false): TrendsInfo {
        return loadFirstPage(forceRefresh).toTrendsInfo()
    }

    private suspend fun loadFirstPage(forceRefresh: Boolean): BangumiTrendingPage {
        val request = cacheMutex.withLock {
            val now = clock.now()
            if (!forceRefresh) {
                cachedFirstPage
                    ?.takeIf { now - it.cachedAt < cacheDuration }
                    ?.page
                    ?.let { return@withLock TrendingRequest.completed(it) }
            }

            inFlightFirstPage?.let { return@withLock TrendingRequest(it, started = false) }

            val deferred = CompletableDeferred<BangumiTrendingPage>()
            inFlightFirstPage = deferred
            TrendingRequest(deferred, started = true)
        }

        if (request.started) {
            requestScope.launch {
                try {
                    val page = withContext(ioDispatcher) {
                        dataSource.getTrendingSubjects(limit = FIRST_PAGE_SIZE, offset = 0)
                    }
                    cacheMutex.withLock {
                        cachedFirstPage = CachedTrendingPage(page = page, cachedAt = clock.now())
                        if (inFlightFirstPage === request.deferred) inFlightFirstPage = null
                    }
                    request.deferred.complete(page)
                } catch (throwable: Throwable) {
                    val error = try {
                        RepositoryException.wrapOrThrowCancellation(throwable)
                    } catch (cancelled: Throwable) {
                        cancelled
                    }
                    cacheMutex.withLock {
                        if (inFlightFirstPage === request.deferred) inFlightFirstPage = null
                    }
                    request.deferred.completeExceptionally(error)
                }
            }
        }

        return request.deferred.await()
    }

    private data class CachedTrendingPage(
        val page: BangumiTrendingPage,
        val cachedAt: Instant,
    )

    private data class TrendingRequest(
        val deferred: CompletableDeferred<BangumiTrendingPage>,
        val started: Boolean,
    ) {
        companion object {
            fun completed(page: BangumiTrendingPage) = TrendingRequest(
                deferred = CompletableDeferred<BangumiTrendingPage>().apply { complete(page) },
                started = false,
            )
        }
    }

    private companion object {
        const val FIRST_PAGE_SIZE = 50
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

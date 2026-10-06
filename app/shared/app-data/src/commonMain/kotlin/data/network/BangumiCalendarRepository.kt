/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
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
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Shared bounded cache for Bangumi's weekly calendar response. */
class BangumiCalendarRepository(
    private val dataSource: BangumiExploreDataSource,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val clock: Clock = Clock.System,
    private val cacheDuration: Duration = 30.minutes,
) : Repository(ioDispatcher) {
    private val cacheMutex = Mutex()
    private val requestScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var cachedCalendar: CachedCalendar? = null
    private var inFlight: InFlightRequest? = null

    suspend fun getCalendarDays(forceRefresh: Boolean = false): List<BangumiCalendarDay> {
        val request = cacheMutex.withLock {
            val now = clock.now()
            if (!forceRefresh) {
                cachedCalendar
                    ?.takeIf { now - it.cachedAt < cacheDuration }
                    ?.let { return@withLock RequestHandle.completed(it.days) }
            }

            inFlight?.let { return@withLock RequestHandle(it.deferred, started = false) }

            val deferred = CompletableDeferred<List<BangumiCalendarDay>>()
            inFlight = InFlightRequest(deferred)
            RequestHandle(deferred, started = true)
        }

        if (request.started) {
            requestScope.launch {
                try {
                    val days = withContext(ioDispatcher) { dataSource.getCalendarDays() }
                    cacheMutex.withLock {
                        cachedCalendar = CachedCalendar(days = days, cachedAt = clock.now())
                        if (inFlight?.deferred === request.deferred) inFlight = null
                    }
                    request.deferred.complete(days)
                } catch (throwable: Throwable) {
                    val error = try {
                        wrapCalendarException(throwable)
                    } catch (cancelled: Throwable) {
                        cancelled
                    }
                    cacheMutex.withLock {
                        if (inFlight?.deferred === request.deferred) inFlight = null
                    }
                    request.deferred.completeExceptionally(error)
                }
            }
        }

        return request.deferred.await()
    }

    private fun wrapCalendarException(throwable: Throwable): RepositoryException {
        if (throwable is BangumiExploreRequestException) {
            return RepositoryRequestError(
                localizedMessage = "Bangumi calendar request failed.",
                message = throwable.message,
                cause = throwable,
            )
        }
        return RepositoryException.wrapOrThrowCancellation(throwable)
    }

    private data class CachedCalendar(
        val days: List<BangumiCalendarDay>,
        val cachedAt: Instant,
    )

    private data class InFlightRequest(
        val deferred: CompletableDeferred<List<BangumiCalendarDay>>,
    )

    private data class RequestHandle(
        val deferred: CompletableDeferred<List<BangumiCalendarDay>>,
        val started: Boolean,
    ) {
        companion object {
            fun completed(days: List<BangumiCalendarDay>) = RequestHandle(
                deferred = CompletableDeferred<List<BangumiCalendarDay>>().apply { complete(days) },
                started = false,
            )
        }
    }
}

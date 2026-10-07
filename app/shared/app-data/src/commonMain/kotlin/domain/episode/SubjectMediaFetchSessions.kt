package com.wynime.app.domain.episode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.domain.media.fetch.SubjectMediaFetchSessionRegistry
import com.wynime.app.domain.media.fetch.isFailedOrAbandoned
import com.wynime.datasources.api.source.MediaFetchRequest

class SubjectMediaFetchSessions(
    private val scope: CoroutineScope,
    private val sharedRegistry: SubjectMediaFetchSessionRegistry? = null,
    private val createSession: suspend (MediaFetchRequest) -> MediaFetchSession,
) : AutoCloseable {
    private val lock = Mutex()
    private var current: Pair<MediaFetchRequest, MediaFetchSession>? = null
    private var subscription: Job? = null

    suspend fun get(request: MediaFetchRequest): MediaFetchSession = lock.withLock {
        if (sharedRegistry != null) {
            val session = sharedRegistry.get(request)
            current = request to session
            return@withLock session
        }
        current?.let { (currentRequest, session) ->
            if (currentRequest.isSameSubjectQuery(request)) {
                retryFailedSources(session)
                return@withLock session
            }
        }
        val session = createSession(request)
        subscription?.cancel()
        subscription = scope.launch { session.cumulativeResults.collect() }
        current = request to session
        session
    }

    private fun retryFailedSources(session: MediaFetchSession) {
        for (result in session.mediaSourceResults) {
            if (result.state.value.isFailedOrAbandoned) {
                result.restart()
            }
        }
    }

    override fun close() {
        if (sharedRegistry != null) {
            current?.second?.let(sharedRegistry::release)
            current = null
            return
        }
        subscription?.cancel()
        subscription = null
        current = null
    }
}

package com.wynime.app.domain.media.fetch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaSource
import com.wynime.utils.coroutines.cancellableCoroutineScope

interface MediaFetchSession {

    val request: Flow<MediaFetchRequest>

    val latestRequest: Flow<MediaFetchRequest> get() = request

    val mediaSourceResults: List<MediaSourceFetchResult>

    val cumulativeResults: Flow<List<Media>>

    val hasCompleted: Flow<CompletedConditions>

    fun setFetchRequest(request: MediaFetchRequest)

    fun restartAll() {
        mediaSourceResults.forEach {
            it.restart()
        }
    }
}

fun MediaFetchSession.restart(instanceId: String) {
    mediaSourceResults.first { it.instanceId == instanceId }.restart()
}

suspend fun MediaFetchSession.awaitCompletion(
    onHasCompletedChanged: suspend (completedConditions: CompletedConditions) -> Boolean = { it.allCompleted() }
) {
    cancellableCoroutineScope {
        cumulativeResults.shareIn(this, started = SharingStarted.Eagerly, replay = 1)
        hasCompleted.first { onHasCompletedChanged(it) }
        cancelScope()
    }
}

suspend inline fun MediaFetchSession.awaitCompletedResults(): List<Media> {
    awaitCompletion()
    return cumulativeResults.first()
}

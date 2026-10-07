package com.wynime.app.domain.media.fetch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.coroutines.cancellableCoroutineScope

interface MediaSourceFetchResult {
    val instanceId: String
    val mediaSourceId: String
    val sourceInfo: MediaSourceInfo
    val kind: MediaSourceKind

    val state: StateFlow<MediaSourceFetchState>

    val results: Flow<List<Media>>

    val resultsIfEnabled
        get() = results

    fun restart()

    fun enable()
}

val MediaSourceFetchResult.hasCompletedOrDisabled: Flow<Boolean>
    get() = state.map { it is MediaSourceFetchState.Completed || it is MediaSourceFetchState.Disabled }

suspend fun MediaSourceFetchResult.awaitCompletion() {
    if (state.value is MediaSourceFetchState.Disabled) {
        return
    }
    cancellableCoroutineScope {
        resultsIfEnabled.shareIn(this, started = SharingStarted.Eagerly, replay = 1)
        hasCompletedOrDisabled.first { it }
        cancelScope()
    }
}

suspend inline fun MediaSourceFetchResult.awaitCompletedResults(): List<Media> {
    awaitCompletion()
    return resultsIfEnabled.first()
}

package com.wynime.app.domain.session

import com.wynime.app.data.repository.player.PlaybackHistorySyncer
import com.wynime.app.data.repository.subject.BangumiCollectionSyncer
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WynimeCloudSyncLifecycle(
    private val playback: PlaybackHistorySyncer,
    private val collections: BangumiCollectionSyncer,
) {
    private val flushMutex = Mutex()

    fun setForeground(active: Boolean) {
        playback.setForeground(active)
        collections.setForeground(active)
    }

    fun requestCollectionPull() {
        collections.requestSync()
    }

    suspend fun flushPending() {
        flushMutex.withLock {
            try {
                playback.saveCurrentProgress()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to save playback progress before leaving the app" }
            }
            coroutineScope {
                launch { playback.flushPending() }
                launch { collections.flushPending() }
            }
        }
    }

    private companion object {
        val logger = logger<WynimeCloudSyncLifecycle>()
    }
}

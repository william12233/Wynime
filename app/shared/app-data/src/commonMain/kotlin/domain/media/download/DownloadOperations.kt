package com.wynime.app.domain.media.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wynime.app.domain.media.cache.DeleteCacheUseCase
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn

class DownloadOperations(
    private val downloadManager: MediaDownloadManager,
    private val deleteCache: DeleteCacheUseCase,
    private val executionScope: CoroutineScope,
) {
    private val serial = Mutex()

    fun submit(ids: Set<String>, operation: DownloadOperation): Deferred<Map<String, Throwable>> {
        val queued = ArrayDeque(ids.mapNotNull { id -> downloadManager.findDownload(id)?.takeIf { it.claim(operation) } })
        if (queued.isEmpty()) return CompletableDeferred(emptyMap())
        val result = CompletableDeferred<Map<String, Throwable>>()
        val job = executionScope.launch {
            val failures = HashMap<String, Throwable>()
            serial.withLock {
                while (true) {
                    val download = queued.removeFirstOrNull() ?: break
                    try {
                        execute(download, operation)
                    } catch (e: CancellationException) {

                        currentCoroutineContext().ensureActive()
                        failures[download.id] = e
                    } catch (e: Exception) {
                        logger.warn(e) { "Download operation $operation failed for ${download.id}" }
                        failures[download.id] = e
                    } finally {
                        download.release()
                    }
                }
            }
            result.complete(failures)
        }
        job.invokeOnCompletion {

            queued.forEach { it.release() }
            result.cancel()
        }
        return result
    }

    private suspend fun execute(download: MediaDownload, operation: DownloadOperation) {
        when (operation) {
            DownloadOperation.Pause -> download.pause()
            DownloadOperation.Resume -> download.resume()
            DownloadOperation.Delete -> deleteCache(download.cache)
        }
    }

    private companion object {
        private val logger = logger<DownloadOperations>()
    }
}

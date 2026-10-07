package com.wynime.app.domain.media.cache.engine

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import com.wynime.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.httpdownloader.DownloadId
import com.wynime.utils.httpdownloader.DownloadState
import com.wynime.utils.httpdownloader.DownloadStatus
import com.wynime.utils.httpdownloader.KtorHttpDownloader
import com.wynime.utils.httpdownloader.m3u.DefaultM3u8Parser
import com.wynime.utils.httpdownloader.m3u.M3u8Parser
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

class KtorPersistentHttpDownloader(
    private val dao: HttpCacheDownloadStateDao,
    client: ScopedHttpClient,
    fileSystem: FileSystem,
    baseSaveDir: Path,
    ioDispatcher: CoroutineContext = Dispatchers.IO_,
    clock: Clock = Clock.System,
    m3u8Parser: M3u8Parser = DefaultM3u8Parser,
    scope: CoroutineScope,
) : KtorHttpDownloader(
    client = client,
    fileSystem = fileSystem,
    baseSaveDir = baseSaveDir,
    clock = clock,
    m3u8Parser = m3u8Parser,
    parentScope = scope,
    ioDispatcher = ioDispatcher,
) {
    override suspend fun init() {
        super.init()
        restoreStates()
    }

    private suspend fun restoreStates() {
        val savedList: List<DownloadState> = dao.getAll().first()
        stateMutex.withLock {
            val currentMap: MutableMap<DownloadId, DownloadEntry> = LinkedHashMap(savedList.size)

            savedList.forEach { st ->
                currentMap[st.downloadId] = DownloadEntry(
                    job = null,
                    state = st.copy(
                        status = when (val status = st.status) {

                            DownloadStatus.INITIALIZING,
                            DownloadStatus.DOWNLOADING,
                            DownloadStatus.MERGING -> DownloadStatus.PAUSED

                            DownloadStatus.PAUSED,
                            DownloadStatus.COMPLETED,
                            DownloadStatus.FAILED,
                            DownloadStatus.CANCELED -> status
                        },
                    ),
                )
            }
            _downloadStatesFlow.value = currentMap.toPersistentMap()
            logger.info { "Restored ${currentMap.size} downloads from DataStore" }
        }
    }

    override fun onCreateDownloadState(state: DownloadState) {
        scope.launch {
            dao.upsert(state)
        }
    }

    override fun onUpdateDownloadState(downloadId: DownloadId, state: DownloadState) {
        scope.launch {
            dao.upsert(state)
        }
    }

    override fun onUpdateDownloadStatus(downloadId: DownloadId, status: DownloadStatus) {
        scope.launch {
            dao.updateStatus(downloadId, status)
        }
    }

    override fun onRemoveAllDownloads() {
        scope.launch {
            dao.deleteAll()
        }
    }

    override fun onRemoveDownload(downloadId: DownloadId) {
        scope.launch {
            dao.deleteById(downloadId)
        }
    }

    private companion object {
        private val logger = logger<KtorPersistentHttpDownloader>()
    }
}

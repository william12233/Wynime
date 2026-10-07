package com.wynime.utils.httpdownloader

import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.coroutines.update
import com.wynime.utils.httpdownloader.DownloadStatus.CANCELED
import com.wynime.utils.httpdownloader.DownloadStatus.COMPLETED
import com.wynime.utils.httpdownloader.DownloadStatus.DOWNLOADING
import com.wynime.utils.httpdownloader.DownloadStatus.FAILED
import com.wynime.utils.httpdownloader.DownloadStatus.INITIALIZING
import com.wynime.utils.httpdownloader.DownloadStatus.MERGING
import com.wynime.utils.httpdownloader.DownloadStatus.PAUSED
import com.wynime.utils.httpdownloader.m3u.DefaultM3u8Parser
import com.wynime.utils.httpdownloader.m3u.M3u8Parser
import com.wynime.utils.httpdownloader.m3u.M3u8Playlist
import com.wynime.utils.httpdownloader.m3u.MediaSegmentEncryption
import com.wynime.utils.httpdownloader.m3u.ResolvedMediaPlaylist
import com.wynime.utils.httpdownloader.m3u.export
import com.wynime.utils.httpdownloader.m3u.parseResolvedMediaPlaylist
import com.wynime.utils.io.DEFAULT_BUFFER_SIZE
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.copyTo
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.length
import com.wynime.utils.io.resolve
import com.wynime.utils.io.writeText
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.trace
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.Uuid
import org.openani.mediamp.ffmpeg.FFmpegKit
import org.openani.mediamp.ffmpeg.FFmpegResult
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

open class KtorHttpDownloader(
    protected val client: ScopedHttpClient,
    private val fileSystem: FileSystem,
    private val baseSaveDir: Path,
    val clock: Clock = Clock.System,
    private val m3u8Parser: M3u8Parser = DefaultM3u8Parser,
    parentScope: CoroutineScope,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : HttpDownloader {

    protected val scope = parentScope.childScope()

    private val _progressFlow = MutableSharedFlow<DownloadProgress>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val progressFlow: Flow<DownloadProgress> = _progressFlow.asSharedFlow()

    private val segmentFailures = MutableStateFlow(persistentMapOf<DownloadId, SegmentFailure>())

    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> {
        return combine(downloadStatesFlow, segmentFailures) { states, failures ->
            states.firstOrNull { it.downloadId == downloadId }?.let { createProgress(it, failures[downloadId]) }
        }
            .filterNotNull()
            .distinctUntilChanged()
    }

    protected val _downloadStatesFlow = MutableStateFlow(persistentMapOf<DownloadId, DownloadEntry>())

    override val downloadStatesFlow: Flow<List<DownloadState>> =
        _downloadStatesFlow.map { it.values.map { entry -> entry.state } }

    private val ffmpegKit by lazy { FFmpegKit() }
    private var ffmpegLogHandlerSet = false
    private val ffmpegLogHandlerLock = Mutex()

    override suspend fun init() {

        logger.info { "KtorHttpDownloader initialized." }
    }

    protected val stateMutex = Mutex()

    override suspend fun download(
        url: String,
        options: DownloadOptions,
    ): DownloadId {
        val downloadId = DownloadId(value = Uuid.randomString())
        return downloadWithId(downloadId, url, options)?.downloadId ?: downloadId
    }

    protected fun getMediaTypeFromUrl(url: String): MediaType? {
        val parsed = io.ktor.http.Url(url)
        val path = parsed.encodedPath
        fun guessFromValue(value: String): MediaType? {
            return when {
                value.endsWith(".m3u8", ignoreCase = true) -> MediaType.M3U8
                value.endsWith(".mp4", ignoreCase = true) -> MediaType.MP4
                value.endsWith(".mkv", ignoreCase = true) -> MediaType.MKV
                else -> null
            }
        }
        return guessFromValue(path)
            ?: guessFromValue(url)
    }

    override suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        options: DownloadOptions,
    ): DownloadState? {
        val mediaType = getMediaTypeFromUrl(url) ?: MediaType.MP4
        logger.info { "Preparing to download with id=$downloadId, url=$url, mediaType=$mediaType" }

        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val existingEntry = currentMap[downloadId]
            if (existingEntry != null) {

                logger.info { "Existing completed download found for $downloadId, ignoring." }
                return existingEntry.state
            }
            val segmentCacheDir = ("segments_" + downloadId.value)
                .also { fileSystem.createDirectories(baseSaveDir.resolve(it)) }
            val initialState = DownloadState(
                downloadId = downloadId,
                url = url,
                relativeOutputPath = downloadId.value + mediaType.outputFileExtension,
                segments = emptyList(),
                totalSegments = 0,
                downloadedBytes = 0L,
                timestamp = clock.now().toEpochMilliseconds(),
                status = INITIALIZING,
                relativeSegmentCacheDir = segmentCacheDir,
                mediaType = mediaType,
                requestHeaders = options.headers,
            )
            _downloadStatesFlow.update {
                put(downloadId, DownloadEntry(job = null, state = initialState))
            }
            onCreateDownloadState(initialState)
            logger.info { "Created initial state for $downloadId" }
        }
        emitProgress(downloadId)

        if (!createSegments(downloadId, url, mediaType, options)) {

            return null
        }

        updateState(downloadId) {
            it.copy(status = DOWNLOADING)
        }
        emitProgress(downloadId)

        logger.info { "Launching download job for $downloadId" }
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                logger.info { "Downloading segments for $downloadId" }
                downloadSegments(downloadId, options)

                clearSegmentFailure(downloadId)
                updateState(downloadId) {
                    it.copy(status = MERGING, timestamp = clock.now().toEpochMilliseconds())
                }
                emitProgress(downloadId)
                logger.info { "Merging segments for $downloadId" }

                mergeSegments(downloadId)

                updateState(downloadId) {
                    it.copy(status = COMPLETED, timestamp = clock.now().toEpochMilliseconds())
                }
                emitProgress(downloadId)
                logger.info { "Download completed for $downloadId" }
            } catch (e: CancellationException) {
                logger.info { "Download cancelled for $downloadId" }
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Download failed for $downloadId" }
                updateState(downloadId) {
                    it.copy(
                        status = FAILED,
                        error = DownloadError(
                            code = if (e is M3u8Exception) e.errorCode else DownloadErrorCode.UNEXPECTED_ERROR,
                            technicalMessage = e.toString(),
                        ),
                        timestamp = clock.now().toEpochMilliseconds(),
                    )
                }
                clearSegmentFailure(downloadId)
                emitProgress(downloadId)
            }
        }

        return stateMutex.withLock {
            val entry = _downloadStatesFlow.value[downloadId]
                ?: error("Job of new download request $downloadId is created, but the download state is not found.")

            _downloadStatesFlow.update { put(downloadId, entry.copy(job = job)) }

            entry.state
        }
    }

    override suspend fun resume(downloadId: DownloadId): Boolean {
        val st = getState(downloadId) ?: return false
        if (st.status != PAUSED && st.status != FAILED) {
            if (st.status != COMPLETED) {
                logger.info { "Cannot resume $downloadId because status=${st.status}" }
            }
            return false
        }

        val alreadyActive = stateMutex.withLock {
            val existingEntry = _downloadStatesFlow.value[downloadId]
            existingEntry?.job?.isActive == true
        }
        if (alreadyActive) {
            logger.info { "Attempting to resume $downloadId but there's already an active job." }
            emitProgress(downloadId)
            return true
        }

        logger.info { "Resuming $downloadId with status=${st.status}" }

        if (st.segments.isEmpty()) {
            if (!createSegments(downloadId, st.url, st.mediaType, DownloadOptions(headers = st.requestHeaders))) {
                return false
            }
        }

        updateState(downloadId) {
            it.copy(status = DOWNLOADING)
        }
        emitProgress(downloadId)

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                logger.info { "Resumed: downloading segments for $downloadId" }
                downloadSegments(downloadId, DownloadOptions(headers = st.requestHeaders))

                updateState(downloadId) {
                    it.copy(status = MERGING)
                }
                emitProgress(downloadId)
                logger.info { "Merging segments for resumed $downloadId" }
                mergeSegments(downloadId)

                updateState(downloadId) {
                    it.copy(status = COMPLETED)
                }
                emitProgress(downloadId)
                logger.info { "Download completed after resume for $downloadId" }
            } catch (e: CancellationException) {
                logger.info { "Download cancelled while resumed for $downloadId" }
                throw e
            } catch (t: Throwable) {
                logger.info { "Resumed download failed for $downloadId: ${t.message}" }
                updateState(downloadId) {
                    it.copy(
                        status = FAILED,
                        error = DownloadError(DownloadErrorCode.UNEXPECTED_ERROR, technicalMessage = t.message),
                    )
                }
                emitProgress(downloadId)
            }
        }

        stateMutex.withLock {
            val entry = _downloadStatesFlow.value[downloadId] ?: return@withLock
            _downloadStatesFlow.update { put(downloadId, entry.copy(job = job)) }
        }
        return true
    }

    override suspend fun getActiveDownloadIds(): List<DownloadId> {
        return stateMutex.withLock {
            _downloadStatesFlow.value.values
                .filter { it.state.status == DOWNLOADING || it.state.status == INITIALIZING }
                .map { it.state.downloadId }
        }
    }

    override suspend fun pause(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val entry = _downloadStatesFlow.value[downloadId] ?: return false

            if (entry.state.status == COMPLETED || entry.state.status == CANCELED) {
                logger.info { "Cannot pause $downloadId because it is already ${entry.state.status}." }
                return false
            }
            val job = entry.job
            if (job != null) {
                if (!job.isActive) {
                    logger.info { "Cannot pause $downloadId because job is not active." }
                    return false
                }
                logger.info { "Pausing download $downloadId" }
                job.cancel()
            }

            val oldState = entry.state
            _downloadStatesFlow.update {
                put(
                    downloadId,
                    entry.copy(
                        job = null,
                        state = oldState.copy(status = PAUSED),
                    ),
                )
            }
            onUpdateDownloadStatus(downloadId, PAUSED)
        }
        clearSegmentFailure(downloadId)
        emitProgress(downloadId)
        return true
    }

    override suspend fun pauseAll(): List<DownloadId> {
        val paused = mutableListOf<DownloadId>()
        stateMutex.withLock {
            _downloadStatesFlow.update {
                mutate { map ->
                    val entriesToUpdate = map.entries.toList()
                    entriesToUpdate.forEach { (id, entry) ->
                        val job = entry.job
                        if (job != null && job.isActive) {
                            logger.info { "Pausing download $id" }
                            job.cancel()
                            map[id] = entry.copy(
                                job = null,
                                state = entry.state.copy(status = PAUSED),
                            )
                            paused.add(id)
                        }
                    }
                }
            }
        }
        paused.forEach {
            onUpdateDownloadStatus(it, PAUSED)
            emitProgress(it)
        }
        return paused
    }

    override suspend fun cancel(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val entry = _downloadStatesFlow.value[downloadId] ?: return false
            val job = entry.job
            if (job != null && job.isActive) {
                logger.info { "Cancelling download $downloadId" }
                job.cancel()
            }
            _downloadStatesFlow.update {
                put(
                    downloadId,
                    entry.copy(
                        job = null,
                        state = entry.state.copy(status = CANCELED),
                    ),
                )
            }
        }
        clearSegmentFailure(downloadId)
        onUpdateDownloadStatus(downloadId, CANCELED)
        emitProgress(downloadId)
        return true
    }

    override suspend fun cancelAll() {
        logger.info { "Cancelling all downloads." }
        stateMutex.withLock {
            _downloadStatesFlow.update {
                mutate { map ->

                    val entriesToUpdate = map.entries.toList()

                    entriesToUpdate.forEach { (id, entry) ->
                        if (entry.job?.isActive == true) {
                            logger.info { "Cancelling download $id" }
                            entry.job.cancel()
                        }
                        val st = entry.state
                        if (st.status in listOf(INITIALIZING, DOWNLOADING, PAUSED, MERGING)) {
                            map[id] = entry.copy(
                                job = null,
                                state = st.copy(status = CANCELED),
                            )
                        }
                    }
                }
            }
        }
        val allIds = stateMutex.withLock { _downloadStatesFlow.value.keys.toList() }
        allIds.forEach {
            clearSegmentFailure(it)
            onUpdateDownloadStatus(it, CANCELED)
            emitProgress(it)
        }
    }

    override suspend fun remove(downloadId: DownloadId): Boolean {
        val removedState = stateMutex.withLock {
            val entry = _downloadStatesFlow.value[downloadId] ?: return false
            if (entry.job?.isActive == true) {
                logger.info { "Removing download $downloadId and cancelling active job first" }
                entry.job.cancel()
            }
            entry.state
        }

        val job = stateMutex.withLock { _downloadStatesFlow.value[downloadId]?.job }
        if (job != null) {
            try {
                job.join()
            } catch (_: CancellationException) {
            }
        }

        stateMutex.withLock {
            val existing = _downloadStatesFlow.value[downloadId] ?: return@withLock
            _downloadStatesFlow.update { remove(downloadId) }
            if (existing.job?.isActive == true) {
                existing.job.cancel()
            }
        }

        clearSegmentFailure(downloadId)
        deleteDownloadArtifacts(removedState)
        onRemoveDownload(downloadId)
        return true
    }

    override suspend fun getState(downloadId: DownloadId): DownloadState? {
        return stateMutex.withLock {
            _downloadStatesFlow.value[downloadId]?.state
        }
    }

    override suspend fun getAllStates(): List<DownloadState> {
        return stateMutex.withLock {
            _downloadStatesFlow.value.values.map { it.state }
        }
    }

    override fun close() {
        logger.info { "Closing KtorHttpDownloader." }
        scope.launch(NonCancellable + CoroutineName("M3u8Downloader.close")) {
            closeSuspend()
        }
    }

    suspend fun closeSuspend() {
        logger.info { "closeSuspend() called; joining all active jobs." }
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value
            currentMap.forEach { (_, entry) ->
                if (entry.job?.isActive == true) {
                    entry.job.cancelAndJoin()
                }
            }
            _downloadStatesFlow.update { clear() }
            segmentFailures.update { clear() }
            onRemoveAllDownloads()
        }
        scope.cancel()
        logger.info { "KtorHttpDownloader closed." }
    }

    protected data class DownloadEntry(val job: Job?, val state: DownloadState)

    private suspend fun createSegments(
        downloadId: DownloadId,
        url: String,
        mediaType: MediaType,
        options: DownloadOptions,
    ): Boolean {
        try {
            val newSegments = when (mediaType) {
                MediaType.M3U8 -> {
                    logger.info { "Resolving M3U8 media playlist for $downloadId" }
                    val resolvedPlaylist = resolveM3u8MediaPlaylist(url, options)

                    val segmentCacheDir = getState(downloadId)?.relativeSegmentCacheDir ?: return false
                    persistResolvedMediaPlaylist(baseSaveDir.resolve(segmentCacheDir), resolvedPlaylist)
                    resolvedPlaylist.playlist.toSegments { Path(segmentCacheDir).resolve(it).toString() }
                }

                MediaType.MP4, MediaType.MKV -> {
                    logger.info { "Creating range segments for $downloadId" }
                    createRangeSegments(downloadId, url, options)
                }
            }

            updateState(downloadId) {
                it.copy(
                    segments = newSegments,
                    totalSegments = newSegments.size,
                )
            }
            emitProgress(downloadId)
            logger.info { "Created ${newSegments.size} segments for $downloadId" }
            return true
        } catch (e: Throwable) {
            handleSegmentCreationFailure(downloadId, e)
            return false
        }
    }

    private suspend fun handleSegmentCreationFailure(downloadId: DownloadId, e: Throwable) {
        logger.error(e) { "Segment creation failed for $downloadId: ${e.message}" }
        updateState(downloadId) {
            it.copy(
                status = FAILED,
                error = DownloadError(
                    code = if (e is M3u8Exception) e.errorCode else DownloadErrorCode.UNEXPECTED_ERROR,
                    technicalMessage = e.message,
                ),
                timestamp = clock.now().toEpochMilliseconds(),
            )
        }
        emitProgress(downloadId)
    }

    private suspend fun resolveM3u8MediaPlaylist(
        url: String,
        options: DownloadOptions,
        depth: Int = 0,
    ): ResolvedMediaPlaylist {
        if (depth >= 5) {
            throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
        }
        logger.info { "Resolving M3U8 playlist from $url (depth=$depth)" }
        val response = httpGet(url, options) { it.body<String>() }
        return when (val playlist = m3u8Parser.parse(response, url)) {
            is M3u8Playlist.MasterPlaylist -> {
                val bestVariant = playlist.variants.maxByOrNull { it.bandwidth }
                    ?: throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
                logger.info { "Master playlist found; picking highest bandwidth variant=${bestVariant.bandwidth}" }
                resolveM3u8MediaPlaylist(bestVariant.uri, options, depth + 1)
            }

            is M3u8Playlist.MediaPlaylist -> {
                logger.info { "Media playlist resolved at depth=$depth for $url" }
                m3u8Parser.parseResolvedMediaPlaylist(response, url)
            }
        }
    }

    private suspend fun persistResolvedMediaPlaylist(cacheDir: Path, resolvedPlaylist: ResolvedMediaPlaylist) {
        withContext(ioDispatcher) {
            fileSystem.createDirectories(cacheDir)
        }
        cacheDir.resolve(UPSTREAM_PLAYLIST_FILE_NAME).inSystem.writeText(resolvedPlaylist.rawContent)
        cacheDir.resolve(UPSTREAM_PLAYLIST_URL_FILE_NAME).inSystem.writeText(resolvedPlaylist.sourceUrl)
    }

    private suspend fun downloadHlsEncryptionKeys(
        snapshot: DownloadState,
        requestOptions: DownloadOptions,
    ) {
        val cacheDir = baseSaveDir.resolve(snapshot.relativeSegmentCacheDir)
        val keyPaths = snapshot.buildHlsKeyRelativePaths()
        snapshot.orderedUniqueEncryptionKeys().forEach { encryption ->
            val relativeKeyPath = keyPaths[encryption.keyUri]
                ?: error("No local key path found for ${encryption.keyUri}")
            val keyPath = cacheDir.resolve(relativeKeyPath)
            if (keyPath.inSystem.exists()) {
                return@forEach
            }

            logger.info { "Downloading HLS key for ${snapshot.downloadId}: ${encryption.keyUri}" }
            val keyBytes = withRetry(
                maxRetries = requestOptions.maxRetriesPerSegment,
                baseDelayMillis = requestOptions.baseRetryDelayMillis,
                onFailure = { attempt, error ->
                    recordSegmentFailure(snapshot.downloadId, null, attempt, requestOptions.maxRetriesPerSegment, error)
                },
            ) {
                httpGet(encryption.keyUri, requestOptions) { it.body<ByteArray>() }
            }
            writeBytesToFile(keyPath, keyBytes)
        }
    }

    private suspend fun createLocalHlsPlaylist(st: DownloadState, cacheDir: Path): Path {
        val upstreamPlaylistPath = cacheDir.resolve(UPSTREAM_PLAYLIST_FILE_NAME)
        val upstreamPlaylistUrlPath = cacheDir.resolve(UPSTREAM_PLAYLIST_URL_FILE_NAME)
        val upstreamContent = readTextFromFile(upstreamPlaylistPath)
        val upstreamSourceUrl = readTextFromFile(upstreamPlaylistUrlPath).trim()
        val sortedSegments = st.segments.sortedBy { it.index }
        val keyPaths = st.buildHlsKeyRelativePaths()
        val resolvedPlaylist = m3u8Parser.parseResolvedMediaPlaylist(upstreamContent, upstreamSourceUrl)
        val localPlaylistContent = resolvedPlaylist.export(
            resolveSegmentUri = { _, index ->
                sortedSegments.getOrNull(index)?.localPlaylistPath()
                    ?: throw IllegalStateException(
                        "No segment found for index=$index while exporting local HLS for ${st.downloadId}",
                    )
            },
            resolveKeyUri = { encryption ->
                keyPaths[encryption.uri]
                    ?: throw IllegalStateException(
                        "No local key path found for ${encryption.uri} while exporting local HLS for ${st.downloadId}",
                    )
            },
        )

        val localPlaylistPath = cacheDir.resolve(LOCAL_PLAYLIST_FILE_NAME)
        localPlaylistPath.inSystem.writeText(localPlaylistContent)
        return localPlaylistPath
    }

    private suspend fun cleanupHlsArtifacts(st: DownloadState, cacheDir: Path) {
        st.buildHlsKeyRelativePaths().values.forEach { relativeKeyPath ->
            deleteIfExists(cacheDir.resolve(relativeKeyPath))
        }
        deleteIfExists(cacheDir.resolve("keys"))
        deleteIfExists(cacheDir.resolve(UPSTREAM_PLAYLIST_FILE_NAME))
        deleteIfExists(cacheDir.resolve(UPSTREAM_PLAYLIST_URL_FILE_NAME))
        deleteIfExists(cacheDir.resolve(LOCAL_PLAYLIST_FILE_NAME))
    }

    private suspend fun readTextFromFile(filePath: Path): String {
        return withContext(ioDispatcher) {
            fileSystem.source(filePath).buffered().use { source ->
                source.readByteArray().decodeToString()
            }
        }
    }

    private fun deleteIfExists(path: Path) {
        if (path.inSystem.exists()) {
            fileSystem.delete(path)
        }
    }

    protected suspend fun updateState(downloadId: DownloadId, transform: (DownloadState) -> DownloadState) {
        var updatedState: DownloadState? = null
        stateMutex.withLock {
            val entry = _downloadStatesFlow.value[downloadId] ?: return
            _downloadStatesFlow.update {
                val newState = transform(entry.state)
                updatedState = newState
                put(downloadId, entry.copy(state = newState))
            }
        }
        updatedState?.let { onUpdateDownloadState(downloadId, it) }
    }

    @OptIn(ExperimentalAtomicApi::class)
    protected suspend fun createRangeSegments(
        downloadId: DownloadId,
        url: String,
        options: DownloadOptions,
    ): List<SegmentInfo> {
        val cacheDir = baseSaveDir.resolve(
            getState(downloadId)?.relativeSegmentCacheDir ?: error("Cache dir not found"),
        )

        val rangeProbe = probeRangeSupport(url, options)

        val (contentLength, rangeSupported) = rangeProbe
            ?: return listOf(
                SegmentInfo(
                    index = 0,
                    url = url,
                    isDownloaded = false,
                    byteSize = -1,
                    relativeTempFilePath = cacheDir.resolve("0.part").inSystem.absolutePath
                        .substringAfter(baseSaveDir.inSystem.absolutePath),
                    rangeStart = null,
                    rangeEnd = null,
                ),
            )

        if (!rangeSupported) {
            logger.info { "Range not supported for $downloadId, creating single segment." }
            return listOf(
                SegmentInfo(
                    index = 0,
                    url = url,
                    isDownloaded = false,
                    byteSize = contentLength,
                    relativeTempFilePath = cacheDir.resolve("0.part").inSystem.absolutePath
                        .substringAfter(baseSaveDir.inSystem.absolutePath),
                    rangeStart = null,
                    rangeEnd = null,
                ),
            )
        }

        logger.info { "Range supported for $downloadId, total file size: $contentLength" }
        val segmentSize = 5 * 1024 * 1024L
        if (contentLength <= segmentSize) {
            logger.info { "File is smaller than $segmentSize for $downloadId, single segment." }
            return listOf(
                SegmentInfo(
                    index = 0,
                    url = url,
                    isDownloaded = false,
                    byteSize = contentLength,
                    relativeTempFilePath = cacheDir.resolve("0.part").inSystem.absolutePath
                        .substringAfter(baseSaveDir.inSystem.absolutePath),
                    rangeStart = 0,
                    rangeEnd = contentLength - 1,
                ),
            )
        }

        logger.info { "Splitting file into segments of size=$segmentSize for $downloadId" }
        val segments = mutableListOf<SegmentInfo>()
        var start = 0L
        var index = 0
        while (start < contentLength) {
            val end = (start + segmentSize - 1).coerceAtMost(contentLength - 1)
            segments.add(
                SegmentInfo(
                    index = index,
                    url = url,
                    isDownloaded = false,
                    byteSize = (end - start + 1),
                    relativeTempFilePath = cacheDir.resolve("$index.part").inSystem.absolutePath
                        .substringAfter(baseSaveDir.inSystem.absolutePath),
                    rangeStart = start,
                    rangeEnd = end,
                ),
            )
            start = end + 1
            index++
        }

        logger.info { "Created ${segments.size} range segments for $downloadId" }
        return segments
    }

    private suspend fun probeRangeSupport(
        url: String,
        options: DownloadOptions,
    ): Pair<Long, Boolean>? {
        val rangeOptions = options.copy(
            headers = options.headers + ("Range" to "bytes=0-0"),
        )
        return try {
            client.use {
                prepareGet(url) {
                    rangeOptions.headers.forEach { (k, v) -> header(k, v) }
                }.execute { response ->
                    when (response.status.value) {
                        206 -> {
                            val contentRange =
                                response.headers[io.ktor.http.HttpHeaders.ContentRange] ?: return@execute null
                            val totalSize = contentRange.substringAfter('/').toLongOrNull() ?: return@execute null
                            totalSize to true
                        }

                        200 -> {
                            val length = response.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                            length to false
                        }

                        else -> null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    protected suspend fun downloadSingleSegment(
        segmentInfo: SegmentInfo,
        options: DownloadOptions,
    ): Long {

        val finalOptions = if (segmentInfo.rangeStart != null && segmentInfo.rangeEnd != null) {
            options.copy(
                headers = options.headers + ("Range" to "bytes=${segmentInfo.rangeStart}-${segmentInfo.rangeEnd}"),
            )
        } else {
            options
        }
        logger.info { "Downloading segment index=${segmentInfo.index}, range=(${segmentInfo.rangeStart}-${segmentInfo.rangeEnd})" }

        return httpGet(segmentInfo.url, finalOptions) { statement ->

            statement.execute { response ->
                val segmentPath = baseSaveDir.resolve(segmentInfo.relativeTempFilePath)
                withContext(ioDispatcher) {
                    fileSystem.createDirectories(
                        segmentPath.parent ?: error("Parent dir not found for segmentInfo: $segmentInfo"),
                    )
                }

                val channel = response.bodyAsChannel()
                val byteSize = copyChannelToFile(channel, segmentPath)
                byteSize.also {
                    logger.info { "Segment index=${segmentInfo.index} downloaded, size=$it" }
                }
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun copyChannelToFile(
        channel: ByteReadChannel,
        filePath: Path,
    ): Long {
        val totalBytes = AtomicLong(0L)
        fileSystem.sink(filePath).buffered().use { sink ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            withContext(ioDispatcher) {
                while (true) {
                    val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                    if (bytesRead == -1) break
                    sink.write(buffer, startIndex = 0, endIndex = bytesRead)
                    totalBytes.fetchAndAdd(bytesRead.toLong())
                }
            }
        }
        return totalBytes.load()
    }

    private suspend fun writeBytesToFile(filePath: Path, data: ByteArray) {
        withContext(ioDispatcher) {
            fileSystem.createDirectories(
                filePath.parent ?: error("Parent dir not found for filePath: $filePath"),
            )
            fileSystem.sink(filePath).buffered().use { sink ->
                sink.write(data, startIndex = 0, endIndex = data.size)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalAtomicApi::class)
    protected suspend fun downloadSegments(downloadId: DownloadId, options: DownloadOptions) {
        val snapshot = getState(downloadId) ?: return
        if (snapshot.segments.isEmpty()) {
            logger.info { "No segments to download for $downloadId" }
            return
        }
        val requestOptions = options.copy(headers = snapshot.requestHeaders + options.headers)
        logger.info { "Downloading ${snapshot.segments.size} segments for $downloadId with concurrency=${options.maxConcurrentSegments}" }
        val semaphore = Semaphore(options.maxConcurrentSegments)

        if (snapshot.mediaType == MediaType.M3U8) {
            downloadHlsEncryptionKeys(snapshot, requestOptions)
        }

        coroutineScope {
            snapshot.segments.forEach { seg ->
                if (seg.isDownloaded) return@forEach
                semaphore.acquire()
                launch(ioDispatcher, start = CoroutineStart.ATOMIC) {
                    try {

                        val newSize = withRetry(
                            maxRetries = options.maxRetriesPerSegment,
                            baseDelayMillis = options.baseRetryDelayMillis,
                            onFailure = { attempt, error ->
                                recordSegmentFailure(downloadId, seg.index, attempt, options.maxRetriesPerSegment, error)
                            },
                        ) {
                            downloadSingleSegment(seg, requestOptions)
                        }
                        markSegmentDownloaded(downloadId, seg.index, newSize)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
        logger.info { "All segments downloaded for $downloadId" }
    }

    protected suspend fun markSegmentDownloaded(downloadId: DownloadId, segmentIndex: Int, byteSize: Long) {
        logger.info { "Segment index=$segmentIndex fully downloaded for $downloadId, size=$byteSize" }
        updateState(downloadId) { old ->
            val updatedSegments = old.segments.map {
                if (it.index == segmentIndex) it.copy(isDownloaded = true, byteSize = byteSize) else it
            }
            old.copy(downloadedBytes = old.downloadedBytes + byteSize, segments = updatedSegments)
        }
        clearSegmentFailure(downloadId)
        emitProgress(downloadId)
    }

    protected suspend fun mergeSegments(downloadId: DownloadId) = withContext(ioDispatcher) {
        val st = getState(downloadId) ?: return@withContext
        val cacheDir = baseSaveDir.resolve(st.relativeSegmentCacheDir)
        val finalOutputRelativePath = st.finalOutputRelativePath()
        val finalOutput = baseSaveDir.resolve(finalOutputRelativePath)

        when (st.mediaType) {
            MediaType.M3U8 -> mergeM3u8Segments(st, cacheDir, finalOutput)
            MediaType.MP4,
            MediaType.MKV -> concatenateSegments(st, finalOutput)
        }

        if (finalOutputRelativePath != st.relativeOutputPath) {
            updateState(downloadId) {
                it.copy(relativeOutputPath = finalOutputRelativePath)
            }
        }

        st.segments.forEach { seg ->
            fileSystem.delete(baseSaveDir.resolve(seg.relativeTempFilePath))
        }
        if (st.mediaType == MediaType.M3U8) {
            cleanupHlsArtifacts(st, cacheDir)
        }

        fileSystem.delete(cacheDir)
        logger.info { "Segments merged into $finalOutput, removed cache dir=$cacheDir for $downloadId" }
    }

    protected fun concatenateSegments(st: DownloadState, finalOutput: Path) {
        fileSystem.sink(finalOutput).buffered().use { out ->
            st.segments.sortedBy { it.index }.forEach { seg ->
                fileSystem.source(baseSaveDir.resolve(seg.relativeTempFilePath)).buffered().use { input ->
                    input.copyTo(out)
                }
            }
        }
    }

    open suspend fun mergeM3u8Segments(st: DownloadState, cacheDir: Path, finalOutput: Path) {
        setFFmpegKitLogHandler()
        val localPlaylistFile = createLocalHlsPlaylist(st, cacheDir).inSystem
        val ffmpegArgs = listOf(
            "-y", "-nostdin",
            "-allowed_extensions", "ALL",
            "-protocol_whitelist", "file,crypto,data",
            "-i", localPlaylistFile.absolutePath,
            "-map", "0",
            "-c", "copy",
            "-bsf:a", "aac_adtstoasc",
            "-movflags",
            "+faststart",
            finalOutput.inSystem.absolutePath,
        )
        logger.info {
            "Running FFmpeg merge for ${st.downloadId}: ffmpeg ${ffmpegArgs.joinToString(" ") { it.quoteForLog() }}"
        }

        if (finalOutput.inSystem.exists()) {
            fileSystem.delete(finalOutput)
        }

        val result = executeFfmpeg(ffmpegArgs)
        if (!result.isSuccess) {
            if (finalOutput.inSystem.exists()) {
                fileSystem.delete(finalOutput)
            }
            throw IllegalStateException(
                "FFmpeg failed to merge HLS segments for ${st.downloadId}: exitCode=${result.exitCode}",
            )
        }

        val outputFile = finalOutput.inSystem
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            if (outputFile.exists()) {
                fileSystem.delete(finalOutput)
            }
            throw IllegalStateException("FFmpeg failed to merge HLS segments for ${st.downloadId}: output file was not created")
        }
    }

    protected open suspend fun executeFfmpeg(args: List<String>): FFmpegResult {
        return ffmpegKit.execute(args)
    }

    private fun String.quoteForLog(): String =
        if (isEmpty() || any { it.isWhitespace() || it == '"' || it == '\'' }) {
            "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            this
        }

    private fun DownloadState.finalOutputRelativePath(): String {
        if (mediaType != MediaType.M3U8) return relativeOutputPath
        return relativeOutputPath.replaceAfterLast('.', "mp4", missingDelimiterValue = "$relativeOutputPath.mp4")
    }

    private suspend fun setFFmpegKitLogHandler() {
        if (ffmpegLogHandlerSet) return
        ffmpegLogHandlerLock.withLock {
            if (ffmpegLogHandlerSet) return
            FFmpegKit.setLogHandler { msg ->

                if (msg.isError) {
                    logger.error { "[FFmpeg:${msg.level}] ${msg.line}" }
                } else if (msg.level >= 48) {
                    logger.trace { "[FFmpeg:${msg.level}] ${msg.line}" }
                } else if (msg.level >= 40) {
                    logger.debug { "[FFmpeg:${msg.level}] ${msg.line}" }
                } else if (msg.level >= 32) {
                    logger.info { "[FFmpeg:${msg.level}] ${msg.line}" }
                } else if (msg.level >= 24) {
                    logger.warn { "[FFmpeg:${msg.level}] ${msg.line}" }
                } else {
                    logger.error { "[FFmpeg:${msg.level}] ${msg.line}" }
                }
            }
        }
    }

    protected suspend fun emitProgress(downloadId: DownloadId) {
        val st = getState(downloadId) ?: return
        val progress = createProgress(st)
        _progressFlow.emit(progress)
    }

    private suspend fun deleteDownloadArtifacts(state: DownloadState) = withContext(ioDispatcher) {
        val outputPath = baseSaveDir.resolve(state.relativeOutputPath)
        if (outputPath.inSystem.exists()) {
            fileSystem.delete(outputPath)
        }

        val cacheDir = baseSaveDir.resolve(state.relativeSegmentCacheDir)
        if (cacheDir.inSystem.exists()) {
            fileSystem.deleteRecursively(cacheDir)
        }
    }

    private fun createProgress(
        st: DownloadState,
        lastSegmentFailure: SegmentFailure? = segmentFailures.value[st.downloadId],
    ): DownloadProgress {
        val downloadedSegments = st.segments.count { it.isDownloaded }
        return DownloadProgress(
            downloadId = st.downloadId,
            url = st.url,
            mediaType = st.mediaType,
            totalSegments = st.totalSegments,
            downloadedSegments = downloadedSegments,
            downloadedBytes = st.downloadedBytes,
            totalBytes = st.segments.sumOf { it.byteSize.coerceAtLeast(0) }
                .coerceAtLeast(st.downloadedBytes),
            status = st.status,
            error = st.error,
            lastSegmentFailure = lastSegmentFailure,
        )
    }

    private suspend fun recordSegmentFailure(
        downloadId: DownloadId,
        segmentIndex: Int?,
        attempt: Int,
        maxAttempts: Int,
        error: Throwable,
    ) {
        val failure = SegmentFailure(
            segmentIndex = segmentIndex,
            attempt = attempt,
            maxAttempts = maxAttempts,
            message = error.toString(),
            timestampMillis = clock.now().toEpochMilliseconds(),
        )
        segmentFailures.update { put(downloadId, failure) }
        emitProgress(downloadId)
    }

    private fun clearSegmentFailure(downloadId: DownloadId) {
        segmentFailures.update { remove(downloadId) }
    }

    protected suspend inline fun <R> httpGet(url: String, options: DownloadOptions, block: (HttpStatement) -> R): R {
        return client.use {
            prepareGet(url) {
                options.headers.forEach { (k, v) -> header(k, v) }
            }.let { statement -> block(statement) }
        }
    }

    suspend fun joinDownload(downloadId: DownloadId) {
        val job = stateMutex.withLock {
            _downloadStatesFlow.value[downloadId]?.job
        }
        if (job != null) {
            logger.info { "Waiting for download job to complete for $downloadId" }
            job.join()
            logger.info { "Download job completed for $downloadId" }
        }
    }

    private suspend fun <T> withRetry(
        maxRetries: Int,
        baseDelayMillis: Long,
        onFailure: suspend (attempt: Int, error: Throwable) -> Unit = { _, _ -> },
        block: suspend () -> T
    ): T {
        var attempt = 1
        var currentDelay = baseDelayMillis
        while (true) {
            try {
                return block()
            } catch (ce: CancellationException) {

                throw ce
            } catch (ex: Throwable) {
                onFailure(attempt, ex)
                if (attempt >= maxRetries) {
                    logger.info {
                        "Segment download failed after $attempt/$maxRetries attempts; no more retries. " +
                                "Error: ${ex.message}"
                    }
                    throw ex
                }
                logger.info {
                    "Segment download failed on attempt $attempt/$maxRetries: ${ex.message}. " +
                            "Retrying after ${currentDelay}ms..."
                }
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(30_000L)
                attempt++
            }
        }
    }

    open fun onCreateDownloadState(state: DownloadState) {}

    open fun onUpdateDownloadStatus(downloadId: DownloadId, status: DownloadStatus) {}

    open fun onUpdateDownloadState(downloadId: DownloadId, state: DownloadState) {}

    open fun onRemoveAllDownloads() {}

    open fun onRemoveDownload(downloadId: DownloadId) {
    }

    private companion object {
        val logger = logger<KtorHttpDownloader>()
        private const val UPSTREAM_PLAYLIST_FILE_NAME = "upstream-playlist.m3u8"
        private const val UPSTREAM_PLAYLIST_URL_FILE_NAME = "upstream-playlist.url"
        private const val LOCAL_PLAYLIST_FILE_NAME = "local-playlist.m3u8"
    }
}

private class M3u8Exception(val errorCode: DownloadErrorCode) : Exception()

private fun M3u8Playlist.MediaPlaylist.toSegments(resolveSegmentPath: (String) -> String): List<SegmentInfo> {
    return segments.mapIndexed { i, seg ->
        val idx = mediaSequence + i
        SegmentInfo(
            index = idx,
            url = seg.uri,
            isDownloaded = false,
            byteSize = seg.byteRange?.length ?: -1,
            durationSeconds = seg.duration,
            title = seg.title,
            isDiscontinuity = seg.isDiscontinuity,
            encryption = seg.encryption?.toSegmentEncryptionInfo(),
            relativeTempFilePath = resolveSegmentPath("$idx.ts"),
        )
    }
}

private fun MediaSegmentEncryption.toSegmentEncryptionInfo(): SegmentEncryptionInfo =
    SegmentEncryptionInfo(
        method = method,
        keyUri = uri,
        iv = iv,
    )

private fun DownloadState.orderedUniqueEncryptionKeys(): List<SegmentEncryptionInfo> =
    segments.sortedBy { it.index }
        .mapNotNull { it.encryption }
        .distinctBy { it.keyUri }

private fun DownloadState.buildHlsKeyRelativePaths(): Map<String, String> =
    orderedUniqueEncryptionKeys()
        .mapIndexed { index, encryption ->
            encryption.keyUri to "keys/key-$index-${encryption.keyUri.stablePathId()}.bin"
        }
        .toMap()

private fun SegmentInfo.localPlaylistPath(): String =
    relativeTempFilePath.substringAfterLast('/').substringAfterLast('\\')

private fun String.stablePathId(): String =
    hashCode().toUInt().toString(16)

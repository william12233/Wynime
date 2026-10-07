package com.wynime.app.domain.media.fetch

import io.ktor.client.plugins.ServerResponseException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.nameCnOrName
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.RepositoryRateLimitedException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.RepositoryServiceUnavailableException
import com.wynime.app.data.repository.RepositoryUnknownException
import com.wynime.app.domain.mediasource.instance.MediaSourceInstance
import com.wynime.app.domain.mediasource.web.BlockReason
import com.wynime.app.domain.mediasource.web.BlockedException
import com.wynime.app.domain.sourceplugin.SourcePluginFailure
import com.wynime.app.domain.sourceplugin.SourcePluginNoMatchException
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.paging.SizedSource
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaMatch
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.toStringMultiline
import com.wynime.utils.coroutines.cancellableCoroutineScope
import com.wynime.utils.coroutines.flows.flowOfEmptyList
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.collections.EnumMap
import com.wynime.utils.platform.collections.ImmutableEnumMap
import com.wynime.utils.platform.currentTimeMillis
import com.wynime.utils.platform.Uuid
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

private val DEFAULT_RATE_LIMIT_RETRY_DELAY = 30.seconds

interface MediaFetcher {

    fun newSession(
        request: MediaFetchRequest,
        flowContext: CoroutineContext = EmptyCoroutineContext
    ): MediaFetchSession {
        return newSession(flowOf(request), flowContext)
    }

    fun newSession(
        requestLazy: Flow<MediaFetchRequest>,
        flowContext: CoroutineContext = EmptyCoroutineContext,
    ): MediaFetchSession
}

fun MediaFetchRequest.Companion.create(
    subject: SubjectInfo,
    episode: EpisodeInfo,
    episodes: List<EpisodeInfo> = emptyList(),
): MediaFetchRequest {
    return MediaFetchRequest(
        subjectId = subject.subjectId.toString(),
        episodeId = episode.episodeId.toString(),
        subjectNameCN = subject.nameCnOrName,
        subjectNames = subject.allNames,
        episodeSort = episode.sort,
        episodeName = episode.displayName,
        episodeEp = episode.ep,
        episodes = episodes.map {
            MediaFetchRequest.Episode(
                episodeId = it.episodeId.toString(),
                sort = it.sort,
                ep = it.ep,
                name = it.displayName,
                airDate = it.airDate,
            )
        },
    )
}

class MediaFetcherConfig {
    companion object {
        val Default = MediaFetcherConfig()
    }
}

class MediaSourceMediaFetcher(
    private val configProvider: () -> MediaFetcherConfig,
    private val mediaSources: List<MediaSourceInstance>,
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : MediaFetcher {
    private inner class MediaSourceResultImpl(
        override val instanceId: String,
        override val mediaSourceId: String,
        override val sourceInfo: MediaSourceInfo,
        override val kind: MediaSourceKind,
        private val config: MediaFetcherConfig,
        val disabled: Boolean,
        pagedSources: Flow<SizedSource<MediaMatch>>,
        private val flowContext: CoroutineContext,
    ) : MediaSourceFetchResult, SynchronizedObject() {

        override val state: MutableStateFlow<MediaSourceFetchState> =
            MutableStateFlow(if (disabled) MediaSourceFetchState.Disabled else MediaSourceFetchState.Idle)
        private val restartCount = MutableStateFlow(0)

        override val results by lazy {
            restartCount.flatMapLatest { restartCount ->

                state.value.let { currentState ->

                    currentCoroutineContext().ensureActive()

                    if (restartCount == 0 && currentState is MediaSourceFetchState.Disabled)
                        return@flatMapLatest flowOf(FetchUpdate.Results(restartCount, emptyList()))

                    val lastRestartCount = when (currentState) {
                        is MediaSourceFetchState.Completed -> currentState.id
                        else -> -1
                    }
                    if (lastRestartCount == restartCount) {

                        return@flatMapLatest emptyFlow()
                    }
                }

                var terminalState: MediaSourceFetchState.Completed? = null
                pagedSources
                    .onStart {
                        state.value = MediaSourceFetchState.Working
                    }
                    .flatMapMerge { sources ->
                        sources.results.map { it.media }
                    }
                    .onEach { media ->
                        logger.info {
                            "Media source result emitted: source=$mediaSourceId mediaId=${media.mediaId} " +
                                "episodeRange=${media.episodeRange}"
                        }
                    }
                    .catch { exception ->
                        terminalState = when {
                            exception is BlockedException -> when (val reason = exception.reason) {
                                is BlockReason.Captcha -> MediaSourceFetchState.CaptchaRequired(
                                    exception.request,
                                    restartCount,
                                )

                                is BlockReason.RateLimited -> MediaSourceFetchState.RateLimited(
                                    retryAt = currentTimeMillis() +
                                            (reason.retryAfter ?: DEFAULT_RATE_LIMIT_RETRY_DELAY).inWholeMilliseconds,
                                    id = restartCount,
                                )

                                else -> MediaSourceFetchState.Failed(exception, restartCount)
                            }

                            exception is SourcePluginNoMatchException -> MediaSourceFetchState.NoMatch(
                                diagnostics = exception.diagnostics,
                                id = restartCount,
                            )

                            exception is SourcePluginFailure -> MediaSourceFetchState.Failed(
                                cause = exception,
                                id = restartCount,
                                diagnostics = exception.diagnostics,
                            )

                            else -> MediaSourceFetchState.Failed(exception, restartCount)
                        }
                        logUpstreamException(exception)
                    }
                    .runningFold(emptyList<Media>()) { acc, list ->
                        acc + list
                    }
                    .map<List<Media>, FetchUpdate> { list ->
                        FetchUpdate.Results(restartCount, list.distinctBy { it.mediaId })
                    }
                    .onStart {

                        emit(FetchUpdate.Results(restartCount, emptyList()))
                    }
                    .onCompletion { exception ->
                        if (exception == null) {

                            emit(FetchUpdate.Completed(terminalState ?: MediaSourceFetchState.Succeed(restartCount)))
                        } else {
                            synchronized(this@MediaSourceResultImpl) {

                                if (this@MediaSourceResultImpl.restartCount.value == restartCount &&
                                    state.value !is MediaSourceFetchState.Completed
                                ) {
                                    state.value = MediaSourceFetchState.Abandoned(exception, restartCount)
                                }
                            }
                            if (exception !is CancellationException) {
                                logger.error(exception) { "Failed to fetch media from $mediaSourceId due to downstream error" }
                            }
                        }
                    }
            }.transform { update ->
                currentCoroutineContext().ensureActive()
                if (update.generation != restartCount.value) return@transform
                when (update) {
                    is FetchUpdate.Results -> emit(update.results)
                    is FetchUpdate.Completed -> {

                        val published = synchronized(this@MediaSourceResultImpl) {
                            if (update.generation != restartCount.value) false else {
                                if (update.state is MediaSourceFetchState.Succeed) rateLimitAutoRestartBudget = 1
                                state.value = update.state
                                true
                            }
                        }
                        if (published && update.state is MediaSourceFetchState.RateLimited && state.value == update.state) {
                            scheduleRateLimitAutoRestart(update.state)
                        }
                    }
                }
            }.shareIn(
                CoroutineScope(flowContext), replay = 1, started = SharingStarted.WhileSubscribed(),
            ).onCompletion {
                if (it == null)
                    logger.error { "results is completed normally, however it shouldn't" }
            }
        }

        private fun logUpstreamException(exception: Throwable) {
            when (exception) {
                is ServerResponseException -> {
                    logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to ${exception.response.status}" }
                }

                is IOException -> {
                    logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to network error" }
                }

                is BlockedException -> {
                    logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to blocked: ${exception.reason}" }
                }

                is CancellationException -> {
                    logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to CancellationException" }
                }

                is SourcePluginFailure -> {
                    logger.warn {
                        "Failed to fetch media from ${sourceInfo.displayName} due to source plugin " +
                            "status=${exception.status.name} provider=${exception.diagnostics.provider} " +
                            "traceId=${exception.diagnostics.traceId}"
                    }
                }

                is SourcePluginNoMatchException -> {
                    logger.info {
                        "Source plugin returned no match: provider=${exception.diagnostics.provider} " +
                            "traceId=${exception.diagnostics.traceId}"
                    }
                }

                is RepositoryException -> {
                    when (exception) {
                        is RepositoryAuthorizationException -> {
                            logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to forbidden" }
                        }

                        is RepositoryNetworkException -> {
                            logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to network error" }
                        }

                        is RepositoryRateLimitedException -> {
                            logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to rate limited" }
                        }

                        is RepositoryServiceUnavailableException -> {
                            logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to service unavailable" }
                        }

                        is RepositoryUnknownException -> {
                            logger.error(exception) { "Failed to fetch media from ${sourceInfo.displayName} due to unknown error" }
                        }

                        is RepositoryRequestError -> {
                            logger.warn { "Failed to fetch media from ${sourceInfo.displayName} due to request error: ${exception.localizedMessage}" }
                        }
                    }
                }

                else -> {
                    logger.error(exception) { "Failed to fetch media from ${sourceInfo.displayName} due to upstream error" }
                }
            }
        }

        private var rateLimitAutoRestartBudget = 1

        private fun scheduleRateLimitAutoRestart(rateLimited: MediaSourceFetchState.RateLimited) {
            val allowed = synchronized(this) {
                if (rateLimitAutoRestartBudget <= 0) {
                    false
                } else {
                    rateLimitAutoRestartBudget--
                    true
                }
            }
            if (!allowed) return
            CoroutineScope(flowContext).launch {
                delay((rateLimited.retryAt - currentTimeMillis()).coerceAtLeast(0))
                if (state.value == rateLimited) {
                    restart()
                }
            }
        }

        override fun restart() {

            synchronized(this) {
                while (true) {
                    when (val value = state.value) {
                        is MediaSourceFetchState.Completed,
                        MediaSourceFetchState.Disabled -> {
                            restartCount.value += 1

                            if (state.compareAndSet(value, MediaSourceFetchState.Idle)) {
                                break
                            }

                        }

                        MediaSourceFetchState.Idle,
                        MediaSourceFetchState.Working -> {
                            break
                        }
                    }
                }
            }
        }

        override fun enable() {
            while (true) {
                val value = state.value
                if (value == MediaSourceFetchState.Disabled) {
                    if (restartCount.compareAndSet(0, 1)) {
                        state.compareAndSet(value, MediaSourceFetchState.Idle)
                        break
                    }
                } else {
                    break
                }
            }
        }
    }

    private inner class MediaFetchSessionImpl(
        request: Flow<MediaFetchRequest>,
        private val config: MediaFetcherConfig,
        private val flowContext: CoroutineContext,
    ) : MediaFetchSession {
        private val initialFetchRequest: Flow<MediaFetchRequest> =
            request.take(1)
                .onEach {
                    logger.info { "MediaFetchSessionImpl pagedSources creating, request: \n${it.toStringMultiline()}" }
                }
                .shareIn(CoroutineScope(flowContext), started = SharingStarted.Lazily, replay = 1)
                .take(1)

        private val overrideFetchRequest = MutableStateFlow<MediaFetchRequest?>(null)

        override val latestRequest: Flow<MediaFetchRequest> =
            combine(initialFetchRequest, overrideFetchRequest) { initial, override ->
                override ?: initial
            }
        override val request: Flow<MediaFetchRequest> = latestRequest.take(1)

        override val mediaSourceResults: List<MediaSourceFetchResult> = mediaSources
            .map { instance ->
                MediaSourceResultImpl(
                    instanceId = instance.instanceId,
                    mediaSourceId = instance.source.mediaSourceId,
                    sourceInfo = instance.source.info,
                    kind = instance.source.kind,
                    config = config,
                    disabled = !instance.isEnabled,
                    pagedSources = this.request
                        .map {
                            instance.source.fetch(it)
                        },
                    flowContext = flowContext,
                )
            }

        override val cumulativeResults: Flow<List<Media>> = kotlin.run {
            if (mediaSourceResults.isEmpty()) {
                return@run flowOfEmptyList()
            }
            combine(mediaSourceResults.map { it.results }) { lists ->
                lists.asSequence().flatten().toList()
            }.map { list ->
                list.distinctBy { it.mediaId }
            }.flowOn(flowContext)
                .run {
                    if (currentWynimeBuildConfig.isDebug && ENABLE_WATCHDOG) {
                        flow {
                            cancellableCoroutineScope {
                                val watchdog = launch {
                                    while (true) {
                                        delay(2000)
                                        logger.info {
                                            val states = mediaSourceResults.map { it.state.value }
                                            "cumulativeResults is still being collected, states=$states"
                                        }
                                    }
                                }
                                collect { emit(it) }
                                watchdog.cancel()
                            }
                        }
                    } else {
                        this
                    }
                }.onCompletion {
                    if (it == null)
                        logger.error { "cumulativeResults is completed normally, however it shouldn't" }
                }
        }

        override val hasCompleted = if (mediaSourceResults.isEmpty()) {
            flowOf(CompletedConditions.AllCompleted)
        } else {
            combine(mediaSourceResults.map { it.state }) {
                val pairs = mediaSourceResults.groupBy { it.kind }.mapValues { results ->
                    val states = results.value.map { it.state }
                    when {

                        states.all { it.value is MediaSourceFetchState.Disabled } -> null
                        states.all { it.value is MediaSourceFetchState.Completed || it.value is MediaSourceFetchState.Disabled } -> true
                        else -> false
                    }
                }
                CompletedConditions(
                    ImmutableEnumMap<MediaSourceKind, _> { kind ->
                        pairs[kind]
                    },
                )
            }.flowOn(flowContext)
        }

        override fun setFetchRequest(request: MediaFetchRequest) {
            if (request == overrideFetchRequest.value) {
                return
            }
            overrideFetchRequest.value = request
            restartAll()
        }
    }

    override fun newSession(
        requestLazy: Flow<MediaFetchRequest>,
        flowContext: CoroutineContext
    ): MediaFetchSession {
        val traceId = Uuid.randomString()
        val tracedRequest = requestLazy.take(1).map { request ->
            request.takeIf { it.traceId.isNotBlank() } ?: request.copy(traceId = traceId)
        }
        return MediaFetchSessionImpl(tracedRequest, configProvider(), this.flowContext + flowContext)
    }

    private companion object {
        private val logger = logger<MediaSourceMediaFetcher>()
        private const val ENABLE_WATCHDOG = false
    }
}

data class CompletedConditions(
    private val values: EnumMap<MediaSourceKind, Boolean?>
) {
    fun allCompleted() = values.values.all { it ?: true }

    operator fun get(kind: MediaSourceKind): Boolean? = try {
        values[kind]
    } catch (e: NoSuchElementException) {
        null
    }

    companion object {
        val AllCompleted = CompletedConditions(
            ImmutableEnumMap { true },
        )
    }
}

private sealed interface FetchUpdate {
    val generation: Int

    data class Results(override val generation: Int, val results: List<Media>) : FetchUpdate
    data class Completed(val state: MediaSourceFetchState.Completed) : FetchUpdate {
        override val generation: Int get() = state.id
    }
}

internal fun EpisodeInfo.withRequestedNumbers(request: MediaFetchRequest): EpisodeInfo {
    if (request.episodeId != episodeId.toString()) return this
    if (request.episodeSort == sort && request.episodeEp == ep) return this
    return copy(sort = request.episodeSort, ep = request.episodeEp)
}

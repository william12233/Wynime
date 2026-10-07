package com.wynime.app.domain.episode

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.fetch.SubjectMediaFetchSessionRegistry
import com.wynime.app.domain.media.fetch.createFetchFetchSession
import com.wynime.app.domain.media.resolver.toEpisodeMetadata
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.player.ExtensionException
import com.wynime.app.domain.player.PlayerExtensionManager
import com.wynime.app.domain.player.extension.EpisodePlayerExtensionFactory
import com.wynime.app.domain.player.extension.ExtensionBackgroundTaskScope
import com.wynime.app.domain.player.extension.PlayerExtension
import com.wynime.app.domain.player.extension.PlayerExtensionEvent
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.EpisodeSwitch
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

class EpisodeFetchSelectPlayState(
    val subjectId: Int,
    initialEpisodeId: Int,
    player: MediampPlayer,
    private val backgroundScope: CoroutineScope,
    extensions: List<EpisodePlayerExtensionFactory<*>>,
    private val koin: Koin = GlobalKoin,
    private val sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
    private val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
    private val analyticsContext: AnalyticsContext = object : AnalyticsContext {},
) {
    interface AnalyticsContext {
        suspend fun isFullscreen(): Boolean? = false
    }

    private val mediaSourceManager by koin.inject<MediaSourceManager>()
    private val sharedFetchSessionRegistry = koin.getOrNull<SubjectMediaFetchSessionRegistry>()

    private val fetchSessions = SubjectMediaFetchSessions(
        scope = backgroundScope,
        createSession = { request ->
            mediaSourceManager.createFetchFetchSession(flowOf(request))
        },
        sharedRegistry = sharedFetchSessionRegistry,
    )

    private val _episodeSessionFlow = MutableStateFlow(
        newEpisodeSession(initialEpisodeId),
    )

    val episodeSessionFlow: StateFlow<EpisodeSession> = _episodeSessionFlow.asStateFlow()

    val playerSession = PlayerSession(
        player,
        koin,
        backgroundScope,
        mainDispatcher,
    )

    private val extensionManager by lazy {
        val intrinsicExtensions = listOf(
            EpisodePlayerExtensionFactory { context, _ ->
                LoadMediaOnSelectExtension { episodeId ->
                    backgroundScope.launch { context.broadcast(MediaLoadedEvent(episodeId)) }
                }
            },
        )

        PlayerExtensionManager(
            intrinsicExtensions + extensions,
            this, koin,
        )
    }

    private val switchEpisodeLock = Mutex()

    suspend fun switchEpisode(episodeId: Int) {
        Analytics.recordEvent(
            EpisodeSwitch,
            mapOf(
                "subject_id" to subjectId,
                "episode_id" to episodeId,
                "is_fullscreen" to analyticsContext.isFullscreen(),
            ),
        )

        currentCoroutineContext()[InSwitchEpisode]?.let { element ->
            error(
                "Recursive switchEpisode call detected. " +
                        "You wanted to switch to $episodeId, while you are already switching to ${element.newEpisodeId}.",
            )
        }

        backgroundScope.launch {
            switchEpisodeLock.withLock {
                withContext(InSwitchEpisode(episodeId)) {

                    logger.info { "SwitchEpisode($episodeId): Stopping previous scope" }
                    _episodeSessionFlow.value.sessionScope.coroutineContext.job.cancelAndJoin()

                    logger.info { "SwitchEpisode($episodeId): Pausing player" }
                    withContext(mainDispatcher) {

                        if (player.state.value.playWhenReady) {
                            player.pause()
                        }
                    }

                    logger.info { "SwitchEpisode($episodeId): Calling extension onBeforeSwitchEpisode" }
                    extensionManager.call {
                        it.onBeforeSwitchEpisode(episodeId)
                    }

                    logger.info { "SwitchEpisode($episodeId): Stopping player" }
                    playerSession.stopPlayback()

                    logger.info { "SwitchEpisode($episodeId): Propagate newEpisodeSession" }
                    val newSession = newEpisodeSession(episodeId)
                    _episodeSessionFlow.value = newSession

                    logger.info { "SwitchEpisode($episodeId): Start background tasks" }
                    newSession.startSessionScopeTasks()

                    logger.info { "SwitchEpisode($episodeId): Complete" }
                }
            }
        }.join()
    }

    private fun newEpisodeSession(episodeId: Int) = EpisodeSession(
        subjectId,
        episodeId,
        koin,
        backgroundScope.coroutineContext,
        sharingStarted,
        fetchSessions,
    )

    private val uiReady = CompletableDeferred<Unit>()

    fun onUIReady() {
        uiReady.complete(Unit)

        episodeSessionFlow.value.let { session ->
            if (!session.sessionScopeTasksStarted.value) {
                backgroundScope.launch {
                    session.startSessionScopeTasks()
                }
            }
        }

    }

    suspend fun onClose() {
        extensionManager.call { it.onClose() }
        playerSession.stopPlayback()
        fetchSessions.close()
    }

    fun restartLoad() {
        episodeSessionFlow.value.restartLoad()
    }

    private suspend fun EpisodeSession.startSessionScopeTasks() {

        uiReady.await()

        if (sessionScopeTasksStarted.getAndUpdate { true }) {
            return
        }
        val episodeSession = this

        withContext(NonCancellable) {

            extensionManager.call { extension ->
                extension.onStart(episodeSession, ExtensionBackgroundTaskScopeImpl(extension, sessionScope))
            }
        }
    }

    private class ExtensionBackgroundTaskScopeImpl(
        private val extension: PlayerExtension,
        private val scope: CoroutineScope,
    ) : ExtensionBackgroundTaskScope {
        override fun launch(subName: String, block: suspend CoroutineScope.() -> Unit): Job {
            return scope.launch(
                CoroutineName(extension.name + "." + subName),
                start = CoroutineStart.UNDISPATCHED,
            ) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ExtensionException(
                        "Unhandled exception in background scope from task '$subName' launched by extension '$extension'",
                        e,
                    )
                }
            }
        }
    }

    private inner class LoadMediaOnSelectExtension(
        private val onMediaLoaded: (episodeId: Int) -> Unit = { }
    ) : PlayerExtension("LoadMediaOnSelect") {
        override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
            backgroundTaskScope.launch("LoadMediaOnSelect") {
                episodeSessionFlow.collectLatest { episodeSession ->
                    episodeSession.fetchSelectFlow.collectLatest fetchSelect@{ fetchSelect ->
                        if (fetchSelect == null) return@fetchSelect

                        fetchSelect.mediaSelector.selected.filterNotNull().collectLatest { media ->
                            val episodeInfo = episodeSession.infoBundleFlow
                                .filterNotNull()
                                .first()
                                .episodeInfo

                            playerSession.loadMedia(media, episodeInfo.toEpisodeMetadata())
                            onMediaLoaded(episodeInfo.episodeId)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        private val logger = logger<EpisodeFetchSelectPlayState>()
    }

    class MediaLoadedEvent(val episodeId: Int) : PlayerExtensionEvent
}

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.infoLoadErrorFlow: Flow<LoadError?> get() = episodeSessionFlow.flatMapLatest { it.infoLoadErrorStateFlow }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.infoBundleFlow get() = episodeSessionFlow.flatMapLatest { it.infoBundleFlow }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.mediaFetchSessionFlow: Flow<MediaFetchSession?>
    get() = episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }.map { it?.mediaFetchSession }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.mediaSelectorFlow: Flow<MediaSelector?>
    get() = episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }.map { it?.mediaSelector }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.episodeIdFlow get() = episodeSessionFlow.map { it.episodeId }

val EpisodeFetchSelectPlayState.player get() = playerSession.player

@UnsafeEpisodeSessionApi
suspend fun EpisodeFetchSelectPlayState.getCurrentEpisodeId(): Int {
    return episodeIdFlow.first()
}

@RequiresOptIn(
    message = "This flow API is unsafe for use. When you collect from multiple flows marked with this annotation, you may see inconsistent (old) data from one flow." +
            "You must not combine these unsafe flows. If you need to combine them, use flows from fetchSelectSession",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafeEpisodeSessionApi

private class InSwitchEpisode(
    val newEpisodeId: Int,
) : AbstractCoroutineContextElement(InSwitchEpisode) {
    companion object Key : CoroutineContext.Key<InSwitchEpisode>
}

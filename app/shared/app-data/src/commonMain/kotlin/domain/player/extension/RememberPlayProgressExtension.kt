package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.episode.SubjectEpisodeInfoBundle
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RememberPlayProgressExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val periodicReportInterval: Duration = 1.minutes,
    private val initialReportDelay: Duration = 5.seconds,
) : PlayerExtension(name = "SaveProgressExtension") {
    private val playProgressRepository: EpisodePlayHistoryRepository by koin.inject()
    private val latestInfoBundleMutex = Mutex()
    private val latestInfoBundles = mutableMapOf<Int, SubjectEpisodeInfoBundle>()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val mediaLoaded = CompletableDeferred<Unit>()
        backgroundTaskScope.launch("MediaLoadedListener") {
            context.subscribeEvents<EpisodeFetchSelectPlayState.MediaLoadedEvent>().collectLatest { event ->
                if (event.episodeId == episodeSession.episodeId && mediaLoaded.isActive) {
                    mediaLoaded.complete(Unit)
                }
            }
        }

        backgroundTaskScope.launch("InfoBundleCache") {
            episodeSession.infoBundleFlow.filterNotNull().collect { info ->
                latestInfoBundleMutex.withLock {
                    latestInfoBundles[info.episodeId] = info
                }
            }
        }

        backgroundTaskScope.launch("MediaSelectorListener") {
            mediaLoaded.await()
            episodeSession.fetchSelectFlow.collectLatest inner@{ fetchSelect ->
                if (fetchSelect == null) return@inner

                fetchSelect.mediaSelector.events.onBeforeSelect.collect {

                    savePlayProgressOrRemove(episodeSession)
                }
            }
        }

        backgroundTaskScope.launch("PlaybackStateListener") {
            val player = context.player
            var haveResumedOnce = false
            player.state.collectLatest { state ->
                when {
                    state.mediaStatus == MediaStatus.Opening -> {

                        haveResumedOnce = false
                    }

                    state.isPlaying -> {

                        if (!haveResumedOnce) {
                            val positionMillis =
                                playProgressRepository.getPositionMillisByEpisodeId(episodeSession.episodeId)
                            if (positionMillis == null) {
                                logger.info { "Did not find saved position" }
                                haveResumedOnce = true
                            } else {
                                logger.info {
                                    "Loaded saved position: $positionMillis, waiting for video properties"
                                }
                                player.mediaProperties.first { (it?.durationMillis ?: 0L) > 0L }
                                withContext(Dispatchers.Main + NonCancellable) {
                                    logger.info {
                                        "Video properties ready, seeking to saved position: $positionMillis"
                                    }
                                    player.seekTo(positionMillis)
                                    haveResumedOnce = true
                                }
                            }
                        }

                        delay(initialReportDelay)
                        savePlayProgressOrRemove(episodeSession, allowZeroPosition = true)

                        if (periodicReportInterval != Duration.INFINITE) {
                            while (true) {
                                delay(periodicReportInterval)
                                savePlayProgressOrRemove(episodeSession, allowZeroPosition = true)
                            }
                        }
                    }

                    state.mediaStatus == MediaStatus.Ready && !state.playWhenReady -> {
                        mediaLoaded.await()
                        savePlayProgressOrRemove(episodeSession)
                    }

                    state.mediaStatus == MediaStatus.Ended -> {
                        mediaLoaded.await()
                        savePlayProgressOrRemove(episodeSession)
                    }

                    else -> Unit
                }
            }

        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        savePlayProgressOrRemove(context.getCurrentEpisodeId())
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    override suspend fun onClose() {
        savePlayProgressOrRemove(context.getCurrentEpisodeId())
    }

    private suspend fun savePlayProgressOrRemove(
        episodeSession: EpisodeSession,
        allowZeroPosition: Boolean = false,
    ) {
        savePlayProgressOrRemove(episodeSession.episodeId, episodeSession, allowZeroPosition)
    }

    private suspend fun savePlayProgressOrRemove(
        episodeId: Int
    ) {
        savePlayProgressOrRemove(episodeId, null)
    }

    private suspend fun savePlayProgressOrRemove(
        episodeId: Int,
        episodeSession: EpisodeSession?,
        allowZeroPosition: Boolean = false,
    ) {
        val player = context.player
        val mediaStatus = player.state.value.mediaStatus
        val videoDurationMillis = player.mediaProperties.value?.durationMillis

        if (videoDurationMillis == null || videoDurationMillis <= 0L) {
            return
        }

        if (mediaStatus != MediaStatus.Ready && mediaStatus != MediaStatus.Ended) {
            return
        }

        val currentPositionMillis = player.currentPositionMillis.value

        if (currentPositionMillis < 0L || (currentPositionMillis == 0L && !allowZeroPosition)) {
            return
        }

        if (videoDurationMillis - currentPositionMillis < 5000 || currentPositionMillis > videoDurationMillis) {
            playProgressRepository.remove(episodeId)
        } else {
            val info = latestInfoBundle(episodeId, episodeSession)
            playProgressRepository.saveOrUpdate(
                episodeId = episodeId,
                positionMillis = currentPositionMillis,
                subjectId = info?.subjectId,
                episodeSort = info?.episodeInfo?.sort?.number,
                subjectName = info?.subjectInfo?.displayName,
                subjectImageUrl = info?.subjectInfo?.imageLarge,
                episodeName = info?.episodeInfo?.displayName,
                durationMillis = videoDurationMillis,
            )
        }
    }

    private suspend fun latestInfoBundle(
        episodeId: Int,
        episodeSession: EpisodeSession?,
    ): SubjectEpisodeInfoBundle? {
        episodeSession?.infoBundleFlow?.replayCache?.lastOrNull()?.let { return it }

        return latestInfoBundleMutex.withLock {
            latestInfoBundles[episodeId]
        }
    }

    companion object : EpisodePlayerExtensionFactory<RememberPlayProgressExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): RememberPlayProgressExtension =
            RememberPlayProgressExtension(context, koin)

        private val logger = logger<RememberPlayProgressExtension>()
    }
}
